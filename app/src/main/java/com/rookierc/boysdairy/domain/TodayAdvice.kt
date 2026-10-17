package com.rookierc.boysdairy.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * The tongue-in-cheek "today's almanac" on the home screen: a verdict on whether today suits a new
 * entry that deliberately leans towards "better not", and never counts down to anything. Like
 * Stats.kt everything works on calendar days in the zone passed in; "now" is always injected.
 * Display only: never stored, never in a backup and never part of the AI prompt.
 */

/** How many of the latest distinct entry days (several entries on one day count once) the typical gap reads. */
const val ADVICE_SAMPLE_DAYS = 10

/** Distinct entry days needed before there is a typical gap, i.e. at least two gaps between them. */
const val ADVICE_MIN_DAYS = 3

/** Consecutive entry days up to yesterday that make today an [AdviceReason.STREAK]. */
const val ADVICE_STREAK_DAYS = 3

/** The almanac's verdict, from "avoid" to (a low-key) "favorable". */
enum class AdviceLevel { AVOID, UNFAVORABLE, NEUTRAL, FAVORABLE }

/** Why today got its [level]; [todayAdvice] checks them in declaration order and stops at the first match. */
enum class AdviceReason(val level: AdviceLevel) {
    /** Today already has an entry, or the latest entry is dated after today (the clock was changed). */
    LOGGED_TODAY(AdviceLevel.AVOID),

    /** No entry today, but entries on [ADVICE_STREAK_DAYS] or more consecutive days up to yesterday. */
    STREAK(AdviceLevel.AVOID),

    /** Fewer calendar days since the latest entry than the typical gap. */
    WITHIN_RHYTHM(AdviceLevel.UNFAVORABLE),

    /** The typical gap (or more) since the latest entry. */
    RHYTHM_REACHED(AdviceLevel.FAVORABLE),

    /** Too few entry days for a typical gap, and the latest entry was yesterday. */
    LOGGED_YESTERDAY(AdviceLevel.UNFAVORABLE),

    /** Too few entry days for a typical gap, and the latest entry is older than yesterday. */
    NO_RHYTHM(AdviceLevel.NEUTRAL),

    /** No entries at all. */
    NO_ENTRIES(AdviceLevel.NEUTRAL),
}

/**
 * @property level always `reason.level`.
 * @property day the local date the advice is for ("today" in the zone it was computed in); also
 *   what picks the day's quip, 宜 / 忌 items and calm line.
 * @property streakDays for [AdviceReason.STREAK] the number of consecutive entry days up to
 *   yesterday ([ADVICE_STREAK_DAYS] or more); 0 for every other reason.
 */
data class TodayAdvice(
    val level: AdviceLevel,
    val reason: AdviceReason,
    val day: LocalDate,
    val streakDays: Int = 0,
) {
    init {
        require(level == reason.level) { "$reason is $level-level, not ${reason.level}" }
        val streakFits = if (reason == AdviceReason.STREAK) streakDays >= ADVICE_STREAK_DAYS else streakDays == 0
        require(streakFits) { "streakDays $streakDays does not fit $reason" }
    }
}

/**
 * Today's verdict for [entries] (any order) as of [nowMillis] in [zone]. The first matching rule wins:
 *  1. today has an entry, or the latest entry is after today: [AdviceReason.LOGGED_TODAY];
 *  2. [ADVICE_STREAK_DAYS]+ consecutive entry days up to yesterday: [AdviceReason.STREAK];
 *  3. with a [typicalGapDays]: fewer days since the latest entry than that gap is
 *     [AdviceReason.WITHIN_RHYTHM], otherwise (exactly the gap included) [AdviceReason.RHYTHM_REACHED];
 *  4. without one: latest entry yesterday is [AdviceReason.LOGGED_YESTERDAY], older is
 *     [AdviceReason.NO_RHYTHM], no entries at all is [AdviceReason.NO_ENTRIES].
 */
fun todayAdvice(entries: List<Entry>, nowMillis: Long, zone: ZoneId): TodayAdvice {
    val today = nowMillis.toLocalDate(zone)
    val days = entries.mapTo(sortedSetOf()) { it.timestamp.toLocalDate(zone) }

    fun advice(reason: AdviceReason, streakDays: Int = 0) =
        TodayAdvice(level = reason.level, reason = reason, day = today, streakDays = streakDays)

    val latest = days.lastOrNull() ?: return advice(AdviceReason.NO_ENTRIES)
    if (!latest.isBefore(today)) return advice(AdviceReason.LOGGED_TODAY)

    // From here on every entry day is before today.
    val yesterday = today.minusDays(1)
    val streak = consecutiveDaysEndingOn(days, yesterday)
    if (streak >= ADVICE_STREAK_DAYS) return advice(AdviceReason.STREAK, streakDays = streak)

    val typicalGap = typicalGapDays(days)
    return when {
        typicalGap != null && daysSince(latest, today) < typicalGap -> advice(AdviceReason.WITHIN_RHYTHM)
        typicalGap != null -> advice(AdviceReason.RHYTHM_REACHED)
        latest == yesterday -> advice(AdviceReason.LOGGED_YESTERDAY)
        else -> advice(AdviceReason.NO_RHYTHM)
    }
}

/**
 * The user's typical gap between entry days: the median calendar-day gap between consecutive days
 * among the latest [ADVICE_SAMPLE_DAYS] distinct ones in [entryDays]; with an even number of gaps
 * the mean of the two middle ones, rounded half up. Always at least 1. `null` with fewer than
 * [ADVICE_MIN_DAYS] distinct days.
 */
internal fun typicalGapDays(entryDays: Collection<LocalDate>): Long? {
    val recent = entryDays.toSortedSet().toList().takeLast(ADVICE_SAMPLE_DAYS)
    if (recent.size < ADVICE_MIN_DAYS) return null
    val gaps = recent.zipWithNext { earlier, later -> ChronoUnit.DAYS.between(earlier, later) }
    return medianRoundedHalfUp(gaps).coerceAtLeast(1L)
}

/**
 * [count] distinct indices into a list of [size] items for the day [epochDay]: the same all day
 * long, and a different set the next day whenever `count < size`. When `size <= count` that is
 * every index once (so fewer than [count] when `size < count`); empty when either is 0.
 *
 * Starts at `epochDay mod size` and walks in steps coprime to [size] (the smallest one from half
 * of it up), so the picks never repeat within a day and are spread over the list.
 */
fun pickDistinctByDay(epochDay: Long, size: Int, count: Int): List<Int> {
    require(size >= 0) { "size must not be negative: $size" }
    require(count >= 0) { "count must not be negative: $count" }
    val picks = minOf(size, count)
    if (picks == 0) return emptyList()
    val start = Math.floorMod(epochDay, size.toLong())
    val step = spreadStep(size).toLong()
    return List(picks) { i -> ((start + i * step) % size).toInt() }
}

/** Smallest step from `ceil(size / 2)` up that shares no factor with [size] (positive). */
private fun spreadStep(size: Int): Int {
    var step = (size + 1) / 2
    while (gcd(step, size) != 1) step++
    return step
}

private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** Length of the run of consecutive days in [days] that ends on [end]; 0 when [end] is not in it. */
private fun consecutiveDaysEndingOn(days: Set<LocalDate>, end: LocalDate): Int {
    var cursor = end
    var length = 0
    while (cursor in days) {
        length++
        cursor = cursor.minusDays(1)
    }
    return length
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
