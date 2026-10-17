package com.rookierc.boysdairy.domain

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class InsightsTest {

    // Friday 16 Oct 2026, 12:00 Asia/Shanghai. Its week starts on Monday 12 Oct.
    private val now = millisOf(2026, 10, 16, 12, 0)
    private val utc: ZoneId = ZoneId.of("UTC")

    private fun entryOn(
        month: Int,
        day: Int,
        hour: Int = 12,
        year: Int = 2026,
        minute: Int = 0,
        type: EntryType = EntryType.SOLO,
        pleasure: Int = 5,
    ) = testEntry(millisOf(year, month, day, hour, minute), type = type, pleasure = pleasure)

    // ---- weeklyCounts -------------------------------------------------------------------------

    @Test
    fun weeklyCountsOnAnEmptyDiaryStillHasEveryWeek() {
        val weeks = weeklyCounts(emptyList(), now, TEST_ZONE)
        assertEquals(12, weeks.size)
        assertEquals(LocalDate.of(2026, 7, 27), weeks.first().weekStart)
        assertEquals(LocalDate.of(2026, 10, 12), weeks.last().weekStart)
        weeks.zipWithNext { a, b -> assertEquals(a.weekStart.plusWeeks(1), b.weekStart) }
        assertTrue(weeks.all { it.weekStart.dayOfWeek == DayOfWeek.MONDAY })
        assertTrue(weeks.all { it.solo == 0 && it.partner == 0 && it.total == 0 && it.averagePleasure == null })
    }

    @Test
    fun weeklyCountsHonoursTheWeeksParameter() {
        val one = weeklyCounts(emptyList(), now, TEST_ZONE, weeks = 1)
        assertEquals(listOf(LocalDate.of(2026, 10, 12)), one.map { it.weekStart })
        assertEquals(4, weeklyCounts(emptyList(), now, TEST_ZONE, weeks = 4).size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun weeklyCountsRejectsZeroWeeks() {
        weeklyCounts(emptyList(), now, TEST_ZONE, weeks = 0)
    }

    @Test
    fun weeklyCountsSplitsByTypeAndAveragesPleasure() {
        val weeks = weeklyCounts(
            listOf(
                entryOn(10, 12, pleasure = 4),
                entryOn(10, 14, pleasure = 6),
                entryOn(10, 16, type = EntryType.PARTNER, pleasure = 8),
                entryOn(10, 6, pleasure = 9), // previous week
            ),
            now,
            TEST_ZONE,
        )
        val current = weeks.last()
        assertEquals(2, current.solo)
        assertEquals(1, current.partner)
        assertEquals(3, current.total)
        assertEquals(6.0, current.averagePleasure!!, 1e-9)

        val previous = weeks[weeks.size - 2]
        assertEquals(LocalDate.of(2026, 10, 5), previous.weekStart)
        assertEquals(1, previous.solo)
        assertEquals(9.0, previous.averagePleasure!!, 1e-9)
        assertNull(weeks.first().averagePleasure)
    }

    @Test
    fun weeksStartOnMondayAtMidnight() {
        val mondayMorning = millisOf(2026, 10, 12, 0, 30)
        val weeks = weeklyCounts(
            listOf(
                entryOn(10, 11, hour = 23, minute = 59), // Sunday: previous week
                entryOn(10, 12, hour = 0, minute = 0), // Monday: this week
            ),
            mondayMorning,
            TEST_ZONE,
        )
        assertEquals(LocalDate.of(2026, 10, 12), weeks.last().weekStart)
        assertEquals(1, weeks.last().total)
        assertEquals(1, weeks[weeks.size - 2].total)
    }

    @Test
    fun sundayNightStillBelongsToTheWeekThatStartedOnMonday() {
        val sundayNight = millisOf(2026, 10, 18, 23, 59)
        val weeks = weeklyCounts(listOf(entryOn(10, 18, hour = 23, minute = 0)), sundayNight, TEST_ZONE)
        assertEquals(LocalDate.of(2026, 10, 12), weeks.last().weekStart)
        assertEquals(1, weeks.last().total)
    }

    @Test
    fun weeklyCountsIgnoresEntriesOutsideTheWindow() {
        val weeks = weeklyCounts(
            listOf(
                entryOn(7, 26, hour = 23, minute = 59), // the Sunday before the first week
                entryOn(7, 27, hour = 0, minute = 0), // first minute of the first week
                entryOn(10, 17), // tomorrow
            ),
            now,
            TEST_ZONE,
        )
        assertEquals(1, weeks.first().total)
        assertEquals(1, weeks.sumOf { it.total })
    }

    @Test
    fun weeklyCountsCrossesTheYearBoundary() {
        // Saturday 2 Jan 2027; its week started on Monday 28 Dec 2026.
        val newYear = millisOf(2027, 1, 2, 10, 0)
        val weeks = weeklyCounts(
            listOf(
                entryOn(12, 27, year = 2026), // Sunday: previous week
                entryOn(12, 28, year = 2026),
                entryOn(12, 31, year = 2026),
                entryOn(1, 1, year = 2027),
            ),
            newYear,
            TEST_ZONE,
        )
        assertEquals(LocalDate.of(2026, 12, 28), weeks.last().weekStart)
        assertEquals(3, weeks.last().total)
        assertEquals(LocalDate.of(2026, 12, 21), weeks[weeks.size - 2].weekStart)
        assertEquals(1, weeks[weeks.size - 2].total)
    }

    @Test
    fun theZoneDecidesTheWeekOfAnEntry() {
        // 20:00 UTC on Sunday 11 Oct is 04:00 on Monday 12 Oct in Shanghai.
        val entry = testEntry(millisOf(2026, 10, 11, 20, 0, zone = utc))
        val inShanghai = weeklyCounts(listOf(entry), now, TEST_ZONE)
        val inUtc = weeklyCounts(listOf(entry), now, utc)
        assertEquals(1, inShanghai.last().total)
        assertEquals(0, inUtc.last().total)
        assertEquals(1, inUtc[inUtc.size - 2].total)
    }

    // ---- weekdayCounts / hourBucketCounts / busiestKeys ---------------------------------------

    @Test
    fun weekdayCountsListsEveryDayMondayFirst() {
        val counts = weekdayCounts(emptyList(), TEST_ZONE)
        assertEquals(DayOfWeek.entries.toList(), counts.keys.toList())
        assertTrue(counts.values.all { it == 0 })
    }

    @Test
    fun weekdayCountsCountsLocalDays() {
        // 12 Oct 2026 is a Monday, 14 a Wednesday, 18 a Sunday.
        val counts = weekdayCounts(
            listOf(entryOn(10, 12), entryOn(10, 12, hour = 22), entryOn(10, 14), entryOn(10, 18), entryOn(10, 5)),
            TEST_ZONE,
        )
        assertEquals(3, counts[DayOfWeek.MONDAY])
        assertEquals(1, counts[DayOfWeek.WEDNESDAY])
        assertEquals(1, counts[DayOfWeek.SUNDAY])
        assertEquals(0, counts[DayOfWeek.FRIDAY])
        assertEquals(5, counts.values.sum())
    }

    @Test
    fun weekdayCountsDependOnTheZone() {
        val entry = testEntry(millisOf(2026, 10, 11, 20, 0, zone = utc))
        assertEquals(1, weekdayCounts(listOf(entry), TEST_ZONE)[DayOfWeek.MONDAY])
        assertEquals(1, weekdayCounts(listOf(entry), utc)[DayOfWeek.SUNDAY])
    }

    @Test
    fun hourBucketCountsListsEveryBucketNightFirst() {
        val counts = hourBucketCounts(emptyList(), TEST_ZONE)
        assertEquals(HourBucket.entries.toList(), counts.keys.toList())
        assertTrue(counts.values.all { it == 0 })
    }

    @Test
    fun hourBucketCountsUsesTheBucketBoundaries() {
        val counts = hourBucketCounts(
            listOf(
                entryOn(10, 1, hour = 0, minute = 0),
                entryOn(10, 1, hour = 5, minute = 59),
                entryOn(10, 1, hour = 6, minute = 0),
                entryOn(10, 1, hour = 11, minute = 59),
                entryOn(10, 1, hour = 12, minute = 0),
                entryOn(10, 1, hour = 17, minute = 59),
                entryOn(10, 1, hour = 18, minute = 0),
                entryOn(10, 1, hour = 23, minute = 59),
                entryOn(10, 2, hour = 23, minute = 0),
            ),
            TEST_ZONE,
        )
        assertEquals(2, counts[HourBucket.NIGHT])
        assertEquals(2, counts[HourBucket.MORNING])
        assertEquals(2, counts[HourBucket.AFTERNOON])
        assertEquals(3, counts[HourBucket.EVENING])
    }

    @Test
    fun hourBucketCountsDependOnTheZone() {
        val entry = testEntry(millisOf(2026, 10, 11, 20, 0, zone = utc)) // 04:00 in Shanghai
        assertEquals(1, hourBucketCounts(listOf(entry), TEST_ZONE)[HourBucket.NIGHT])
        assertEquals(1, hourBucketCounts(listOf(entry), utc)[HourBucket.EVENING])
    }

    @Test
    fun busiestKeysReturnsEveryTiedMaximum() {
        val counts = weekdayCounts(listOf(entryOn(10, 13), entryOn(10, 16), entryOn(10, 9), entryOn(10, 14)), TEST_ZONE)
        // Tuesday 1, Wednesday 1, Friday 2 (9 and 16 Oct).
        assertEquals(setOf(DayOfWeek.FRIDAY), busiestKeys(counts))

        val tied = hourBucketCounts(listOf(entryOn(10, 1, hour = 3), entryOn(10, 2, hour = 20)), TEST_ZONE)
        assertEquals(setOf(HourBucket.NIGHT, HourBucket.EVENING), busiestKeys(tied))
    }

    @Test
    fun busiestKeysIsEmptyWhenNothingWasCounted() {
        assertTrue(busiestKeys(weekdayCounts(emptyList(), TEST_ZONE)).isEmpty())
        assertTrue(busiestKeys(emptyMap<HourBucket, Int>()).isEmpty())
    }

    // ---- longestStreakDays --------------------------------------------------------------------

    @Test
    fun longestStreakOfNothingIsZero() {
        assertEquals(0, longestStreakDays(emptyList(), TEST_ZONE))
    }

    @Test
    fun severalEntriesOnOneDayAreAStreakOfOne() {
        assertEquals(1, longestStreakDays(listOf(entryOn(10, 1, hour = 8), entryOn(10, 1, hour = 22)), TEST_ZONE))
    }

    @Test
    fun longestStreakPicksTheLongestRunNotTheLatest() {
        val entries = listOf(
            entryOn(10, 11), entryOn(10, 12), // run of 2 (latest)
            entryOn(10, 1), entryOn(10, 2), entryOn(10, 3), entryOn(10, 4), // run of 4
            entryOn(9, 20),
        ).shuffled(java.util.Random(3))
        assertEquals(4, longestStreakDays(entries, TEST_ZONE))
    }

    @Test
    fun tiedRunsReportTheirCommonLength() {
        val entries = listOf(entryOn(10, 1), entryOn(10, 2), entryOn(10, 3), entryOn(10, 7), entryOn(10, 8), entryOn(10, 9))
        assertEquals(3, longestStreakDays(entries, TEST_ZONE))
    }

    @Test
    fun longestStreakCrossesYearAndLeapDay() {
        val newYear = listOf(
            entryOn(12, 30, year = 2026),
            entryOn(12, 31, year = 2026),
            entryOn(1, 1, year = 2027),
            entryOn(1, 2, year = 2027),
        )
        assertEquals(4, longestStreakDays(newYear, TEST_ZONE))

        val leap = listOf(entryOn(2, 28, year = 2028), entryOn(2, 29, year = 2028), entryOn(3, 1, year = 2028))
        assertEquals(3, longestStreakDays(leap, TEST_ZONE))
    }

    @Test
    fun longestStreakUsesCalendarDaysOfTheZone() {
        // 20:00 UTC on 14 Oct is 04:00 on 15 Oct in Shanghai; 01:00 UTC on 16 Oct is 09:00 on 16 Oct.
        val entries = listOf(
            testEntry(millisOf(2026, 10, 14, 20, 0, zone = utc)),
            testEntry(millisOf(2026, 10, 16, 1, 0, zone = utc)),
        )
        assertEquals(2, longestStreakDays(entries, TEST_ZONE))
        assertEquals(1, longestStreakDays(entries, utc))
    }

    // ---- entriesInPeriod ----------------------------------------------------------------------

    private fun inPeriod(entries: List<Entry>, period: InsightsPeriod, nowMillis: Long = now, zone: ZoneId = TEST_ZONE) =
        entriesInPeriod(entries, period, nowMillis, zone)

    @Test
    fun everyPeriodOfAnEmptyDiaryIsEmpty() {
        InsightsPeriod.entries.forEach { period ->
            assertEquals("$period", emptyList<Entry>(), inPeriod(emptyList(), period))
        }
    }

    @Test
    fun periodStartDates() {
        val today = LocalDate.of(2026, 10, 16)
        assertEquals(LocalDate.of(2026, 9, 17), InsightsPeriod.LAST_30_DAYS.startDate(today))
        assertEquals(LocalDate.of(2026, 7, 19), InsightsPeriod.LAST_90_DAYS.startDate(today))
        assertEquals(LocalDate.of(2026, 1, 1), InsightsPeriod.THIS_YEAR.startDate(today))
        assertNull(InsightsPeriod.ALL.startDate(today))
    }

    @Test
    fun last30DaysStartsOnItsFirstDayAndEndsToday() {
        // Today is 16 Oct, so the window is 17 Sep - 16 Oct.
        val firstDay = entryOn(9, 17, hour = 0, minute = 0)
        val dayBefore = entryOn(9, 16, hour = 23, minute = 59)
        val lateToday = entryOn(10, 16, hour = 23, minute = 59) // later than "now" but still today
        val tomorrow = entryOn(10, 17, hour = 0, minute = 0)
        val result = inPeriod(listOf(tomorrow, lateToday, firstDay, dayBefore), InsightsPeriod.LAST_30_DAYS)
        assertEquals(listOf(lateToday, firstDay), result)
    }

    @Test
    fun last90DaysStartsOnItsFirstDay() {
        // 90 days ending 16 Oct start on 19 Jul.
        val firstDay = entryOn(7, 19, hour = 0, minute = 0)
        val dayBefore = entryOn(7, 18, hour = 23, minute = 59)
        val inside = entryOn(8, 30)
        assertEquals(listOf(firstDay, inside), inPeriod(listOf(firstDay, dayBefore, inside), InsightsPeriod.LAST_90_DAYS))
    }

    @Test
    fun last30DaysMatchesTheStatsWindow() {
        val entries = (0 until 200).map { testEntry(now - it * 7L * 3_600_000L) } // every 7 hours, ~58 days back
        val stats = computeStats(entries, now, TEST_ZONE)
        assertEquals(stats.last30DaysCount, inPeriod(entries, InsightsPeriod.LAST_30_DAYS).size)
    }

    @Test
    fun lastNDaysCrossTheYearBoundary() {
        // 10 Jan 2027: 30 days start on 12 Dec 2026, 90 days on 13 Oct 2026.
        val nowJan = millisOf(2027, 1, 10, 9, 0)
        val dec12 = entryOn(12, 12, hour = 0, minute = 0)
        val dec11 = entryOn(12, 11, hour = 23, minute = 59)
        val jan1 = entryOn(1, 1, year = 2027)
        val oct13 = entryOn(10, 13, hour = 0, minute = 0)
        val oct12 = entryOn(10, 12, hour = 23, minute = 59)
        val all = listOf(jan1, dec12, dec11, oct13, oct12)
        assertEquals(listOf(jan1, dec12), inPeriod(all, InsightsPeriod.LAST_30_DAYS, nowJan))
        assertEquals(listOf(jan1, dec12, dec11, oct13), inPeriod(all, InsightsPeriod.LAST_90_DAYS, nowJan))
    }

    @Test
    fun last30DaysAcrossALeapFebruary() {
        // 1 Mar 2028 (leap year): the window starts on 1 Feb. In 2027 it starts on 31 Jan.
        val leap = listOf(entryOn(2, 1, year = 2028, hour = 0, minute = 0), entryOn(1, 31, year = 2028, hour = 23, minute = 59))
        assertEquals(listOf(leap[0]), inPeriod(leap, InsightsPeriod.LAST_30_DAYS, millisOf(2028, 3, 1)))

        val common = listOf(entryOn(1, 31, year = 2027, hour = 0, minute = 0), entryOn(1, 30, year = 2027, hour = 23, minute = 59))
        assertEquals(listOf(common[0]), inPeriod(common, InsightsPeriod.LAST_30_DAYS, millisOf(2027, 3, 1)))
    }

    @Test
    fun thisYearStartsOnTheFirstOfJanuary() {
        val newYear = entryOn(1, 1, hour = 0, minute = 0)
        val newYearsEve = entryOn(12, 31, year = 2025, hour = 23, minute = 59)
        val summer = entryOn(7, 1)
        val tomorrow = entryOn(10, 17)
        val result = inPeriod(listOf(tomorrow, summer, newYear, newYearsEve), InsightsPeriod.THIS_YEAR)
        assertEquals(listOf(summer, newYear), result)
    }

    @Test
    fun thisYearInJanuaryOnlyHasTheNewYear() {
        val nowJan = millisOf(2027, 1, 5, 8, 0)
        val dec31 = entryOn(12, 31, hour = 23, minute = 59)
        val jan1 = entryOn(1, 1, year = 2027, hour = 0, minute = 0)
        val jan5 = entryOn(1, 5, year = 2027, hour = 22, minute = 0)
        assertEquals(listOf(jan5, jan1), inPeriod(listOf(jan5, jan1, dec31), InsightsPeriod.THIS_YEAR, nowJan))

        // On 1 January itself only that day counts.
        val newYearsMorning = millisOf(2027, 1, 1, 0, 1)
        assertEquals(listOf(jan1), inPeriod(listOf(jan5, jan1, dec31), InsightsPeriod.THIS_YEAR, newYearsMorning))
    }

    @Test
    fun thisYearFollowsTheZone() {
        // 20:00 UTC on 31 Dec 2026 is 04:00 on 1 Jan 2027 in Shanghai.
        val entry = testEntry(millisOf(2026, 12, 31, 20, 0, zone = utc))
        val nowJan = millisOf(2027, 1, 5, 12, 0, zone = utc)
        assertEquals(listOf(entry), inPeriod(listOf(entry), InsightsPeriod.THIS_YEAR, nowJan, TEST_ZONE))
        assertEquals(emptyList<Entry>(), inPeriod(listOf(entry), InsightsPeriod.THIS_YEAR, nowJan, utc))
    }

    @Test
    fun allKeepsEverythingIncludingFutureEntries() {
        val entries = listOf(entryOn(12, 1), entryOn(10, 16), entryOn(1, 1, year = 2019))
        assertEquals(entries, inPeriod(entries, InsightsPeriod.ALL))
    }

    // ---- monthOverMonth -----------------------------------------------------------------------

    @Test
    fun monthOverMonthOfAnEmptyDiary() {
        val comparison = monthOverMonth(emptyList(), now, TEST_ZONE)
        assertEquals(MonthComparison(0, 0, LocalDate.of(2026, 9, 16)), comparison)
        assertEquals(0, comparison.difference)
    }

    @Test
    fun monthOverMonthComparesTheSameStretch() {
        // Today is 16 Oct: 1-16 Oct against 1-16 Sep.
        val entries = listOf(
            entryOn(10, 17), // future: ignored
            entryOn(10, 16, hour = 23, minute = 59),
            entryOn(10, 1, hour = 0, minute = 0),
            entryOn(10, 8),
            entryOn(9, 30), // after the same day last month: ignored
            entryOn(9, 17, hour = 0, minute = 0), // ignored
            entryOn(9, 16, hour = 23, minute = 59),
            entryOn(9, 1, hour = 0, minute = 0),
            entryOn(8, 31, hour = 23, minute = 59), // ignored
        )
        val comparison = monthOverMonth(entries, now, TEST_ZONE)
        assertEquals(3, comparison.thisMonth)
        assertEquals(2, comparison.lastMonthSamePeriod)
        assertEquals(1, comparison.difference)
        assertEquals(LocalDate.of(2026, 9, 1), comparison.lastMonthStart)
        assertEquals(LocalDate.of(2026, 9, 16), comparison.lastMonthEnd)
    }

    @Test
    fun monthOverMonthCanBeBehindOrEven() {
        val behind = monthOverMonth(listOf(entryOn(10, 2), entryOn(9, 2), entryOn(9, 3), entryOn(9, 4)), now, TEST_ZONE)
        assertEquals(-2, behind.difference)
        val even = monthOverMonth(listOf(entryOn(10, 2), entryOn(9, 2)), now, TEST_ZONE)
        assertEquals(0, even.difference)
        assertEquals(1, even.thisMonth)
    }

    @Test
    fun monthOverMonthOnTheFirstOfTheMonth() {
        val nov1 = millisOf(2026, 11, 1, 9, 0)
        val comparison = monthOverMonth(listOf(entryOn(11, 1), entryOn(10, 1), entryOn(10, 2)), nov1, TEST_ZONE)
        assertEquals(MonthComparison(1, 1, LocalDate.of(2026, 10, 1)), comparison)
    }

    @Test
    fun monthOverMonthInJanuaryLooksAtLastDecember() {
        val jan15 = millisOf(2027, 1, 15, 9, 0)
        val entries = listOf(
            entryOn(1, 3, year = 2027),
            entryOn(12, 15, hour = 23, minute = 59),
            entryOn(12, 16), // ignored
            entryOn(1, 3), // January of the year before: ignored
        )
        assertEquals(MonthComparison(1, 1, LocalDate.of(2026, 12, 15)), monthOverMonth(entries, jan15, TEST_ZONE))
    }

    @Test
    fun march31stComparesWithTheWholeOfFebruary() {
        val entries = listOf(
            entryOn(3, 31, year = 2027),
            entryOn(3, 1, year = 2027, hour = 0, minute = 0),
            entryOn(2, 28, year = 2027, hour = 23, minute = 59),
            entryOn(2, 1, year = 2027, hour = 0, minute = 0),
            entryOn(1, 31, year = 2027), // ignored
        )
        val comparison = monthOverMonth(entries, millisOf(2027, 3, 31), TEST_ZONE)
        assertEquals(MonthComparison(2, 2, LocalDate.of(2027, 2, 28)), comparison)
        // 30 March maps to the last day of February as well.
        assertEquals(LocalDate.of(2027, 2, 28), monthOverMonth(entries, millisOf(2027, 3, 30), TEST_ZONE).lastMonthEnd)
    }

    @Test
    fun monthOverMonthInALeapYearIncludesThe29th() {
        val feb29 = entryOn(2, 29, year = 2028)
        val feb28 = entryOn(2, 28, year = 2028)
        val entries = listOf(feb29, feb28)

        val mar31 = monthOverMonth(entries, millisOf(2028, 3, 31), TEST_ZONE)
        assertEquals(LocalDate.of(2028, 2, 29), mar31.lastMonthEnd)
        assertEquals(2, mar31.lastMonthSamePeriod)

        val mar29 = monthOverMonth(entries, millisOf(2028, 3, 29), TEST_ZONE)
        assertEquals(LocalDate.of(2028, 2, 29), mar29.lastMonthEnd)
        assertEquals(2, mar29.lastMonthSamePeriod)

        val mar28 = monthOverMonth(entries, millisOf(2028, 3, 28), TEST_ZONE)
        assertEquals(LocalDate.of(2028, 2, 28), mar28.lastMonthEnd)
        assertEquals(1, mar28.lastMonthSamePeriod)
    }

    @Test
    fun monthOverMonthFollowsTheZone() {
        // 17:00 UTC on 30 Sep is 01:00 on 1 Oct in Shanghai.
        val entries = listOf(testEntry(millisOf(2026, 9, 30, 17, 0, zone = utc)))
        val shanghai = monthOverMonth(entries, now, TEST_ZONE)
        assertEquals(1, shanghai.thisMonth)
        assertEquals(0, shanghai.lastMonthSamePeriod)
        val inUtc = monthOverMonth(entries, now, utc)
        assertEquals(0, inUtc.thisMonth)
        assertEquals(0, inUtc.lastMonthSamePeriod) // 30 Sep is after 16 Sep
    }
}
