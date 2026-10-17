package com.rookierc.boysdairy.domain

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class NextTimeTest {

    // Saturday 17 Oct 2026, 12:00 Asia/Shanghai.
    private val now = millisOf(2026, 10, 17, 12, 0)
    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")

    /** Fixed seed: inputs are shuffled to prove order never matters, reproducibly. */
    private val random = Random(20261017)

    private fun entryOn(month: Int, day: Int, hour: Int = 12, year: Int = 2026, minute: Int = 0, zone: ZoneId = TEST_ZONE) =
        testEntry(millisOf(year, month, day, hour, minute, zone))

    private fun forecast(entries: List<Entry>, nowMillis: Long = now, zone: ZoneId = TEST_ZONE) =
        forecastNextTime(entries, nowMillis, zone)

    private fun ready(entries: List<Entry>, nowMillis: Long = now, zone: ZoneId = TEST_ZONE): NextTimeForecast.Ready {
        val result = forecast(entries, nowMillis, zone)
        assertTrue("expected a forecast, got $result", result is NextTimeForecast.Ready)
        return result as NextTimeForecast.Ready
    }

    /** One entry at noon on each of [days] (October 2026), shuffled so input order never matters. */
    private fun octoberDays(vararg days: Int, hour: Int = 12) = days.map { entryOn(10, it, hour) }.shuffled(random)

    // ---- not enough history ---------------------------------------------------------------------

    @Test
    fun noEntriesMeansNoCard() {
        assertNull(forecast(emptyList()))
    }

    @Test
    fun oneDayIsNotEnough() {
        val result = forecast(listOf(entryOn(10, 15)))
        assertEquals(NextTimeForecast.NotEnoughDays(loggedDays = 1), result)
        assertEquals(2, (result as NextTimeForecast.NotEnoughDays).missingDays)
    }

    @Test
    fun manyEntriesOnTwoDaysAreStillOnlyTwoDays() {
        val entries = listOf(
            entryOn(10, 10, hour = 8),
            entryOn(10, 10, hour = 13),
            entryOn(10, 10, hour = 22),
            entryOn(10, 14, hour = 1),
            entryOn(10, 14, hour = 23, minute = 59),
        )
        val result = forecast(entries)
        assertEquals(NextTimeForecast.NotEnoughDays(loggedDays = 2), result)
        assertEquals(1, (result as NextTimeForecast.NotEnoughDays).missingDays)
    }

    @Test
    fun exactlyThreeDaysGiveAForecastFromTwoGaps() {
        // Gaps 2 and 4: an even count, so (2 + 4) / 2 = 3.
        val result = ready(octoberDays(6, 8, 12))
        assertEquals(3L, result.typicalIntervalDays)
        assertEquals(LocalDate.of(2026, 10, 12), result.latestDay)
        assertEquals(LocalDate.of(2026, 10, 15), result.predictedDate)
        assertEquals(-2L, result.daysUntil)
    }

    // ---- typical interval -----------------------------------------------------------------------

    @Test
    fun severalEntriesOnOneDayCountAsOneDay() {
        val entries = listOf(
            entryOn(10, 10, hour = 7),
            entryOn(10, 10, hour = 12),
            entryOn(10, 10, hour = 21),
            entryOn(10, 12),
            entryOn(10, 14, hour = 9),
            entryOn(10, 14, hour = 20),
        )
        // Days 10, 12, 14: gaps 2 and 2, never the 0 between two entries of one day.
        val result = ready(entries)
        assertEquals(2L, result.typicalIntervalDays)
        assertEquals(LocalDate.of(2026, 10, 16), result.predictedDate)
    }

    @Test
    fun anOddNumberOfGapsTakesTheMiddleOne() {
        // Gaps 1, 5, 2 -> sorted 1, 2, 5 -> 2 (the mean would be 2.67).
        val result = ready(octoberDays(1, 2, 7, 9))
        assertEquals(2L, result.typicalIntervalDays)
        assertEquals(LocalDate.of(2026, 10, 11), result.predictedDate)
    }

    @Test
    fun anEvenNumberOfGapsRoundsTheMiddlePairHalfUp() {
        // Gaps 1 and 2 -> 1.5 -> 2.
        assertEquals(2L, ready(octoberDays(13, 14, 16)).typicalIntervalDays)
        // Gaps 7, 2, 4, 3 -> sorted 2, 3, 4, 7 -> 3.5 -> 4.
        assertEquals(4L, ready(octoberDays(1, 8, 10, 14, 17)).typicalIntervalDays)
        // Gaps 2, 4, 3, 5 -> sorted 2, 3, 4, 5 -> 3.5 -> 4; gaps 1, 3, 2, 3 -> 2.5 -> 3.
        assertEquals(4L, ready(octoberDays(1, 3, 7, 10, 15)).typicalIntervalDays)
        assertEquals(3L, ready(octoberDays(1, 2, 5, 7, 10)).typicalIntervalDays)
    }

    @Test
    fun aRunOfDailyEntriesGivesOneDayNeverZero() {
        val result = ready(octoberDays(15, 16, 17))
        assertEquals(1L, result.typicalIntervalDays)
        assertEquals(LocalDate.of(2026, 10, 18), result.predictedDate)
        assertEquals(1L, result.daysUntil)
    }

    @Test
    fun onlyTheLatestTenDaysCount() {
        // Ten recent days with gaps 1,1,1,1,1,3,3,3,3 (median 1), plus three older days full of NIGHT
        // entries that, if read too, would add gaps 10, 20, 15 (median (3 + 3) / 2 = 3) and win the
        // usual time.
        val recent = listOf(1, 2, 3, 4, 5, 6, 9, 12, 15, 18).map { entryOn(9, it, hour = 20) }
        val old = listOf(7 to 18, 7 to 28, 8 to 17).flatMap { (month, day) ->
            List(5) { entryOn(month, day, hour = 2) }
        }
        val result = ready((recent + old).shuffled(random), nowMillis = millisOf(2026, 9, 18, 21, 0))
        assertEquals(1L, result.typicalIntervalDays)
        assertEquals(LocalDate.of(2026, 9, 19), result.predictedDate)
        assertEquals(HourBucket.EVENING, result.usualTime)
    }

    @Test
    fun gapsAreCalendarDaysNotTwentyFourHourBlocks() {
        // 23:50 -> 00:10 is 20 minutes but one calendar day; 00:10 -> 23:50 two days later is two.
        val entries = listOf(
            entryOn(10, 10, hour = 23, minute = 50),
            entryOn(10, 11, hour = 0, minute = 10),
            entryOn(10, 13, hour = 23, minute = 50),
        )
        // Gaps 1 and 2 -> 1.5 -> 2.
        assertEquals(2L, ready(entries).typicalIntervalDays)
    }

    // ---- predicted date & days until ------------------------------------------------------------

    @Test
    fun aForecastAheadCountsTheDaysLeft() {
        val result = ready(octoberDays(11, 14, 17))
        assertEquals(LocalDate.of(2026, 10, 20), result.predictedDate)
        assertEquals(3L, result.daysUntil)
    }

    @Test
    fun aForecastForTodayIsZeroDaysAway() {
        val result = ready(octoberDays(8, 11, 14))
        assertEquals(LocalDate.of(2026, 10, 17), result.predictedDate)
        assertEquals(0L, result.daysUntil)
    }

    @Test
    fun theWholeOfTodayCountsAsToday() {
        val entries = octoberDays(8, 11, 14)
        assertEquals(0L, ready(entries, nowMillis = millisOf(2026, 10, 17, 0, 0)).daysUntil)
        assertEquals(0L, ready(entries, nowMillis = millisOf(2026, 10, 17, 23, 59)).daysUntil)
        assertEquals(-1L, ready(entries, nowMillis = millisOf(2026, 10, 18, 0, 0)).daysUntil)
    }

    @Test
    fun aPassedForecastIsNegative() {
        val result = ready(octoberDays(4, 7, 10))
        assertEquals(LocalDate.of(2026, 10, 13), result.predictedDate)
        assertEquals(-4L, result.daysUntil)
    }

    @Test
    fun theForecastCanCrossIntoTheNextMonth() {
        val entries = listOf(entryOn(10, 25), entryOn(10, 28), entryOn(10, 31))
        val result = ready(entries, nowMillis = millisOf(2026, 10, 31, 22, 0))
        assertEquals(LocalDate.of(2026, 11, 3), result.predictedDate)
        assertEquals(3L, result.daysUntil)
    }

    @Test
    fun gapsAcrossMonthAndYearEndsAreCalendarDays() {
        // 27 Dec, 30 Dec, 2 Jan: gaps 3 and 3 across New Year's Eve.
        val entries = listOf(entryOn(12, 27, year = 2026), entryOn(12, 30, year = 2026), entryOn(1, 2, year = 2027))
        val result = ready(entries, nowMillis = millisOf(2027, 1, 3, 9, 0))
        assertEquals(3L, result.typicalIntervalDays)
        assertEquals(LocalDate.of(2027, 1, 2), result.latestDay)
        assertEquals(LocalDate.of(2027, 1, 5), result.predictedDate)
        assertEquals(2L, result.daysUntil)
        // 28 Feb -> 1 Mar is one day in a non-leap year (gaps 1 and 1).
        val february = listOf(entryOn(2, 27, year = 2027), entryOn(2, 28, year = 2027), entryOn(3, 1, year = 2027))
        assertEquals(1L, ready(february, nowMillis = millisOf(2027, 3, 1, 20, 0)).typicalIntervalDays)
    }

    @Test
    fun daylightSavingTimeChangesDoNotShiftDays() {
        // Europe/Berlin: summer time starts on 29 Mar 2026 (23-hour day) and ends on 25 Oct 2026
        // (25-hour day). 00:30 local is the previous day in UTC, so a UTC-based count would put
        // every entry a day early and in the evening.
        val spring = listOf(28, 29, 30).map { entryOn(3, it, hour = 0, minute = 30, zone = berlin) }
        val springResult = ready(spring, nowMillis = millisOf(2026, 3, 30, 12, 0, berlin), zone = berlin)
        assertEquals(1L, springResult.typicalIntervalDays)
        assertEquals(LocalDate.of(2026, 3, 30), springResult.latestDay)
        assertEquals(LocalDate.of(2026, 3, 31), springResult.predictedDate)
        assertEquals(1L, springResult.daysUntil)
        assertEquals(HourBucket.NIGHT, springResult.usualTime)

        val autumn = listOf(24, 25, 26).map { entryOn(10, it, hour = 23, minute = 30, zone = berlin) }
        val autumnResult = ready(autumn, nowMillis = millisOf(2026, 10, 27, 0, 15, berlin), zone = berlin)
        assertEquals(1L, autumnResult.typicalIntervalDays)
        assertEquals(LocalDate.of(2026, 10, 27), autumnResult.predictedDate)
        assertEquals(0L, autumnResult.daysUntil)
        assertEquals(HourBucket.EVENING, autumnResult.usualTime)
    }

    @Test
    fun aLatestEntryAfterTodayCountsAsToday() {
        // Clock set back to 17 Oct while entries go up to 20 Oct: gaps 3, 3 -> 3, anchored on
        // today instead of the 20th, so the forecast is never "overdue" because of it.
        val result = ready(octoberDays(14, 17, 20))
        assertEquals(LocalDate.of(2026, 10, 20), result.latestDay)
        assertEquals(LocalDate.of(2026, 10, 20), result.predictedDate)
        assertEquals(3L, result.daysUntil)

        val farAhead = ready(listOf(entryOn(11, 1), entryOn(11, 2), entryOn(11, 3)))
        assertEquals(LocalDate.of(2026, 10, 18), farAhead.predictedDate)
        assertEquals(1L, farAhead.daysUntil)
    }

    // ---- usual time of day ----------------------------------------------------------------------

    @Test
    fun theUsualTimeIsTheMostCommonBucket() {
        val entries = listOf(
            entryOn(10, 10, hour = 21),
            entryOn(10, 12, hour = 22),
            entryOn(10, 14, hour = 9), // latest, but MORNING is outnumbered
            entryOn(10, 12, hour = 19),
        )
        assertEquals(HourBucket.EVENING, ready(entries).usualTime)
    }

    @Test
    fun bucketBoundariesFollowTheInsightsSplit() {
        assertEquals(HourBucket.NIGHT, ready(octoberDays(10, 12, 14, hour = 5)).usualTime)
        assertEquals(HourBucket.MORNING, ready(octoberDays(10, 12, 14, hour = 6)).usualTime)
        assertEquals(HourBucket.AFTERNOON, ready(octoberDays(10, 12, 14, hour = 12)).usualTime)
        assertEquals(HourBucket.EVENING, ready(octoberDays(10, 12, 14, hour = 18)).usualTime)
    }

    @Test
    fun aTieGoesToTheBucketOfTheLatestEntry() {
        val entries = listOf(
            entryOn(10, 10, hour = 8),
            entryOn(10, 12, hour = 22),
            entryOn(10, 13, hour = 7),
            entryOn(10, 14, hour = 23), // latest: EVENING, tied 2-2 with MORNING
        )
        assertEquals(HourBucket.EVENING, ready(entries.shuffled(random)).usualTime)
        val swapped = listOf(
            entryOn(10, 10, hour = 22),
            entryOn(10, 12, hour = 8),
            entryOn(10, 13, hour = 23),
            entryOn(10, 14, hour = 7), // latest: MORNING
        )
        assertEquals(HourBucket.MORNING, ready(swapped.shuffled(random)).usualTime)
    }

    @Test
    fun aTieIgnoresALatestEntryOutsideTheTiedBuckets() {
        val entries = listOf(
            entryOn(10, 7, hour = 21),
            entryOn(10, 8, hour = 2),
            entryOn(10, 9, hour = 20),
            entryOn(10, 10, hour = 3), // the most recent of the tied NIGHT / EVENING entries (2-2)
            entryOn(10, 11, hour = 15), // latest overall, but AFTERNOON has only one
        )
        assertEquals(HourBucket.NIGHT, ready(entries.shuffled(random)).usualTime)
    }

    @Test
    fun theUsualTimeCountsEveryEntryOfThoseDays() {
        // Four MORNING entries on one day outweigh EVENING on two other days.
        val entries = listOf(
            entryOn(10, 10, hour = 20),
            entryOn(10, 12, hour = 21),
            entryOn(10, 14, hour = 13),
        ) + List(4) { entryOn(10, 11, hour = 7 + it) }
        assertEquals(HourBucket.MORNING, ready(entries.shuffled(random)).usualTime)
    }
}
