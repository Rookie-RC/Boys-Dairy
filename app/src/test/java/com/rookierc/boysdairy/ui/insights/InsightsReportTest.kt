package com.rookierc.boysdairy.ui.insights

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.domain.Achievement
import com.rookierc.boysdairy.domain.AchievementProgress
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.GapExclusion
import com.rookierc.boysdairy.domain.InsightsPeriod
import com.rookierc.boysdairy.domain.KeywordCount
import com.rookierc.boysdairy.domain.toExclusion
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Which parts of the full report follow the picked range and which always use everything. */
class InsightsReportTest {

    // Friday 16 Oct 2026, 12:00 Asia/Shanghai. "Last 30 days" starts on 17 Sep.
    private val now = millisOf(2026, 10, 16, 12, 0)

    private fun entryOn(month: Int, day: Int, type: EntryType = EntryType.SOLO) =
        testEntry(millisOf(2026, month, day), type = type)

    private fun report(
        entries: List<Entry>,
        period: InsightsPeriod,
        exclusions: Set<GapExclusion> = emptySet(),
    ) = InsightsReport.compute(entries, period, now, TEST_ZONE, exclusions)

    private fun List<AchievementProgress>.of(achievement: Achievement) = single { it.achievement == achievement }

    // Gaps, oldest first: 20 Aug -> 10 Sep (21 days), 10 Sep -> 1 Oct (21), 1 Oct -> 10 Oct (9) and
    // the open one from 10 Oct to today (6).
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
    fun theChartGapsAchievementsAndMonthComparisonIgnoreTheRange() {
        val report = report(entries, InsightsPeriod.LAST_30_DAYS)
        assertEquals(4, report.weeks.sumOf { it.total })
        assertEquals(listOf(21, 21, 9, 6), report.gaps.map { it.days })
        assertTrue(report.gaps.last().isOpen)
        assertEquals(4, report.achievements.of(Achievement.GAP_3_X5).progress)
        assertEquals(2, report.achievements.of(Achievement.GAP_15_X3).progress)
        assertEquals(2, report.monthComparison.thisMonth) // 1 and 10 Oct
        assertEquals(1, report.monthComparison.lastMonthSamePeriod) // 10 Sep
        assertTrue(report.exclusions.isEmpty())
    }

    @Test
    fun anExcludedGapNoLongerCountsForAnyGapBadge() {
        val tenSepToFirstOct = GapExclusion(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 1))
        val counted = report(entries, InsightsPeriod.ALL)
        val excluded = report(entries, InsightsPeriod.ALL, setOf(tenSepToFirstOct))

        assertEquals(4, counted.achievements.of(Achievement.GAP_3_X5).progress)
        assertEquals(3, excluded.achievements.of(Achievement.GAP_3_X5).progress)
        assertEquals(3, counted.achievements.of(Achievement.GAP_7_X5).progress)
        assertEquals(2, excluded.achievements.of(Achievement.GAP_7_X5).progress)
        assertEquals(2, counted.achievements.of(Achievement.GAP_15_X3).progress)
        assertEquals(1, excluded.achievements.of(Achievement.GAP_15_X3).progress)
        assertTrue(excluded.achievements.of(Achievement.GAP_15_X1).unlocked) // 20 Aug -> 10 Sep still counts

        // The ledger still lists the gap (as excluded); nothing but the gap badges changes.
        assertEquals(counted.gaps, excluded.gaps)
        assertEquals(setOf(tenSepToFirstOct), excluded.exclusions)
        assertEquals(
            counted.achievements.filterNot { it.achievement.isGap },
            excluded.achievements.filterNot { it.achievement.isGap },
        )
        assertEquals(counted.stats, excluded.stats)
    }

    @Test
    fun excludingTheOpenGapTakesItOutWhileItKeepsGrowing() {
        val single = listOf(entryOn(10, 10)) // open gap of 6 days
        // Excluded today (16 October) while still open.
        val openGap = GapExclusion(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 16), orLater = true)
        assertEquals(openGap, report(single, InsightsPeriod.ALL).gaps.single().toExclusion())
        assertTrue(report(single, InsightsPeriod.ALL).achievements.of(Achievement.GAP_3_X1).unlocked)

        val excluded = report(single, InsightsPeriod.ALL, setOf(openGap)).achievements
        assertEquals(0, excluded.of(Achievement.GAP_3_X1).progress)
        assertFalse(excluded.of(Achievement.GAP_3_X1).unlocked)

        // Ten days later the gap is 16 days long and still excluded.
        val tenDaysLater = now + 10 * 86_400_000L
        val later = InsightsReport.compute(single, InsightsPeriod.ALL, tenDaysLater, TEST_ZONE, setOf(openGap))
        assertEquals(16, later.gaps.single().days)
        assertEquals(0, later.achievements.of(Achievement.GAP_15_X1).progress)
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
        assertTrue(report.achievements.of(Achievement.FIRST_ENTRY).unlocked)
        assertFalse(report(listOf(entryOn(8, 20)), InsightsPeriod.ALL).periodIsEmpty)
    }
}
