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
import com.rookierc.boysdairy.domain.WeekBucket
import com.rookierc.boysdairy.domain.computeAchievements
import com.rookierc.boysdairy.domain.computeStats
import com.rookierc.boysdairy.domain.hourBucketCounts
import com.rookierc.boysdairy.domain.longestStreakDays
import com.rookierc.boysdairy.domain.weekdayCounts
import com.rookierc.boysdairy.domain.weeklyCounts
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { InsightsViewModel(appContainer().entryRepository) }
        }
    }
}

/** Number of weeks in the bar chart. */
const val INSIGHTS_WEEKS = 12

/** Everything the report screen shows, computed in one go. */
data class InsightsReport(
    val stats: EntryStats,
    val weeks: List<WeekBucket>,
    val weekdays: Map<DayOfWeek, Int>,
    val hourBuckets: Map<HourBucket, Int>,
    val longestStreak: Int,
    val achievements: List<AchievementProgress>,
) {
    companion object {
        fun compute(entries: List<Entry>, nowMillis: Long, zone: ZoneId) = InsightsReport(
            stats = computeStats(entries, nowMillis, zone),
            weeks = weeklyCounts(entries, nowMillis, zone, INSIGHTS_WEEKS),
            weekdays = weekdayCounts(entries, zone),
            hourBuckets = hourBucketCounts(entries, zone),
            longestStreak = longestStreakDays(entries, zone),
            achievements = computeAchievements(entries, nowMillis, zone),
        )
    }
}
