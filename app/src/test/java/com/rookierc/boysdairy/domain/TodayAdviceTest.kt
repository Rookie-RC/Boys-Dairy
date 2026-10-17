package com.rookierc.boysdairy.domain

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class TodayAdviceTest {

    // Saturday 17 Oct 2026, 12:00 Asia/Shanghai.
    private val now = millisOf(2026, 10, 17, 12, 0)
    private val today = LocalDate.of(2026, 10, 17)
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    /** Fixed seed: inputs are shuffled to prove order never matters, reproducibly. */
    private val random = Random(20261017)

    private fun entryOn(month: Int, day: Int, hour: Int = 12, year: Int = 2026, minute: Int = 0, zone: ZoneId = TEST_ZONE) =
        testEntry(millisOf(year, month, day, hour, minute, zone))

    /** One entry at noon on each of [days] (October 2026), shuffled so input order never matters. */
    private fun octoberDays(vararg days: Int, hour: Int = 12) = days.map { entryOn(10, it, hour) }.shuffled(random)

    private fun advice(entries: List<Entry>, nowMillis: Long = now, zone: ZoneId = TEST_ZONE) =
        todayAdvice(entries, nowMillis, zone)

    private fun assertReason(expected: AdviceReason, actual: TodayAdvice) {
        assertEquals("reason of $actual", expected, actual.reason)
        assertEquals("level of $actual", expected.level, actual.level)
    }

    private fun days(vararg dayOfMonth: Int) = dayOfMonth.map { LocalDate.of(2026, 10, it) }

    // ---- rule 1: already logged today ----------------------------------------------------------

    @Test
    fun noEntriesIsNeutral() {
        val result = advice(emptyList())
        assertEquals(TodayAdvice(AdviceLevel.NEUTRAL, AdviceReason.NO_ENTRIES, today), result)
    }

    @Test
    fun anEntryTodayMeansAvoid() {
        val result = advice(listOf(entryOn(10, 17, hour = 8)))
        assertEquals(TodayAdvice(AdviceLevel.AVOID, AdviceReason.LOGGED_TODAY, today), result)
    }

    @Test
    fun anEntryTodayBeatsEveryOtherRule() {
        // A streak up to yesterday (rule 2) and a typical gap of 1 (rule 3) as well.
        assertReason(AdviceReason.LOGGED_TODAY, advice(octoberDays(12, 13, 14, 15, 16, 17)))
        // A long-gone rhythm that would be "reached".
        assertReason(AdviceReason.LOGGED_TODAY, advice(octoberDays(1, 5, 9, 17)))
    }

    @Test
    fun aLaterEntryTodayStillCountsAsToday() {
        // 12:00 now, entry stamped 23:30 tonight: same calendar day.
        assertReason(AdviceReason.LOGGED_TODAY, advice(listOf(entryOn(10, 17, hour = 23, minute = 30))))
    }

    @Test
    fun aLatestEntryAfterTodayCountsAsLoggedToday() {
        // Clock set back: entries go up to the 20th, nothing on the 17th itself.
        assertReason(AdviceReason.LOGGED_TODAY, advice(octoberDays(10, 14, 20)))
        // Far ahead, on its own.
        assertReason(AdviceReason.LOGGED_TODAY, advice(listOf(entryOn(1, 3, year = 2027))))
        // Ahead of a streak up to yesterday: rule 1 still comes first.
        assertReason(AdviceReason.LOGGED_TODAY, advice(octoberDays(14, 15, 16, 25)))
    }

    // ---- rule 2: streak up to yesterday ----------------------------------------------------------

    @Test
    fun threeDaysInARowUpToYesterdayIsAStreak() {
        val result = advice(octoberDays(14, 15, 16))
        assertEquals(TodayAdvice(AdviceLevel.AVOID, AdviceReason.STREAK, today, streakDays = 3), result)
    }

    @Test
    fun theStreakCountsTheWholeRun() {
        assertEquals(7, advice(octoberDays(3, 10, 11, 12, 13, 14, 15, 16)).streakDays)
        // Across a month end.
        val acrossMonths = listOf(entryOn(9, 29), entryOn(9, 30), entryOn(10, 1), entryOn(10, 2))
        val result = advice(acrossMonths, nowMillis = millisOf(2026, 10, 3, 9, 0))
        assertReason(AdviceReason.STREAK, result)
        assertEquals(4, result.streakDays)
    }

    @Test
    fun twoDaysInARowIsNotAStreak() {
        // Only two distinct days, so no typical gap either: rule 4, logged yesterday.
        assertReason(AdviceReason.LOGGED_YESTERDAY, advice(octoberDays(15, 16)))
    }

    @Test
    fun aStreakMustReachYesterday() {
        // 12–15 in a row, but nothing on the 16th: gaps 1, 1, 1 -> 1; 2 days since -> reached.
        assertReason(AdviceReason.RHYTHM_REACHED, advice(octoberDays(12, 13, 14, 15)))
        // A hole on the 15th breaks the run that ends yesterday (only the 16th): gaps 1, 2 -> 2, 1 day since.
        assertReason(AdviceReason.WITHIN_RHYTHM, advice(octoberDays(13, 14, 16)))
    }

    @Test
    fun aStreakBeatsTheRhythm() {
        // Gaps 4, 9, 1, 1 -> sorted 1, 1, 4, 9 -> (1 + 4) / 2 = 2.5 -> 3; 1 day since < 3 would be
        // "within rhythm", but three days in a row up to yesterday come first.
        assertEquals(3L, typicalGapDays(days(1, 5, 14, 15, 16)))
        assertReason(AdviceReason.STREAK, advice(octoberDays(1, 5, 14, 15, 16)))
    }

    @Test
    fun severalEntriesADayDoNotMakeAStreak() {
        val entries = listOf(
            entryOn(10, 15, hour = 9),
            entryOn(10, 16, hour = 8),
            entryOn(10, 16, hour = 14),
            entryOn(10, 16, hour = 22),
        )
        assertReason(AdviceReason.LOGGED_YESTERDAY, advice(entries.shuffled(random)))
    }

    // ---- rule 3: typical gap ----------------------------------------------------------------------

    @Test
    fun fewerDaysThanTheTypicalGapIsUnfavorable() {
        // Gaps 4, 4 -> 4; latest the 13th.
        val entries = octoberDays(5, 9, 13)
        assertReason(AdviceReason.WITHIN_RHYTHM, advice(entries, nowMillis = millisOf(2026, 10, 14, 12, 0)))
        assertReason(AdviceReason.WITHIN_RHYTHM, advice(entries, nowMillis = millisOf(2026, 10, 16, 23, 59)))
    }

    @Test
    fun exactlyTheTypicalGapIsFavorable() {
        val entries = octoberDays(5, 9, 13)
        assertReason(AdviceReason.RHYTHM_REACHED, advice(entries, nowMillis = millisOf(2026, 10, 17, 0, 0)))
    }

    @Test
    fun moreThanTheTypicalGapIsFavorable() {
        assertReason(AdviceReason.RHYTHM_REACHED, advice(octoberDays(1, 3, 5)))
        // Months later, too.
        assertReason(AdviceReason.RHYTHM_REACHED, advice(octoberDays(1, 3, 5), nowMillis = millisOf(2027, 6, 1, 12, 0)))
    }

    @Test
    fun aRunOfDailyEntriesGivesAGapOfOneNeverZero() {
        assertEquals(1L, typicalGapDays(days(10, 11, 12)))
        // Daily up to the 12th, nothing since: favorable (not a streak, it does not reach yesterday).
        assertReason(AdviceReason.RHYTHM_REACHED, advice(octoberDays(10, 11, 12)))
    }

    @Test
    fun anOddNumberOfGapsTakesTheMiddleOne() {
        // Gaps 1, 5, 2 -> sorted 1, 2, 5 -> 2 (the mean would be 2.67 -> 3).
        assertEquals(2L, typicalGapDays(days(1, 2, 7, 9)))
        val entries = octoberDays(1, 2, 7, 9)
        assertReason(AdviceReason.WITHIN_RHYTHM, advice(entries, nowMillis = millisOf(2026, 10, 10, 12, 0)))
        assertReason(AdviceReason.RHYTHM_REACHED, advice(entries, nowMillis = millisOf(2026, 10, 11, 12, 0)))
        // Gaps 3, 1, 1, 6, 2 -> sorted 1, 1, 2, 3, 6 -> 2.
        assertEquals(2L, typicalGapDays(days(1, 4, 5, 6, 12, 14)))
    }

    @Test
    fun anEvenNumberOfGapsRoundsTheMiddlePairHalfUp() {
        // Gaps 1 and 2 -> 1.5 -> 2.
        assertEquals(2L, typicalGapDays(days(13, 14, 16)))
        // Gaps 7, 2, 4, 3 -> sorted 2, 3, 4, 7 -> 3.5 -> 4.
        assertEquals(4L, typicalGapDays(days(1, 8, 10, 14, 17)))
        // Gaps 1, 3, 2, 3 -> sorted 1, 2, 3, 3 -> 2.5 -> 3.
        assertEquals(3L, typicalGapDays(days(1, 2, 5, 7, 10)))
        // Gaps 2 and 2 -> exactly 2, nothing to round.
        assertEquals(2L, typicalGapDays(days(10, 12, 14)))

        // 1.5 -> 2 at work: latest the 14th, typical 2.
        val entries = octoberDays(11, 12, 14)
        assertReason(AdviceReason.WITHIN_RHYTHM, advice(entries, nowMillis = millisOf(2026, 10, 15, 12, 0)))
        assertReason(AdviceReason.RHYTHM_REACHED, advice(entries, nowMillis = millisOf(2026, 10, 16, 12, 0)))
    }

    @Test
    fun onlyTheLatestTenDaysCount() {
        // Ten recent days with gaps 1,1,1,1,1,3,3,3,3 (median 1), plus three older days that, if
        // read too, would add gaps 10, 20, 15 and lift the median to (3 + 3) / 2 = 3.
        val recent = listOf(1, 2, 3, 4, 5, 6, 9, 12, 15, 18).map { LocalDate.of(2026, 9, it) }
        val old = listOf(LocalDate.of(2026, 7, 18), LocalDate.of(2026, 7, 28), LocalDate.of(2026, 8, 17))
        assertEquals(1L, typicalGapDays(recent + old))
        assertEquals(1L, typicalGapDays((old + recent).shuffled(random)))

        // 2 days after the 18th: with 3 it would still be "within rhythm".
        val entries = (recent + old).map { testEntry(millisOf(it.year, it.monthValue, it.dayOfMonth)) }
        assertReason(
            AdviceReason.RHYTHM_REACHED,
            advice(entries.shuffled(random), nowMillis = millisOf(2026, 9, 20, 12, 0)),
        )
    }

    @Test
    fun severalEntriesOnOneDayCountAsOneDay() {
        val entries = listOf(
            entryOn(10, 6, hour = 7),
            entryOn(10, 6, hour = 12),
            entryOn(10, 6, hour = 21),
            entryOn(10, 8),
            entryOn(10, 10, hour = 9),
            entryOn(10, 10, hour = 20),
        )
        // Days 6, 8, 10: gaps 2 and 2, never the 0 between two entries of one day.
        assertEquals(2L, typicalGapDays(entries.map { it.timestamp.toLocalDate(TEST_ZONE) }))
        assertReason(AdviceReason.WITHIN_RHYTHM, advice(entries.shuffled(random), nowMillis = millisOf(2026, 10, 11, 9, 0)))
        assertReason(AdviceReason.RHYTHM_REACHED, advice(entries.shuffled(random), nowMillis = millisOf(2026, 10, 12, 9, 0)))
    }

    @Test
    fun gapsAcrossTheYearEndAreCalendarDays() {
        // 27 Dec, 30 Dec, 2 Jan: gaps 3 and 3 across New Year's Eve.
        val entries = listOf(entryOn(12, 27), entryOn(12, 30), entryOn(1, 2, year = 2027))
        assertReason(AdviceReason.WITHIN_RHYTHM, advice(entries, nowMillis = millisOf(2027, 1, 4, 12, 0)))
        assertReason(AdviceReason.RHYTHM_REACHED, advice(entries, nowMillis = millisOf(2027, 1, 5, 12, 0)))
    }

    // ---- rule 4: no typical gap yet -----------------------------------------------------------

    @Test
    fun fewerThanThreeDaysHaveNoTypicalGap() {
        assertNull(typicalGapDays(emptyList()))
        assertNull(typicalGapDays(days(10)))
        assertNull(typicalGapDays(days(3, 10)))
        // Duplicates are one day.
        assertNull(typicalGapDays(days(3, 3, 10, 10, 10)))
    }

    @Test
    fun oneDayOnlyYesterdayIsUnfavorable() {
        assertReason(AdviceReason.LOGGED_YESTERDAY, advice(listOf(entryOn(10, 16, hour = 23))))
    }

    @Test
    fun oneDayOnlyLongAgoIsNeutral() {
        assertReason(AdviceReason.NO_RHYTHM, advice(listOf(entryOn(10, 15))))
        assertReason(AdviceReason.NO_RHYTHM, advice(listOf(entryOn(3, 1))))
    }

    @Test
    fun twoDaysOnly() {
        // Latest yesterday, whatever came before.
        assertReason(AdviceReason.LOGGED_YESTERDAY, advice(octoberDays(2, 16)))
        // Latest before yesterday.
        assertReason(AdviceReason.NO_RHYTHM, advice(octoberDays(13, 14)))
        assertReason(AdviceReason.NO_RHYTHM, advice(octoberDays(1, 15)))
    }

    @Test
    fun manyEntriesOnTwoDaysAreStillTwoDays() {
        val entries = listOf(
            entryOn(10, 10, hour = 8),
            entryOn(10, 10, hour = 13),
            entryOn(10, 10, hour = 22),
            entryOn(10, 14, hour = 1),
            entryOn(10, 14, hour = 23, minute = 59),
        )
        assertReason(AdviceReason.NO_RHYTHM, advice(entries.shuffled(random)))
    }

    // ---- day boundaries -------------------------------------------------------------------------

    @Test
    fun midnightSplitsTodayFromYesterday() {
        val lateNight = listOf(entryOn(10, 16, hour = 23, minute = 59))

        val sameMinute = advice(lateNight, nowMillis = millisOf(2026, 10, 16, 23, 59))
        assertReason(AdviceReason.LOGGED_TODAY, sameMinute)
        assertEquals(LocalDate.of(2026, 10, 16), sameMinute.day)

        val atMidnight = advice(lateNight, nowMillis = millisOf(2026, 10, 17, 0, 0))
        assertReason(AdviceReason.LOGGED_YESTERDAY, atMidnight)
        assertEquals(today, atMidnight.day)

        // Midnight itself starts the new day: "after today" for a clock at 23:59, today until 23:59.
        val justAfter = listOf(entryOn(10, 17, hour = 0, minute = 0))
        assertReason(AdviceReason.LOGGED_TODAY, advice(justAfter, nowMillis = millisOf(2026, 10, 16, 23, 59)))
        assertReason(AdviceReason.LOGGED_TODAY, advice(justAfter, nowMillis = millisOf(2026, 10, 17, 23, 59)))
        assertReason(AdviceReason.LOGGED_YESTERDAY, advice(justAfter, nowMillis = millisOf(2026, 10, 18, 0, 0)))
    }

    @Test
    fun gapsAreCalendarDaysNotTwentyFourHourBlocks() {
        // 23:50 -> 00:10 is 20 minutes but one calendar day; 00:10 -> 23:50 two days later is two.
        val entries = listOf(
            entryOn(10, 10, hour = 23, minute = 50),
            entryOn(10, 11, hour = 0, minute = 10),
            entryOn(10, 13, hour = 23, minute = 50),
        )
        // Gaps 1 and 2 -> 1.5 -> 2. Twenty minutes past midnight is already a day since the 13th.
        assertReason(AdviceReason.WITHIN_RHYTHM, advice(entries, nowMillis = millisOf(2026, 10, 14, 0, 10)))
        assertReason(AdviceReason.RHYTHM_REACHED, advice(entries, nowMillis = millisOf(2026, 10, 15, 0, 10)))
    }

    @Test
    fun daylightSavingTimeChangesDoNotShiftDays() {
        // Europe/Berlin: summer time starts on 29 Mar 2026 (23-hour day) and ends on 25 Oct 2026
        // (25-hour day). 00:30 local is the previous day in UTC, so a UTC-based count would put
        // every entry a day early.
        val spring = listOf(28, 29, 30).map { entryOn(3, it, hour = 0, minute = 30, zone = berlin) }
        val springStreak = advice(spring.shuffled(random), nowMillis = millisOf(2026, 3, 31, 0, 15, berlin), zone = berlin)
        assertEquals(TodayAdvice(AdviceLevel.AVOID, AdviceReason.STREAK, LocalDate.of(2026, 3, 31), streakDays = 3), springStreak)

        // 28 Mar 23:30 -> 30 Mar 00:15 is only 23 h 45 min across the short day, yet two calendar days.
        val springGap = listOf(24, 26, 28).map { entryOn(3, it, hour = 23, minute = 30, zone = berlin) }
        assertReason(
            AdviceReason.WITHIN_RHYTHM,
            advice(springGap, nowMillis = millisOf(2026, 3, 29, 23, 45, berlin), zone = berlin),
        )
        val springReached = advice(springGap, nowMillis = millisOf(2026, 3, 30, 0, 15, berlin), zone = berlin)
        assertReason(AdviceReason.RHYTHM_REACHED, springReached)
        assertEquals(LocalDate.of(2026, 3, 30), springReached.day)

        // 25 Oct (25 hours) 23:30 -> 26 Oct 00:15: 45 minutes, one calendar day. Gaps 2, 1 -> 2.
        val autumn = listOf(entryOn(10, 22, zone = berlin)) +
            listOf(24, 25).map { entryOn(10, it, hour = 23, minute = 30, zone = berlin) }
        val autumnResult = advice(autumn.shuffled(random), nowMillis = millisOf(2026, 10, 26, 0, 15, berlin), zone = berlin)
        assertReason(AdviceReason.WITHIN_RHYTHM, autumnResult)
        assertEquals(LocalDate.of(2026, 10, 26), autumnResult.day)
        // Still on the long day itself: logged today.
        assertReason(
            AdviceReason.LOGGED_TODAY,
            advice(autumn, nowMillis = millisOf(2026, 10, 25, 23, 59, berlin), zone = berlin),
        )
    }

    @Test
    fun theZoneDecidesTheDay() {
        // 17 Oct 01:00 in Shanghai is still 16 Oct 19:00 in Berlin.
        val entries = listOf(testEntry(millisOf(2026, 10, 17, 1, 0)))
        val nowShanghai = millisOf(2026, 10, 17, 12, 0)
        assertReason(AdviceReason.LOGGED_TODAY, advice(entries, nowMillis = nowShanghai, zone = TEST_ZONE))
        val inBerlin = advice(entries, nowMillis = millisOf(2026, 10, 17, 9, 0, berlin), zone = berlin)
        assertReason(AdviceReason.LOGGED_YESTERDAY, inBerlin)
        assertEquals(today, inBerlin.day)
    }

    // ---- input order ----------------------------------------------------------------------------

    @Test
    fun inputOrderNeverMatters() {
        val scenarios = listOf(
            octoberDays(12, 13, 14, 15, 16, 17),
            octoberDays(10, 14, 20),
            octoberDays(3, 10, 11, 12, 13, 14, 15, 16),
            octoberDays(1, 2, 7, 9),
            octoberDays(1, 5, 14, 15, 16),
            octoberDays(2, 16),
            octoberDays(1, 3, 6, 7, 9, 12, 13),
        )
        for (entries in scenarios) {
            val sorted = entries.sortedBy { it.timestamp }
            val expected = advice(sorted)
            assertEquals(expected, advice(sorted.reversed()))
            repeat(5) { assertEquals(expected, advice(sorted.shuffled(random))) }
        }
    }

    // ---- TodayAdvice ----------------------------------------------------------------------------

    @Test
    fun everyReasonHasItsLevel() {
        val expected = mapOf(
            AdviceReason.LOGGED_TODAY to AdviceLevel.AVOID,
            AdviceReason.STREAK to AdviceLevel.AVOID,
            AdviceReason.WITHIN_RHYTHM to AdviceLevel.UNFAVORABLE,
            AdviceReason.RHYTHM_REACHED to AdviceLevel.FAVORABLE,
            AdviceReason.LOGGED_YESTERDAY to AdviceLevel.UNFAVORABLE,
            AdviceReason.NO_RHYTHM to AdviceLevel.NEUTRAL,
            AdviceReason.NO_ENTRIES to AdviceLevel.NEUTRAL,
        )
        assertEquals(expected, AdviceReason.entries.associateWith { it.level })
    }

    @Test
    fun inconsistentAdviceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            TodayAdvice(AdviceLevel.FAVORABLE, AdviceReason.LOGGED_TODAY, today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TodayAdvice(AdviceLevel.AVOID, AdviceReason.STREAK, today, streakDays = 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TodayAdvice(AdviceLevel.NEUTRAL, AdviceReason.NO_RHYTHM, today, streakDays = 4)
        }
    }

    // ---- pickDistinctByDay ----------------------------------------------------------------------

    @Test
    fun picksAreDistinctAndInRange() {
        for (size in 0..20) {
            for (count in 0..size + 2) {
                for (day in -40L..400L) {
                    val picks = pickDistinctByDay(day, size, count)
                    assertEquals("size $size count $count day $day", minOf(size, count), picks.size)
                    assertEquals("repeated pick: $picks", picks.size, picks.toSet().size)
                    assertTrue("out of range: $picks", picks.all { it in 0 until size })
                }
            }
        }
    }

    @Test
    fun picksAreStableAllDay() {
        val day = today.toEpochDay()
        val first = pickDistinctByDay(day, 12, 2)
        repeat(3) { assertEquals(first, pickDistinctByDay(day, 12, 2)) }
        // Starts at epochDay mod size, then a step of 7 (the first step >= 6 coprime to 12).
        assertEquals(listOf(0, 7), pickDistinctByDay(0, 12, 2))
        assertEquals(listOf(1, 8), pickDistinctByDay(1, 12, 2))
        assertEquals(listOf(5, 0), pickDistinctByDay(5, 12, 2))
        assertEquals(listOf(11, 6), pickDistinctByDay(-1, 12, 2))
        // 13 is prime: step 7 as well.
        assertEquals(listOf(12, 6), pickDistinctByDay(12, 13, 2))
    }

    @Test
    fun picksChangeEveryDay() {
        for (size in 2..20) {
            for (count in 1 until size) {
                for (day in -40L..400L) {
                    assertNotEquals(
                        "size $size count $count: day $day and the next pick the same items",
                        pickDistinctByDay(day, size, count).toSet(),
                        pickDistinctByDay(day + 1, size, count).toSet(),
                    )
                }
            }
        }
    }

    @Test
    fun everyItemComesUpEquallyOftenOverAFullCycle() {
        for (size in listOf(11, 12, 13)) {
            val start = today.toEpochDay()
            val counts = (start until start + size)
                .flatMap { pickDistinctByDay(it, size, 2) }
                .groupingBy { it }
                .eachCount()
            assertEquals((0 until size).associateWith { 2 }, counts)
        }
    }

    @Test
    fun aListShorterThanTheCountGivesEveryItemOnce() {
        assertEquals(emptyList<Int>(), pickDistinctByDay(5, 0, 2))
        assertEquals(emptyList<Int>(), pickDistinctByDay(5, 12, 0))
        assertEquals(listOf(0), pickDistinctByDay(5, 1, 2))
        assertEquals(setOf(0, 1), pickDistinctByDay(5, 2, 2).toSet())
        assertEquals(setOf(0, 1, 2), pickDistinctByDay(7, 3, 5).toSet())
    }

    @Test
    fun negativeSizesAndCountsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { pickDistinctByDay(0, -1, 2) }
        assertThrows(IllegalArgumentException::class.java) { pickDistinctByDay(0, 12, -1) }
    }
}
