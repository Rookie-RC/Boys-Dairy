package com.rookierc.boysdairy.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/*
 * Extra figures for the full report screen. Like Stats.kt everything works on calendar days and
 * local times in the zone passed in; "now" is always injected.
 */

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

/**
 * Badges of the achievement wall, in display order. [target] is what [AchievementProgress.progress]
 * has to reach. Badges only ever celebrate; nothing rewards a short session or comments on the body.
 */
enum class Achievement(val target: Int) {
    /** First entry ever. */
    FIRST_ENTRY(1),

    /** 10 / 50 / 100 entries in total. */
    TOTAL_10(10),
    TOTAL_50(50),
    TOTAL_100(100),

    /** Longest run of consecutive active days reaches 3 / 7. */
    STREAK_3(3),
    STREAK_7(7),

    /** A gap of 7 / 30 calendar days between two entries, or since the latest one. */
    GAP_7(7),
    GAP_30(30),

    /** 10 partner entries. */
    PARTNER_10(10),

    /** 5 entries between 00:00 and 05:59. */
    NIGHT_OWL(5),

    /** 5 entries between 06:00 and 11:59. */
    EARLY_BIRD(5),

    /** One entry of 60 minutes or more. */
    MARATHON(1),

    /** One entry rated 10. */
    PEAK_PLEASURE(1),

    /** 10 entries with RELAXED as the mood afterwards. */
    SAGE_MODE(10),
}

/** [progress] is capped at [target]; [unlocked] means it got there. */
data class AchievementProgress(
    val achievement: Achievement,
    val unlocked: Boolean,
    val progress: Int,
    val target: Int,
)

/** Minimum duration for [Achievement.MARATHON]. */
const val MARATHON_MINUTES = 60

/** Pleasure score needed for [Achievement.PEAK_PLEASURE]. */
const val PEAK_PLEASURE_SCORE = 10

/** Progress of every [Achievement], in declaration order. */
fun computeAchievements(entries: List<Entry>, nowMillis: Long, zone: ZoneId): List<AchievementProgress> {
    val total = entries.size
    val longestStreak = longestStreakDays(entries, zone)
    val longestGap = longestGapIncludingNow(entries, nowMillis, zone)
    val partner = entries.count { it.type == EntryType.PARTNER }
    val buckets = hourBucketCounts(entries, zone)
    val longSessions = entries.count { it.durationMinutes >= MARATHON_MINUTES }
    val peakSessions = entries.count { it.pleasure >= PEAK_PLEASURE_SCORE }
    val relaxedAfter = entries.count { it.moodAfter == Mood.RELAXED }

    return Achievement.entries.map { achievement ->
        val raw = when (achievement) {
            Achievement.FIRST_ENTRY,
            Achievement.TOTAL_10,
            Achievement.TOTAL_50,
            Achievement.TOTAL_100,
            -> total

            Achievement.STREAK_3,
            Achievement.STREAK_7,
            -> longestStreak

            Achievement.GAP_7,
            Achievement.GAP_30,
            -> longestGap

            Achievement.PARTNER_10 -> partner
            Achievement.NIGHT_OWL -> buckets.getValue(HourBucket.NIGHT)
            Achievement.EARLY_BIRD -> buckets.getValue(HourBucket.MORNING)
            Achievement.MARATHON -> longSessions
            Achievement.PEAK_PLEASURE -> peakSessions
            Achievement.SAGE_MODE -> relaxedAfter
        }
        val progress = raw.coerceIn(0, achievement.target)
        AchievementProgress(
            achievement = achievement,
            unlocked = progress >= achievement.target,
            progress = progress,
            target = achievement.target,
        )
    }
}

/**
 * Achievements that are unlocked in [after] but were not in [before] (e.g. the progress before and
 * after saving an entry, computed with the same "now" and zone), in [Achievement] declaration order.
 * One missing from [before] counts as locked there; one that got locked again is not reported.
 */
fun newlyUnlocked(before: List<AchievementProgress>, after: List<AchievementProgress>): List<Achievement> {
    val unlockedBefore = before.filter { it.unlocked }.mapTo(HashSet()) { it.achievement }
    return after
        .filter { it.unlocked && it.achievement !in unlockedBefore }
        .map { it.achievement }
        .distinct()
        .sortedBy { it.ordinal }
}

/**
 * Largest calendar-day gap between two consecutive entry days, also counting the still-open gap
 * from the latest entry day to today. 0 without entries (there is no gap before the first one).
 */
private fun longestGapIncludingNow(entries: List<Entry>, nowMillis: Long, zone: ZoneId): Int {
    val days = entries.map { it.timestamp.toLocalDate(zone) }.toSortedSet()
    if (days.isEmpty()) return 0
    val between = days.zipWithNext { earlier, later -> ChronoUnit.DAYS.between(earlier, later) }.maxOrNull() ?: 0L
    val sinceLatest = daysSince(days.last(), nowMillis.toLocalDate(zone))
    return maxOf(between, sinceLatest).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

private fun LocalDate.startOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

private fun Entry.zoned(zone: ZoneId) = Instant.ofEpochMilli(timestamp).atZone(zone)
