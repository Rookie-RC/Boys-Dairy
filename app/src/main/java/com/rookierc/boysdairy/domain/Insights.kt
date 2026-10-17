package com.rookierc.boysdairy.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * Extra figures for the full report screen. Like Stats.kt everything works on calendar days and
 * local times in the zone passed in; "now" is always injected.
 */

/** Time range the full report can be narrowed to. */
enum class InsightsPeriod {
    /** The last 30 calendar days, today included (same window as [EntryStats.last30DaysCount]). */
    LAST_30_DAYS,

    /** The last 90 calendar days, today included. */
    LAST_90_DAYS,

    /** 1 January of the current local calendar year up to today. */
    THIS_YEAR,

    /** Everything, future-dated entries included. */
    ALL,
}

/** First calendar day of [period] for [today] (inclusive); null for [InsightsPeriod.ALL]. */
fun InsightsPeriod.startDate(today: LocalDate): LocalDate? = when (this) {
    InsightsPeriod.LAST_30_DAYS -> today.minusDays(29)
    InsightsPeriod.LAST_90_DAYS -> today.minusDays(89)
    InsightsPeriod.THIS_YEAR -> today.withDayOfYear(1)
    InsightsPeriod.ALL -> null
}

/**
 * Entries whose local date falls in [period]: from [startDate] to today, both inclusive, so
 * entries dated after today only show up under [InsightsPeriod.ALL] (which returns [entries]
 * as is). Keeps the input order.
 */
fun entriesInPeriod(
    entries: List<Entry>,
    period: InsightsPeriod,
    nowMillis: Long,
    zone: ZoneId,
): List<Entry> {
    val today = nowMillis.toLocalDate(zone)
    val start = period.startDate(today) ?: return entries
    return entries.filter { it.timestamp.toLocalDate(zone) in start..today }
}

/**
 * Entries of the current calendar month so far against the same stretch of the previous month.
 *
 * @property thisMonth entries from the 1st of this month to today.
 * @property lastMonthSamePeriod entries from the 1st of last month to [lastMonthEnd].
 * @property lastMonthEnd the same day of the month last month, or that month's last day when it
 *   is shorter (31 March compares with 1-28 or 1-29 February).
 */
data class MonthComparison(
    val thisMonth: Int,
    val lastMonthSamePeriod: Int,
    val lastMonthEnd: LocalDate,
) {
    /** Positive when this month is ahead. */
    val difference: Int get() = thisMonth - lastMonthSamePeriod

    /** The 1st of the month [lastMonthEnd] is in. */
    val lastMonthStart: LocalDate get() = lastMonthEnd.withDayOfMonth(1)
}

/**
 * Month-over-month comparison on calendar days in [zone]; entries dated after today are ignored.
 * Independent of any [InsightsPeriod].
 */
fun monthOverMonth(entries: List<Entry>, nowMillis: Long, zone: ZoneId): MonthComparison {
    val today = nowMillis.toLocalDate(zone)
    val thisMonthStart = today.withDayOfMonth(1)
    val lastMonth = YearMonth.from(today).minusMonths(1)
    val lastMonthStart = lastMonth.atDay(1)
    val lastMonthEnd = lastMonth.atDay(minOf(today.dayOfMonth, lastMonth.lengthOfMonth()))

    var thisMonth = 0
    var previous = 0
    for (entry in entries) {
        val day = entry.timestamp.toLocalDate(zone)
        when (day) {
            in thisMonthStart..today -> thisMonth++
            in lastMonthStart..lastMonthEnd -> previous++
        }
    }
    return MonthComparison(thisMonth = thisMonth, lastMonthSamePeriod = previous, lastMonthEnd = lastMonthEnd)
}

/**
 * Entries of one Monday-first calendar week.
 *
 * @property weekStart the Monday of the week.
 * @property averagePleasure mean pleasure of that week's entries; null for an empty week.
 */
data class WeekBucket(
    val weekStart: LocalDate,
    val solo: Int,
    val partner: Int,
    val averagePleasure: Double?,
) {
    val total: Int get() = solo + partner
}

/**
 * The last [weeks] calendar weeks (Monday first), oldest first, ending with the current week.
 * Every week is present, empty ones included. Entries dated before the first week or after
 * today are ignored.
 */
fun weeklyCounts(
    entries: List<Entry>,
    nowMillis: Long,
    zone: ZoneId,
    weeks: Int = 12,
): List<WeekBucket> {
    require(weeks > 0) { "weeks must be positive" }
    val today = nowMillis.toLocalDate(zone)
    val currentWeekStart = today.startOfWeek()
    val firstWeekStart = currentWeekStart.minusWeeks((weeks - 1).toLong())

    val byWeek = entries
        .filter { it.timestamp.toLocalDate(zone) in firstWeekStart..today }
        .groupBy { it.timestamp.toLocalDate(zone).startOfWeek() }

    return (0 until weeks).map { index ->
        val weekStart = firstWeekStart.plusWeeks(index.toLong())
        val weekEntries = byWeek[weekStart].orEmpty()
        WeekBucket(
            weekStart = weekStart,
            solo = weekEntries.count { it.type == EntryType.SOLO },
            partner = weekEntries.count { it.type == EntryType.PARTNER },
            averagePleasure = weekEntries.takeIf { it.isNotEmpty() }?.map { it.pleasure }?.average(),
        )
    }
}

/** Entries per local day of the week. All seven days are present, Monday to Sunday. */
fun weekdayCounts(entries: List<Entry>, zone: ZoneId): Map<DayOfWeek, Int> {
    val counts = entries.groupingBy { it.zoned(zone).dayOfWeek }.eachCount()
    return DayOfWeek.entries.associateWith { counts[it] ?: 0 }
}

/** Entries per local time-of-day bucket. All four buckets are present, NIGHT first. */
fun hourBucketCounts(entries: List<Entry>, zone: ZoneId): Map<HourBucket, Int> {
    val counts = entries.groupingBy { HourBucket.forHour(it.zoned(zone).hour) }.eachCount()
    return HourBucket.entries.associateWith { counts[it] ?: 0 }
}

/**
 * Longest run of consecutive calendar days with at least one entry, over the whole history
 * (several entries on one day count once). 0 without entries.
 */
fun longestStreakDays(entries: List<Entry>, zone: ZoneId): Int {
    val days = entries.map { it.timestamp.toLocalDate(zone) }.toSortedSet()
    var longest = 0
    var current = 0
    var previous: LocalDate? = null
    for (day in days) {
        current = if (previous != null && previous.plusDays(1) == day) current + 1 else 1
        longest = maxOf(longest, current)
        previous = day
    }
    return longest
}

/**
 * All keys sharing the highest count (several on a tie), or an empty set when every count is 0.
 * Used to highlight the busiest weekday / time of day.
 */
fun <K> busiestKeys(counts: Map<K, Int>): Set<K> {
    val max = counts.values.maxOrNull() ?: 0
    if (max <= 0) return emptySet()
    return counts.filterValues { it == max }.keys
}

private fun LocalDate.startOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun Entry.zoned(zone: ZoneId) = Instant.ofEpochMilli(timestamp).atZone(zone)
