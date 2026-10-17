package com.rookierc.boysdairy.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * The tongue-in-cheek "next time" forecast on the home screen: when the next entry would land if
 * the user kept their own recent rhythm. Like Stats.kt everything works on calendar days and local
 * times in the zone passed in; "now" is always injected. Display only: never stored, never in a
 * backup and never part of the AI prompt.
 */

/** How many of the latest distinct entry days (several entries on one day count once) the forecast reads. */
const val NEXT_TIME_SAMPLE_DAYS = 10

/** Distinct entry days needed before there is a forecast, i.e. at least two gaps between them. */
const val NEXT_TIME_MIN_DAYS = 3

sealed interface NextTimeForecast {

    /**
     * Fewer than [NEXT_TIME_MIN_DAYS] distinct entry days so far: no forecast yet.
     *
     * @property loggedDays distinct calendar days with at least one entry (1 or 2).
     */
    data class NotEnoughDays(val loggedDays: Int) : NextTimeForecast {
        /** Distinct days still to log before there is a forecast (1 or 2). */
        val missingDays: Int get() = NEXT_TIME_MIN_DAYS - loggedDays
    }

    /**
     * @property typicalIntervalDays median calendar-day gap between consecutive entry days among the
     *   latest [NEXT_TIME_SAMPLE_DAYS] distinct days; with an even number of gaps the mean of the two
     *   middle ones, rounded half up. Always at least 1.
     * @property latestDay local date of the most recent entry. Can be after today when the clock
     *   was changed (or a backup came from a phone with a different clock).
     * @property predictedDate [latestDay] plus [typicalIntervalDays], except that a [latestDay]
     *   after today counts as today (the cooldown card's "days since" never goes negative either),
     *   so a future-dated entry never makes the forecast look overdue.
     * @property daysUntil calendar days from today to [predictedDate]: 0 = today, negative =
     *   [predictedDate] has passed by that many days.
     * @property usualTime the most common time of day among all entries on those latest days; on a
     *   tie, the tied bucket the most recent of those entries falls in.
     */
    data class Ready(
        val typicalIntervalDays: Long,
        val latestDay: LocalDate,
        val predictedDate: LocalDate,
        val daysUntil: Long,
        val usualTime: HourBucket,
    ) : NextTimeForecast
}

/**
 * The forecast for [entries] (any order) as of [nowMillis] in [zone]; `null` without entries.
 * See [NextTimeForecast.Ready] for how each figure is derived.
 */
fun forecastNextTime(entries: List<Entry>, nowMillis: Long, zone: ZoneId): NextTimeForecast? {
    if (entries.isEmpty()) return null

    val entriesByDay = groupEntriesByDate(entries, zone)
    val recentDays = entriesByDay.keys.sortedDescending().take(NEXT_TIME_SAMPLE_DAYS).asReversed()
    if (recentDays.size < NEXT_TIME_MIN_DAYS) {
        return NextTimeForecast.NotEnoughDays(loggedDays = recentDays.size)
    }

    val gaps = recentDays.zipWithNext { earlier, later -> ChronoUnit.DAYS.between(earlier, later) }
    val typicalInterval = medianRoundedHalfUp(gaps).coerceAtLeast(1L)

    val today = nowMillis.toLocalDate(zone)
    val latestDay = recentDays.last()
    val predicted = minOf(latestDay, today).plusDays(typicalInterval)

    return NextTimeForecast.Ready(
        typicalIntervalDays = typicalInterval,
        latestDay = latestDay,
        predictedDate = predicted,
        daysUntil = ChronoUnit.DAYS.between(today, predicted),
        usualTime = usualTime(recentDays.flatMap { entriesByDay.getValue(it) }, zone),
    )
}

/** Median of non-negative [values] (not empty); the mean of the two middle values rounded half up when even. */
private fun medianRoundedHalfUp(values: List<Long>): Long {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) {
        sorted[middle]
    } else {
        (sorted[middle - 1] + sorted[middle] + 1) / 2
    }
}

/** Most common [HourBucket] of [entries] (not empty); ties go to the tied bucket seen most recently. */
private fun usualTime(entries: List<Entry>, zone: ZoneId): HourBucket {
    val busiest = busiestKeys(hourBucketCounts(entries, zone))
    return entries
        .sortedByDescending { it.timestamp }
        .map { HourBucket.forHour(Instant.ofEpochMilli(it.timestamp).atZone(zone).hour) }
        .first { it in busiest }
}
