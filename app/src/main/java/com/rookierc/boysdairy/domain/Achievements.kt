package com.rookierc.boysdairy.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/*
 * Achievement wall of the full report. The product records and discourages overdoing it, so
 * honours celebrate staying away (gaps between entry days) and the "shame" badges hold up a
 * mirror to bingeing. Calendar days in the zone passed in; "now" is always injected.
 */

/** Which wall a badge sits on. */
enum class AchievementKind {
    /** Achievement wall: something to be proud of. */
    HONOR,

    /** "Dark history": a warning sign, unlocked by overdoing it. */
    SHAME,
}

/**
 * Badges in display order (honours first, then shame). [target] is what
 * [AchievementProgress.progress] has to reach. For a gap badge ([isGap]) progress is the number of
 * counted gaps of at least [minGapDays] days (see [abstinenceGaps]); a longer gap counts for every
 * lower tier too. Badges never comment on the body.
 */
enum class Achievement(val kind: AchievementKind, val target: Int, val minGapDays: Int = 0) {
    /** First entry ever. */
    FIRST_ENTRY(AchievementKind.HONOR, 1),

    /** 1 / 5 / 20 gaps of 3+ days. */
    GAP_3_X1(AchievementKind.HONOR, 1, minGapDays = 3),
    GAP_3_X5(AchievementKind.HONOR, 5, minGapDays = 3),
    GAP_3_X20(AchievementKind.HONOR, 20, minGapDays = 3),

    /** 1 / 5 / 10 gaps of 7+ days. */
    GAP_7_X1(AchievementKind.HONOR, 1, minGapDays = 7),
    GAP_7_X5(AchievementKind.HONOR, 5, minGapDays = 7),
    GAP_7_X10(AchievementKind.HONOR, 10, minGapDays = 7),

    /** 1 / 3 gaps of 15+ days (half a month). */
    GAP_15_X1(AchievementKind.HONOR, 1, minGapDays = 15),
    GAP_15_X3(AchievementKind.HONOR, 3, minGapDays = 15),

    /** 1 / 3 gaps of 30+ days. */
    GAP_30_X1(AchievementKind.HONOR, 1, minGapDays = 30),
    GAP_30_X3(AchievementKind.HONOR, 3, minGapDays = 30),

    /** One gap of 100+ days. */
    GAP_100_X1(AchievementKind.HONOR, 1, minGapDays = 100),

    /** Longest run of consecutive calendar days with an entry reaches 3 / 7. */
    STREAK_3(AchievementKind.SHAME, 3),
    STREAK_7(AchievementKind.SHAME, 7),

    /** 2 / 3 entries on one calendar day. */
    SAME_DAY_2(AchievementKind.SHAME, 2),
    SAME_DAY_3(AchievementKind.SHAME, 3),

    /** 5 entries within [BURST_WINDOW_DAYS] consecutive calendar days. */
    BURST(AchievementKind.SHAME, 5),

    /** 5 entries between 00:00 and 05:59. */
    NIGHT_OWL(AchievementKind.SHAME, 5),

    /** 5 entries with a mood afterwards in [REGRET_MOODS]. */
    REGRET(AchievementKind.SHAME, 5),

    /** 5 entries with BORED as the mood before. */
    BOREDOM(AchievementKind.SHAME, 5),
    ;

    /** True for the badges counted from gaps (the only ones a gap exclusion can affect). */
    val isGap: Boolean get() = minGapDays > 0
}

/** [progress] is capped at [target]; [unlocked] means it got there. */
data class AchievementProgress(
    val achievement: Achievement,
    val unlocked: Boolean,
    val progress: Int,
    val target: Int,
)

/** Window of [Achievement.BURST], in calendar days (today and the 6 days before count as 7). */
const val BURST_WINDOW_DAYS = 7

/** Moods afterwards that count for [Achievement.REGRET]. */
val REGRET_MOODS: Set<Mood> = setOf(Mood.GUILTY, Mood.EMPTY)

/**
 * Calendar days between two consecutive entry days: [start] has an entry, [end] has the next one,
 * nothing in between. [end] `null` = the gap is still open (from the latest entry day to today).
 * [days] is `ChronoUnit.DAYS` between them (entries on 1 and 4 October = 3), always >= 1.
 */
data class AbstinenceGap(val start: LocalDate, val end: LocalDate?, val days: Int) {
    val isOpen: Boolean get() = end == null
}

/**
 * Every gap between consecutive entry days, oldest first, plus the open gap from the latest entry
 * day to today when that is at least one day. Several entries on one day count once; entries dated
 * after today are ignored (that time has not passed yet). Empty without entries.
 */
fun abstinenceGaps(entries: List<Entry>, nowMillis: Long, zone: ZoneId): List<AbstinenceGap> {
    val today = nowMillis.toLocalDate(zone)
    val days = entries.map { it.timestamp.toLocalDate(zone) }.filter { it <= today }.toSortedSet()
    if (days.isEmpty()) return emptyList()
    val closed = days.zipWithNext { earlier, later -> AbstinenceGap(earlier, later, daysBetween(earlier, later)) }
    val latest = days.last()
    val open = if (latest < today) AbstinenceGap(latest, null, daysBetween(latest, today)) else null
    return if (open != null) closed + open else closed
}

/**
 * "This gap does not count" (the user forgot to log something in between).
 *
 * A closed gap is excluded exactly: the gap from [start] to [end]. Logging an entry inside it
 * splits it into two new gaps, which no longer match and count again.
 *
 * A gap excluded while still open ([orLater]) is recorded up to the day it was excluded ([end] =
 * that day): it matches the gap starting on [start] while it stays open and once it closes on
 * [end] or later, so closing it does not bring it back. An entry logged later for a day before
 * [end] splits it like a closed gap, and both parts count again.
 */
data class GapExclusion(val start: LocalDate, val end: LocalDate, val orLater: Boolean = false) {

    fun matches(gap: AbstinenceGap): Boolean {
        if (gap.start != start) return false
        val gapEnd = gap.end
        return if (orLater) gapEnd == null || gapEnd >= end else gapEnd == end
    }

    /** Stable storage form: `2026-10-01/2026-10-09`, or `2026-10-01/2026-10-09+` for [orLater]. */
    fun encode(): String = "$start/$end${if (orLater) OR_LATER else ""}"

    companion object {
        private const val OR_LATER = "+"

        /** Inverse of [encode]; `null` for anything malformed (or an end that is not after start). */
        fun decode(raw: String): GapExclusion? {
            val parts = raw.split('/')
            if (parts.size != 2) return null
            val orLater = parts[1].endsWith(OR_LATER)
            return try {
                val start = LocalDate.parse(parts[0])
                val end = LocalDate.parse(parts[1].removeSuffix(OR_LATER))
                if (end <= start) null else GapExclusion(start, end, orLater)
            } catch (malformed: DateTimeParseException) {
                null
            }
        }
    }
}

/**
 * The exclusion that takes [this] gap out of the count. An open gap is recorded up to today (its
 * start plus [AbstinenceGap.days]) and keeps matching once it closes; see [GapExclusion].
 */
fun AbstinenceGap.toExclusion(): GapExclusion =
    if (end != null) GapExclusion(start, end) else GapExclusion(start, start.plusDays(days.toLong()), orLater = true)

/** True when one of [exclusions] matches this gap. */
fun AbstinenceGap.isExcludedBy(exclusions: Set<GapExclusion>): Boolean = exclusions.any { it.matches(this) }

/** Gaps of at least a tier's length, newest first, split into those that count and those excluded. */
data class GapLedger(val counted: List<AbstinenceGap>, val excluded: List<AbstinenceGap>)

/** [GapLedger] of [gaps] (as returned by [abstinenceGaps]) for gaps of at least [minDays] days. */
fun gapLedger(gaps: List<AbstinenceGap>, exclusions: Set<GapExclusion>, minDays: Int): GapLedger {
    val (excluded, counted) = gaps.filter { it.days >= minDays }.asReversed().partition { it.isExcludedBy(exclusions) }
    return GapLedger(counted = counted, excluded = excluded)
}

/**
 * Progress of every [Achievement], in declaration order. Gaps matched by [excludedGaps] do not
 * count for any gap badge; nothing else looks at them.
 */
fun computeAchievements(
    entries: List<Entry>,
    nowMillis: Long,
    zone: ZoneId,
    excludedGaps: Set<GapExclusion> = emptySet(),
): List<AchievementProgress> {
    val countedGapDays = abstinenceGaps(entries, nowMillis, zone)
        .filterNot { it.isExcludedBy(excludedGaps) }
        .map { it.days }
    val entryDays = entries.map { it.timestamp.toLocalDate(zone) }
    val longestStreak = longestStreakDays(entries, zone)
    val busiestDay = entryDays.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
    val busiestWindow = mostInWindow(entryDays, BURST_WINDOW_DAYS)
    val night = hourBucketCounts(entries, zone).getValue(HourBucket.NIGHT)
    val regret = entries.count { it.moodAfter in REGRET_MOODS }
    val bored = entries.count { it.moodBefore == Mood.BORED }

    return Achievement.entries.map { achievement ->
        val raw = when (achievement) {
            Achievement.FIRST_ENTRY -> entries.size

            Achievement.GAP_3_X1,
            Achievement.GAP_3_X5,
            Achievement.GAP_3_X20,
            Achievement.GAP_7_X1,
            Achievement.GAP_7_X5,
            Achievement.GAP_7_X10,
            Achievement.GAP_15_X1,
            Achievement.GAP_15_X3,
            Achievement.GAP_30_X1,
            Achievement.GAP_30_X3,
            Achievement.GAP_100_X1,
            -> countedGapDays.count { it >= achievement.minGapDays }

            Achievement.STREAK_3,
            Achievement.STREAK_7,
            -> longestStreak

            Achievement.SAME_DAY_2,
            Achievement.SAME_DAY_3,
            -> busiestDay

            Achievement.BURST -> busiestWindow
            Achievement.NIGHT_OWL -> night
            Achievement.REGRET -> regret
            Achievement.BOREDOM -> bored
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
 * after saving an entry, computed with the same "now", zone and exclusions), in [Achievement]
 * declaration order. One missing from [before] counts as locked there; one that got locked again
 * is not reported.
 */
fun newlyUnlocked(before: List<AchievementProgress>, after: List<AchievementProgress>): List<Achievement> {
    val unlockedBefore = before.filter { it.unlocked }.mapTo(HashSet()) { it.achievement }
    return after
        .filter { it.unlocked && it.achievement !in unlockedBefore }
        .map { it.achievement }
        .distinct()
        .sortedBy { it.ordinal }
}

/** Most entries (one per element of [days]) inside any run of [windowDays] consecutive calendar days. */
private fun mostInWindow(days: List<LocalDate>, windowDays: Int): Int {
    val sorted = days.sorted()
    var best = 0
    var first = 0
    for (last in sorted.indices) {
        while (daysBetween(sorted[first], sorted[last]) >= windowDays) first++
        best = maxOf(best, last - first + 1)
    }
    return best
}

private fun daysBetween(earlier: LocalDate, later: LocalDate): Int =
    ChronoUnit.DAYS.between(earlier, later).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
