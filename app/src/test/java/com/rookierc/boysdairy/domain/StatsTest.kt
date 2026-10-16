package com.rookierc.boysdairy.domain

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId

class StatsTest {

    // Friday 16 Oct 2026, 12:00 Asia/Shanghai.
    private val now = millisOf(2026, 10, 16, 12, 0)

    private fun stats(entries: List<Entry>, nowMillis: Long = now, zone: ZoneId = TEST_ZONE) =
        computeStats(entries, nowMillis, zone)

    private fun entryOn(month: Int, day: Int, hour: Int = 12, year: Int = 2026, minute: Int = 0) =
        testEntry(millisOf(year, month, day, hour, minute))

    // ---- empty / single -----------------------------------------------------------------------

    @Test
    fun emptyListYieldsZerosAndNulls() {
        val s = stats(emptyList())
        assertEquals(0, s.totalCount)
        assertEquals(0, s.soloCount)
        assertEquals(0, s.partnerCount)
        assertEquals(0, s.last7DaysCount)
        assertEquals(0, s.last30DaysCount)
        assertNull(s.daysSinceLast)
        assertEquals(0, s.consecutiveActiveDays)
        assertNull(s.longestGapDays)
        assertNull(s.averageIntervalDays)
        assertNull(s.averageDurationMinutes)
        assertNull(s.averagePleasure)
        assertTrue(s.moodBeforeCounts.isEmpty())
        assertTrue(s.moodAfterCounts.isEmpty())
        assertNull(s.busiestWeekday)
        assertNull(s.busiestHourBucket)
    }

    @Test
    fun singleEntryToday() {
        val s = stats(
            listOf(
                testEntry(
                    timestamp = millisOf(2026, 10, 16, 9, 30),
                    type = EntryType.PARTNER,
                    durationMinutes = 25,
                    moodBefore = Mood.EXCITED,
                    moodAfter = Mood.ENERGETIC,
                    pleasure = 8,
                ),
            ),
        )
        assertEquals(1, s.totalCount)
        assertEquals(0, s.soloCount)
        assertEquals(1, s.partnerCount)
        assertEquals(1, s.last7DaysCount)
        assertEquals(1, s.last30DaysCount)
        assertEquals(0L, s.daysSinceLast)
        assertEquals(1, s.consecutiveActiveDays)
        assertNull(s.longestGapDays)
        assertNull(s.averageIntervalDays)
        assertEquals(25.0, s.averageDurationMinutes!!, 1e-9)
        assertEquals(8.0, s.averagePleasure!!, 1e-9)
        assertEquals(mapOf(Mood.EXCITED to 1), s.moodBeforeCounts)
        assertEquals(mapOf(Mood.ENERGETIC to 1), s.moodAfterCounts)
        assertEquals(DayOfWeek.FRIDAY, s.busiestWeekday)
        assertEquals(HourBucket.MORNING, s.busiestHourBucket)
    }

    @Test
    fun singleOldEntry() {
        val s = stats(listOf(entryOn(month = 8, day = 1)))
        assertEquals(0, s.last7DaysCount)
        assertEquals(0, s.last30DaysCount)
        assertEquals(76L, s.daysSinceLast) // 1 Aug -> 16 Oct
        assertEquals(0, s.consecutiveActiveDays)
        assertNull(s.longestGapDays)
    }

    @Test
    fun totalsSplitBySoloAndPartner() {
        val s = stats(
            listOf(
                testEntry(now, EntryType.SOLO),
                testEntry(now - 1, EntryType.SOLO),
                testEntry(now - 2, EntryType.PARTNER),
            ),
        )
        assertEquals(3, s.totalCount)
        assertEquals(2, s.soloCount)
        assertEquals(1, s.partnerCount)
    }

    // ---- windows ------------------------------------------------------------------------------

    @Test
    fun sevenDayWindowCoversTodayAndSixPreviousDays() {
        val s = stats(
            listOf(
                entryOn(10, 16), // today: in
                entryOn(10, 10), // 6 days ago: in
                entryOn(10, 9), // 7 days ago: out
            ),
        )
        assertEquals(2, s.last7DaysCount)
        assertEquals(3, s.last30DaysCount)
    }

    @Test
    fun thirtyDayWindowCoversTodayAndTwentyNinePreviousDays() {
        val s = stats(
            listOf(
                entryOn(9, 17), // 29 days ago: in
                entryOn(9, 16), // 30 days ago: out
            ),
        )
        assertEquals(1, s.last30DaysCount)
    }

    @Test
    fun windowsUseCalendarDaysNotRollingHours() {
        // 6 days ago at 00:01 is still "in" even though it is 6 days + 12 hours before `now`.
        val s = stats(listOf(entryOn(10, 10, hour = 0, minute = 1)))
        assertEquals(1, s.last7DaysCount)
    }

    @Test
    fun futureEntriesAreNotCountedInWindowsAndDaysSinceIsNeverNegative() {
        val s = stats(listOf(entryOn(10, 20)))
        assertEquals(0, s.last7DaysCount)
        assertEquals(0, s.last30DaysCount)
        assertEquals(0L, s.daysSinceLast)
        assertEquals(0, s.consecutiveActiveDays)
    }

    // ---- daysSinceLast ------------------------------------------------------------------------

    @Test
    fun daysSinceLastIsACalendarDifferenceNotA24HourFloor() {
        val justAfterMidnight = millisOf(2026, 10, 16, 0, 1)
        val s = stats(listOf(entryOn(10, 15, hour = 23, minute = 59)), nowMillis = justAfterMidnight)
        assertEquals(1L, s.daysSinceLast)
    }

    @Test
    fun daysSinceLastUsesTheMostRecentEntryRegardlessOfListOrder() {
        val s = stats(listOf(entryOn(10, 1), entryOn(10, 14), entryOn(9, 20)))
        assertEquals(2L, s.daysSinceLast)
    }

    // ---- streak -------------------------------------------------------------------------------

    @Test
    fun streakEndingTodayCountsConsecutiveDays() {
        val s = stats(listOf(entryOn(10, 16), entryOn(10, 15), entryOn(10, 14)))
        assertEquals(3, s.consecutiveActiveDays)
    }

    @Test
    fun streakEndingYesterdayStillCounts() {
        val s = stats(listOf(entryOn(10, 15), entryOn(10, 14)))
        assertEquals(2, s.consecutiveActiveDays)
    }

    @Test
    fun streakEndingTwoDaysAgoIsZero() {
        val s = stats(listOf(entryOn(10, 14), entryOn(10, 13), entryOn(10, 12)))
        assertEquals(0, s.consecutiveActiveDays)
    }

    @Test
    fun streakStopsAtTheFirstMissingDay() {
        val s = stats(listOf(entryOn(10, 16), entryOn(10, 15), entryOn(10, 13), entryOn(10, 12)))
        assertEquals(2, s.consecutiveActiveDays)
    }

    @Test
    fun severalEntriesOnTheSameDayCountOnceForTheStreak() {
        val s = stats(
            listOf(
                entryOn(10, 16, hour = 8),
                entryOn(10, 16, hour = 22),
                entryOn(10, 15, hour = 1),
                entryOn(10, 15, hour = 2),
            ),
        )
        assertEquals(2, s.consecutiveActiveDays)
    }

    @Test
    fun streakCrossesMonthAndYearBoundaries() {
        val newYear = millisOf(2027, 1, 2, 10, 0)
        val s = stats(
            listOf(
                entryOn(12, 30, year = 2026),
                entryOn(12, 31, year = 2026),
                entryOn(1, 1, year = 2027),
                entryOn(1, 2, year = 2027),
            ),
            nowMillis = newYear,
        )
        assertEquals(4, s.consecutiveActiveDays)
    }

    @Test
    fun streakIgnoresFutureEntries() {
        val s = stats(listOf(entryOn(10, 17), entryOn(10, 16), entryOn(10, 15)))
        assertEquals(2, s.consecutiveActiveDays)
    }

    // ---- gaps ---------------------------------------------------------------------------------

    @Test
    fun longestGapNeedsTwoDistinctDays() {
        assertNull(stats(listOf(entryOn(10, 16, hour = 8), entryOn(10, 16, hour = 20))).longestGapDays)
    }

    @Test
    fun adjacentDaysHaveAGapOfOne() {
        assertEquals(1L, stats(listOf(entryOn(10, 16), entryOn(10, 15))).longestGapDays)
    }

    @Test
    fun longestGapAcrossAMonthBoundary() {
        // 28 Sep -> 3 Oct is 5 days; 3 Oct -> 4 Oct is 1.
        val s = stats(listOf(entryOn(9, 28), entryOn(10, 3), entryOn(10, 4)))
        assertEquals(5L, s.longestGapDays)
    }

    @Test
    fun longestGapSpanningAShortFebruary() {
        // 31 Jan -> 1 Mar 2026 (28-day February) is 29 days.
        val s = stats(listOf(entryOn(1, 31), entryOn(3, 1), entryOn(3, 2)))
        assertEquals(29L, s.longestGapDays)
    }

    @Test
    fun longestGapAcrossLeapDay() {
        val leapNow = millisOf(2028, 3, 5, 12, 0)
        val s = stats(listOf(entryOn(2, 28, year = 2028), entryOn(3, 1, year = 2028)), nowMillis = leapNow)
        assertEquals(2L, s.longestGapDays) // 28 Feb -> 29 Feb -> 1 Mar
    }

    @Test
    fun longestGapPicksTheMaximumNotTheLatest() {
        val s = stats(listOf(entryOn(10, 1), entryOn(10, 11), entryOn(10, 13), entryOn(10, 14)))
        assertEquals(10L, s.longestGapDays)
    }

    @Test
    fun gapIsMeasuredBetweenCalendarDaysNotHours() {
        // 23:59 -> 00:01 is two minutes but consecutive calendar days.
        val s = stats(listOf(entryOn(10, 14, hour = 23, minute = 59), entryOn(10, 15, hour = 0, minute = 1)))
        assertEquals(1L, s.longestGapDays)
    }

    // ---- average interval ---------------------------------------------------------------------

    @Test
    fun averageIntervalNeedsTwoEntries() {
        assertNull(stats(listOf(entryOn(10, 16))).averageIntervalDays)
    }

    @Test
    fun averageIntervalIsTheMeanElapsedDays() {
        // Gaps of 1 day and 3 days.
        val s = stats(listOf(entryOn(10, 16), entryOn(10, 15), entryOn(10, 12)))
        assertEquals(2.0, s.averageIntervalDays!!, 1e-9)
    }

    @Test
    fun averageIntervalKeepsFractionsOfADay() {
        val s = stats(listOf(entryOn(10, 16, hour = 12), entryOn(10, 16, hour = 0)))
        assertEquals(0.5, s.averageIntervalDays!!, 1e-9)
    }

    @Test
    fun averageIntervalOnlyLooksAtTheMostRecentElevenEntries() {
        // 11 daily entries (10 gaps of 1 day) ...
        val recent = (0 until 11).map { entryOn(10, 16 - it) }
        // ... plus older ones with huge gaps that must be ignored.
        val ancient = listOf(entryOn(month = 1, day = 1), entryOn(month = 2, day = 1), entryOn(month = 3, day = 1))
        val s = stats((ancient + recent).shuffled(java.util.Random(7)))
        assertEquals(1.0, s.averageIntervalDays!!, 1e-9)
    }

    @Test
    fun averageIntervalUsesTheElevenMostRecentEvenWhenTheListIsUnsorted() {
        // Newest 11 are spaced 2 days apart; the 12th is far away and must be excluded.
        val spaced = (0 until 11).map { entryOn(10, 16).copy(timestamp = millisOf(2026, 10, 16) - it * 2 * 86_400_000L) }
        val old = entryOn(month = 1, day = 1)
        val s = stats(listOf(old) + spaced.reversed())
        assertEquals(2.0, s.averageIntervalDays!!, 1e-9)
    }

    // ---- averages and moods -------------------------------------------------------------------

    @Test
    fun averagesOverAllEntries() {
        val s = stats(
            listOf(
                testEntry(now, durationMinutes = 10, pleasure = 4),
                testEntry(now - 1, durationMinutes = 20, pleasure = 9),
                testEntry(now - 2, durationMinutes = 30, pleasure = 8),
            ),
        )
        assertEquals(20.0, s.averageDurationMinutes!!, 1e-9)
        assertEquals(7.0, s.averagePleasure!!, 1e-9)
    }

    @Test
    fun moodCountsOnlyContainMoodsThatOccurInDeclarationOrder() {
        val s = stats(
            listOf(
                testEntry(now, moodBefore = Mood.STRESSED, moodAfter = Mood.RELAXED),
                testEntry(now - 1, moodBefore = Mood.ANXIOUS, moodAfter = Mood.RELAXED),
                testEntry(now - 2, moodBefore = Mood.STRESSED, moodAfter = Mood.GUILTY),
            ),
        )
        assertEquals(mapOf(Mood.STRESSED to 2, Mood.ANXIOUS to 1), s.moodBeforeCounts)
        assertEquals(listOf(Mood.ANXIOUS, Mood.STRESSED), s.moodBeforeCounts.keys.toList())
        assertEquals(mapOf(Mood.RELAXED to 2, Mood.GUILTY to 1), s.moodAfterCounts)
        assertEquals(listOf(Mood.RELAXED, Mood.GUILTY), s.moodAfterCounts.keys.toList())
    }

    // ---- busiest weekday / hour bucket --------------------------------------------------------

    @Test
    fun busiestWeekdayPicksTheMostFrequent() {
        // 2026-10-12 Mon, 10-14 Wed, 10-21 Wed.
        val s = stats(listOf(entryOn(10, 12), entryOn(10, 14), entryOn(10, 21)))
        assertEquals(DayOfWeek.WEDNESDAY, s.busiestWeekday)
    }

    @Test
    fun busiestWeekdayTieResolvesToTheEarliestDayOfTheWeek() {
        // One Sunday (10-18), one Tuesday (10-13), one Friday (10-16).
        val s = stats(listOf(entryOn(10, 18), entryOn(10, 13), entryOn(10, 16)))
        assertEquals(DayOfWeek.TUESDAY, s.busiestWeekday)
    }

    @Test
    fun busiestHourBucketPicksTheMostFrequent() {
        val s = stats(listOf(entryOn(10, 1, hour = 22), entryOn(10, 2, hour = 23), entryOn(10, 3, hour = 7)))
        assertEquals(HourBucket.EVENING, s.busiestHourBucket)
    }

    @Test
    fun busiestHourBucketTieResolvesToTheEarliestBucket() {
        val s = stats(listOf(entryOn(10, 1, hour = 21), entryOn(10, 2, hour = 14), entryOn(10, 3, hour = 3)))
        assertEquals(HourBucket.NIGHT, s.busiestHourBucket)
    }

    @Test
    fun hourBucketBoundaries() {
        assertEquals(HourBucket.NIGHT, HourBucket.forHour(0))
        assertEquals(HourBucket.NIGHT, HourBucket.forHour(5))
        assertEquals(HourBucket.MORNING, HourBucket.forHour(6))
        assertEquals(HourBucket.MORNING, HourBucket.forHour(11))
        assertEquals(HourBucket.AFTERNOON, HourBucket.forHour(12))
        assertEquals(HourBucket.AFTERNOON, HourBucket.forHour(17))
        assertEquals(HourBucket.EVENING, HourBucket.forHour(18))
        assertEquals(HourBucket.EVENING, HourBucket.forHour(23))
    }

    @Test
    fun bucketIsDeterminedByLocalTimeOfDay() {
        assertEquals(HourBucket.NIGHT, stats(listOf(entryOn(10, 1, hour = 5, minute = 59))).busiestHourBucket)
        assertEquals(HourBucket.MORNING, stats(listOf(entryOn(10, 1, hour = 6, minute = 0))).busiestHourBucket)
    }

    // ---- time zones ---------------------------------------------------------------------------

    @Test
    fun theZoneDecidesWhichCalendarDayAnEntryBelongsTo() {
        // 20:00 UTC on 15 Oct is 04:00 on 16 Oct in Shanghai but still 15 Oct in UTC.
        val utc = ZoneId.of("UTC")
        val entry = testEntry(millisOf(2026, 10, 15, 20, 0, zone = utc))
        val nowUtc = millisOf(2026, 10, 15, 22, 0, zone = utc)

        val inShanghai = stats(listOf(entry), nowMillis = nowUtc, zone = TEST_ZONE)
        assertEquals(HourBucket.NIGHT, inShanghai.busiestHourBucket)
        assertEquals(DayOfWeek.FRIDAY, inShanghai.busiestWeekday)
        assertEquals(0L, inShanghai.daysSinceLast)

        val inUtc = stats(listOf(entry), nowMillis = nowUtc, zone = utc)
        assertEquals(HourBucket.EVENING, inUtc.busiestHourBucket)
        assertEquals(DayOfWeek.THURSDAY, inUtc.busiestWeekday)
    }
}
