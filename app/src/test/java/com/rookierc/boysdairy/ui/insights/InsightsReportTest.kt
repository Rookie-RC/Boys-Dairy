package com.rookierc.boysdairy.ui.insights

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.domain.Achievement
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.InsightsPeriod
import com.rookierc.boysdairy.domain.KeywordCount
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which parts of the full report follow the picked range and which always use everything. */
class InsightsReportTest {

    // Friday 16 Oct 2026, 12:00 Asia/Shanghai. "Last 30 days" starts on 17 Sep.
    private val now = millisOf(2026, 10, 16, 12, 0)

    private fun entryOn(month: Int, day: Int, type: EntryType = EntryType.SOLO) =
        testEntry(millisOf(2026, month, day), type = type)

    private fun report(entries: List<Entry>, period: InsightsPeriod) =
        InsightsReport.compute(entries, period, now, TEST_ZONE)

    private val entries = listOf(
        entryOn(10, 10, type = EntryType.PARTNER),
        entryOn(10, 1),
        entryOn(9, 10), // outside the last 30 days, inside the 12-week chart
        entryOn(8, 20), // same
    )

    @Test
    fun theRangeNarrowsTheOverviewAndTheDistributions() {
        val report = report(entries, InsightsPeriod.LAST_30_DAYS)
        assertEquals(InsightsPeriod.LAST_30_DAYS, report.period)
        assertEquals(2, report.stats.totalCount)
        assertEquals(1, report.stats.partnerCount)
        assertEquals(2, report.weekdays.values.sum())
        assertEquals(2, report.hourBuckets.values.sum())
        assertEquals(2, report.stats.moodAfterCounts.values.sum())
        assertFalse(report.periodIsEmpty)
    }

    @Test
    fun theChartAchievementsAndMonthComparisonIgnoreTheRange() {
        val report = report(entries, InsightsPeriod.LAST_30_DAYS)
        assertEquals(4, report.weeks.sumOf { it.total })
        assertEquals(4, report.achievements.single { it.achievement == Achievement.TOTAL_10 }.progress)
        assertEquals(2, report.monthComparison.thisMonth) // 1 and 10 Oct
        assertEquals(1, report.monthComparison.lastMonthSamePeriod) // 10 Sep
    }

    @Test
    fun allTimeUsesEveryEntry() {
        val report = report(entries, InsightsPeriod.ALL)
        assertEquals(4, report.stats.totalCount)
        assertEquals(4, report.weekdays.values.sum())
    }

    @Test
    fun theLongestStreakIsCutAtTheStartOfTheRange() {
        val run = listOf(entryOn(9, 15), entryOn(9, 16), entryOn(9, 17), entryOn(9, 18))
        assertEquals(4, report(run, InsightsPeriod.ALL).longestStreak)
        assertEquals(2, report(run, InsightsPeriod.LAST_30_DAYS).longestStreak)
    }

    @Test
    fun theKeywordLeaderboardFollowsTheRange() {
        val withKeywords = listOf(
            entryOn(10, 12).copy(keyword = "mia"),
            entryOn(10, 10).copy(keyword = "Zoe"),
            entryOn(10, 5).copy(keyword = "MIA"),
            entryOn(9, 10).copy(keyword = "Zoe"), // outside the last 30 days
            entryOn(9, 9).copy(keyword = "Zoe"), // same
            entryOn(10, 11, type = EntryType.PARTNER),
        )
        assertEquals(
            listOf(KeywordCount("mia", 2, millisOf(2026, 10, 12)), KeywordCount("Zoe", 1, millisOf(2026, 10, 10))),
            report(withKeywords, InsightsPeriod.LAST_30_DAYS).keywords,
        )
        assertEquals(listOf("Zoe", "mia"), report(withKeywords, InsightsPeriod.ALL).keywords.map { it.keyword })
    }

    @Test
    fun theKeywordLeaderboardIsEmptyWithoutKeywordsInTheRange() {
        assertTrue(report(entries, InsightsPeriod.ALL).keywords.isEmpty())
        val oldKeyword = listOf(entryOn(10, 10), entryOn(8, 20).copy(keyword = "Mia"))
        assertTrue(report(oldKeyword, InsightsPeriod.LAST_30_DAYS).keywords.isEmpty())
        assertEquals(1, report(oldKeyword, InsightsPeriod.ALL).keywords.size)
    }

    @Test
    fun theKeywordLeaderboardHasAtMostFiveRows() {
        val many = (1..7).map { day -> entryOn(10, day).copy(keyword = "k$day") }
        val keywords = report(many, InsightsPeriod.ALL).keywords
        assertEquals(INSIGHTS_TOP_KEYWORDS, keywords.size)
        assertEquals(listOf("k7", "k6", "k5", "k4", "k3"), keywords.map { it.keyword })
    }

    @Test
    fun anEmptyRangeStillHasTheChartAndTheBadges() {
        val report = report(listOf(entryOn(8, 20)), InsightsPeriod.LAST_30_DAYS)
        assertTrue(report.periodIsEmpty)
        assertEquals(0, report.weekdays.values.sum())
        assertEquals(1, report.weeks.sumOf { it.total })
        assertTrue(report.achievements.single { it.achievement == Achievement.FIRST_ENTRY }.unlocked)
        assertFalse(report(listOf(entryOn(8, 20)), InsightsPeriod.ALL).periodIsEmpty)
    }
}
