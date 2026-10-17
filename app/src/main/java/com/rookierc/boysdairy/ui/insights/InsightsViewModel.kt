package com.rookierc.boysdairy.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.data.GapExclusionStore
import com.rookierc.boysdairy.domain.AbstinenceGap
import com.rookierc.boysdairy.domain.AchievementProgress
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryStats
import com.rookierc.boysdairy.domain.GapExclusion
import com.rookierc.boysdairy.domain.HourBucket
import com.rookierc.boysdairy.domain.InsightsPeriod
import com.rookierc.boysdairy.domain.KeywordCount
import com.rookierc.boysdairy.domain.MonthComparison
import com.rookierc.boysdairy.domain.WeekBucket
import com.rookierc.boysdairy.domain.abstinenceGaps
import com.rookierc.boysdairy.domain.computeAchievements
import com.rookierc.boysdairy.domain.computeStats
import com.rookierc.boysdairy.domain.entriesInPeriod
import com.rookierc.boysdairy.domain.hourBucketCounts
import com.rookierc.boysdairy.domain.longestStreakDays
import com.rookierc.boysdairy.domain.monthOverMonth
import com.rookierc.boysdairy.domain.topKeywords
import com.rookierc.boysdairy.domain.weekdayCounts
import com.rookierc.boysdairy.domain.weeklyCounts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.DayOfWeek
import java.time.ZoneId

/**
 * Raw data only, like the home screen: the report depends on "today", so the UI derives it from
 * [entries] and [exclusions] together with a date that refreshes on resume and at midnight.
 */
class InsightsViewModel(
    repository: EntryRepository,
    private val gapExclusionStore: GapExclusionStore,
) : ViewModel() {

    /** All entries, newest first. `null` until the first database read completes. */
    val entries: StateFlow<List<Entry>?> = repository.entries.stateIn<List<Entry>?>(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    /**
     * Gaps marked "does not count" on the achievement wall. `null` until the first read completes,
     * so the wall is never drawn with an empty set first and then flips badges back to locked.
     */
    val exclusions: StateFlow<Set<GapExclusion>?> = gapExclusionStore.exclusions.stateIn<Set<GapExclusion>?>(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private val _period = MutableStateFlow(InsightsPeriod.ALL)

    /** Range picked with the chips at the top. In memory only: every visit starts at "all". */
    val period: StateFlow<InsightsPeriod> = _period.asStateFlow()

    fun selectPeriod(period: InsightsPeriod) {
        _period.value = period
    }

    /** Takes [gap] out of every gap badge (the user has already confirmed). */
    fun exclude(gap: AbstinenceGap) = editExclusions { exclude(gap) }

    /** Counts [gap] again. */
    fun restore(gap: AbstinenceGap) = editExclusions { restore(gap) }

    private fun editExclusions(change: suspend GapExclusionStore.() -> Unit) {
        viewModelScope.launch {
            try {
                gapExclusionStore.change()
            } catch (error: IOException) {
                // Not written: the wall keeps showing what is stored, which is still consistent.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                InsightsViewModel(container.entryRepository, container.gapExclusionStore)
            }
        }
    }
}

/** Number of weeks in the bar chart. */
const val INSIGHTS_WEEKS = 12

/** Rows of the keyword leaderboard. */
const val INSIGHTS_TOP_KEYWORDS = 5

/**
 * Everything the report screen shows, computed in one go.
 *
 * [stats], [weekdays], [hourBuckets], [longestStreak] and [keywords] only look at the entries
 * inside [period]; [weeks], [gaps], [achievements] and [monthComparison] always use the whole diary.
 * [keywords] is the solo keyword leaderboard ([topKeywords]); empty when the range has none.
 * [gaps] are all gaps of the diary, oldest first ([abstinenceGaps]), and [exclusions] the ones the
 * user marked "does not count", already left out of [achievements]; the gap ledger shows both.
 */
data class InsightsReport(
    val period: InsightsPeriod,
    val stats: EntryStats,
    val weeks: List<WeekBucket>,
    val weekdays: Map<DayOfWeek, Int>,
    val hourBuckets: Map<HourBucket, Int>,
    val longestStreak: Int,
    val gaps: List<AbstinenceGap>,
    val exclusions: Set<GapExclusion>,
    val achievements: List<AchievementProgress>,
    val monthComparison: MonthComparison,
    val keywords: List<KeywordCount>,
) {
    /** True when [period] holds no entries at all (the diary itself may still have some). */
    val periodIsEmpty: Boolean get() = stats.totalCount == 0

    companion object {
        fun compute(
            entries: List<Entry>,
            period: InsightsPeriod,
            nowMillis: Long,
            zone: ZoneId,
            exclusions: Set<GapExclusion> = emptySet(),
        ): InsightsReport {
            val scoped = entriesInPeriod(entries, period, nowMillis, zone)
            return InsightsReport(
                period = period,
                stats = computeStats(scoped, nowMillis, zone),
                weeks = weeklyCounts(entries, nowMillis, zone, INSIGHTS_WEEKS),
                weekdays = weekdayCounts(scoped, zone),
                hourBuckets = hourBucketCounts(scoped, zone),
                longestStreak = longestStreakDays(scoped, zone),
                gaps = abstinenceGaps(entries, nowMillis, zone),
                exclusions = exclusions,
                achievements = computeAchievements(entries, nowMillis, zone, exclusions),
                monthComparison = monthOverMonth(entries, nowMillis, zone),
                keywords = topKeywords(scoped, INSIGHTS_TOP_KEYWORDS),
            )
        }
    }
}
