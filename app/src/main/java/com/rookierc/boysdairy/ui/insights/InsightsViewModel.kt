package com.rookierc.boysdairy.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.domain.AchievementProgress
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryStats
import com.rookierc.boysdairy.domain.HourBucket
import com.rookierc.boysdairy.domain.InsightsPeriod
import com.rookierc.boysdairy.domain.MonthComparison
import com.rookierc.boysdairy.domain.WeekBucket
import com.rookierc.boysdairy.domain.computeAchievements
import com.rookierc.boysdairy.domain.computeStats
import com.rookierc.boysdairy.domain.entriesInPeriod
import com.rookierc.boysdairy.domain.hourBucketCounts
import com.rookierc.boysdairy.domain.longestStreakDays
import com.rookierc.boysdairy.domain.monthOverMonth
import com.rookierc.boysdairy.domain.weekdayCounts
import com.rookierc.boysdairy.domain.weeklyCounts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.ZoneId

/**
 * Raw data only, like the home screen: the report depends on "today", so the UI derives it from
 * [entries] together with a date that refreshes on resume and at midnight.
 */
class InsightsViewModel(repository: EntryRepository) : ViewModel() {

    /** All entries, newest first. `null` until the first database read completes. */
    val entries: StateFlow<List<Entry>?> = repository.entries.stateIn<List<Entry>?>(
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { InsightsViewModel(appContainer().entryRepository) }
        }
    }
}

/** Number of weeks in the bar chart. */
const val INSIGHTS_WEEKS = 12

/**
 * Everything the report screen shows, computed in one go.
 *
 * [stats], [weekdays], [hourBuckets] and [longestStreak] only look at the entries inside [period];
 * [weeks], [achievements] and [monthComparison] always use the whole diary.
 */
data class InsightsReport(
    val period: InsightsPeriod,
    val stats: EntryStats,
    val weeks: List<WeekBucket>,
    val weekdays: Map<DayOfWeek, Int>,
    val hourBuckets: Map<HourBucket, Int>,
    val longestStreak: Int,
    val achievements: List<AchievementProgress>,
    val monthComparison: MonthComparison,
) {
    /** True when [period] holds no entries at all (the diary itself may still have some). */
    val periodIsEmpty: Boolean get() = stats.totalCount == 0

    companion object {
        fun compute(
            entries: List<Entry>,
            period: InsightsPeriod,
            nowMillis: Long,
            zone: ZoneId,
        ): InsightsReport {
            val scoped = entriesInPeriod(entries, period, nowMillis, zone)
            return InsightsReport(
                period = period,
                stats = computeStats(scoped, nowMillis, zone),
                weeks = weeklyCounts(entries, nowMillis, zone, INSIGHTS_WEEKS),
                weekdays = weekdayCounts(scoped, zone),
                hourBuckets = hourBucketCounts(scoped, zone),
                longestStreak = longestStreakDays(scoped, zone),
                achievements = computeAchievements(entries, nowMillis, zone),
                monthComparison = monthOverMonth(entries, nowMillis, zone),
            )
        }
    }
}
