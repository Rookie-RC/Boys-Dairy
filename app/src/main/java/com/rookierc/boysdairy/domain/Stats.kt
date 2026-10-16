package com.rookierc.boysdairy.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Coarse time-of-day buckets (local time). */
enum class HourBucket {
    /** 00:00-05:59 */
    NIGHT,

    /** 06:00-11:59 */
    MORNING,

    /** 12:00-17:59 */
    AFTERNOON,

    /** 18:00-23:59 */
    EVENING;

    companion object {
        fun forHour(hour: Int): HourBucket = when (hour) {
            in 0..5 -> NIGHT
            in 6..11 -> MORNING
            in 12..17 -> AFTERNOON
            else -> EVENING
        }
    }
}

/**
 * Aggregate figures over the whole diary. "Days" are calendar days in the zone passed to
 * [computeStats], never 24-hour blocks, except for [averageIntervalDays] which uses exact
 * elapsed time.
 *
 * @property last7DaysCount entries whose local date is within the last 7 calendar days, today included.
 * @property last30DaysCount same for 30 days.
 * @property daysSinceLast calendar days since the most recent entry (never negative); null without entries.
 * @property consecutiveActiveDays length of the run of consecutive days with at least one entry
 *   that ends today or yesterday; 0 when the latest active day is older (or there are no entries).
 * @property longestGapDays largest calendar-day difference between two consecutive distinct entry
 *   days (entries on adjacent days give 1); null with fewer than two distinct days.
 * @property averageIntervalDays mean elapsed days between consecutive entries among the most recent
 *   11 (up to 10 intervals); null with fewer than two entries.
 * @property moodBeforeCounts only moods that occur, in [Mood] declaration order.
 * @property busiestWeekday ties resolve to the earliest day of the week (Monday first).
 * @property busiestHourBucket ties resolve to the earliest bucket (NIGHT first).
 */
data class EntryStats(
    val totalCount: Int,
    val soloCount: Int,
    val partnerCount: Int,
    val last7DaysCount: Int,
    val last30DaysCount: Int,
    val daysSinceLast: Long?,
    val consecutiveActiveDays: Int,
    val longestGapDays: Long?,
    val averageIntervalDays: Double?,
    val averageDurationMinutes: Double?,
    val averagePleasure: Double?,
    val moodBeforeCounts: Map<Mood, Int>,
    val moodAfterCounts: Map<Mood, Int>,
    val busiestWeekday: DayOfWeek?,
    val busiestHourBucket: HourBucket?,
)

private const val DAY_MILLIS = 86_400_000.0
private const val INTERVAL_SAMPLE_SIZE = 11

fun computeStats(entries: List<Entry>, nowMillis: Long, zone: ZoneId): EntryStats {
    val today = nowMillis.toLocalDate(zone)
    val zoned = entries.map { Instant.ofEpochMilli(it.timestamp).atZone(zone) }
    val dates = zoned.map { it.toLocalDate() }

    val last7Start = today.minusDays(6)
    val last30Start = today.minusDays(29)

    val latest = entries.maxByOrNull { it.timestamp }
    val distinctDays = dates.toSortedSet()

    val longestGap = if (distinctDays.size < 2) {
        null
    } else {
        distinctDays.zipWithNext { earlier, later -> ChronoUnit.DAYS.between(earlier, later) }.max()
    }

    val recent = entries.sortedByDescending { it.timestamp }.take(INTERVAL_SAMPLE_SIZE)
    val averageInterval = if (recent.size < 2) {
        null
    } else {
        recent.zipWithNext { newer, older -> (newer.timestamp - older.timestamp) / DAY_MILLIS }.average()
    }

    val weekdayCounts = zoned.groupingBy { it.dayOfWeek }.eachCount()
    val bucketCounts = zoned.groupingBy { HourBucket.forHour(it.hour) }.eachCount()

    return EntryStats(
        totalCount = entries.size,
        soloCount = entries.count { it.type == EntryType.SOLO },
        partnerCount = entries.count { it.type == EntryType.PARTNER },
        last7DaysCount = dates.count { it in last7Start..today },
        last30DaysCount = dates.count { it in last30Start..today },
        daysSinceLast = latest?.let { daysSince(it.timestamp, nowMillis, zone) },
        consecutiveActiveDays = consecutiveActiveDays(distinctDays, today),
        longestGapDays = longestGap,
        averageIntervalDays = averageInterval,
        averageDurationMinutes = entries.takeIf { it.isNotEmpty() }?.map { it.durationMinutes }?.average(),
        averagePleasure = entries.takeIf { it.isNotEmpty() }?.map { it.pleasure }?.average(),
        moodBeforeCounts = moodCounts(entries.map { it.moodBefore }),
        moodAfterCounts = moodCounts(entries.map { it.moodAfter }),
        busiestWeekday = busiest(DayOfWeek.entries, weekdayCounts),
        busiestHourBucket = busiest(HourBucket.entries, bucketCounts),
    )
}

/** Run of consecutive active days ending today, or ending yesterday if today has no entry yet. */
private fun consecutiveActiveDays(activeDays: Set<LocalDate>, today: LocalDate): Int {
    var cursor = when {
        today in activeDays -> today
        today.minusDays(1) in activeDays -> today.minusDays(1)
        else -> return 0
    }
    var length = 0
    while (cursor in activeDays) {
        length++
        cursor = cursor.minusDays(1)
    }
    return length
}

private fun moodCounts(moods: List<Mood>): Map<Mood, Int> {
    val counts = moods.groupingBy { it }.eachCount()
    val ordered = LinkedHashMap<Mood, Int>()
    for (mood in Mood.entries) {
        counts[mood]?.let { ordered[mood] = it }
    }
    return ordered
}

/** The key with the highest count; the earliest in [order] wins ties. Null when nothing was counted. */
private fun <T> busiest(order: List<T>, counts: Map<T, Int>): T? {
    var best: T? = null
    var bestCount = 0
    for (key in order) {
        val count = counts[key] ?: 0
        if (count > bestCount) {
            best = key
            bestCount = count
        }
    }
    return best
}
