package com.rookierc.boysdairy.domain

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class AchievementsTest {

    // Friday 16 Oct 2026, 12:00 Asia/Shanghai.
    private val now = millisOf(2026, 10, 16, 12, 0)
    private val today: LocalDate = LocalDate.of(2026, 10, 16)
    private val utc: ZoneId = ZoneId.of("UTC")

    private fun date(month: Int, day: Int, year: Int = 2026): LocalDate = LocalDate.of(year, month, day)

    private fun entryAt(
        day: LocalDate,
        hour: Int = 12,
        minute: Int = 0,
        moodBefore: Mood = Mood.NEUTRAL,
        moodAfter: Mood = Mood.RELAXED,
    ) = testEntry(
        millisOf(day.year, day.monthValue, day.dayOfMonth, hour, minute),
        moodBefore = moodBefore,
        moodAfter = moodAfter,
    )

    private fun entryOn(
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
        moodBefore: Mood = Mood.NEUTRAL,
        moodAfter: Mood = Mood.RELAXED,
    ) = entryAt(date(month, day), hour, minute, moodBefore, moodAfter)

    /** Noon entries whose gaps are [gapDays] (oldest first); the latest is today, so no gap is open. */
    private fun entriesWithGaps(vararg gapDays: Int): List<Entry> {
        val days = mutableListOf(today)
        gapDays.reversed().forEach { days += days.last().minusDays(it.toLong()) }
        return days.map { entryAt(it) }
    }

    private fun gaps(entries: List<Entry>, nowMillis: Long = now, zone: ZoneId = TEST_ZONE) =
        abstinenceGaps(entries, nowMillis, zone)

    private fun achievements(
        entries: List<Entry>,
        excluded: Set<GapExclusion> = emptySet(),
        nowMillis: Long = now,
        zone: ZoneId = TEST_ZONE,
    ) = computeAchievements(entries, nowMillis, zone, excluded)

    private fun List<AchievementProgress>.of(achievement: Achievement) = single { it.achievement == achievement }

    private fun List<AchievementProgress>.gapBadges() = filter { it.achievement.isGap }

    private fun assertProgress(expected: Int, unlocked: Boolean, actual: AchievementProgress) {
        assertEquals("progress of ${actual.achievement}", expected, actual.progress)
        assertEquals("unlocked of ${actual.achievement}", unlocked, actual.unlocked)
        assertEquals(actual.achievement.target, actual.target)
    }

    // ---- abstinenceGaps -----------------------------------------------------------------------

    @Test
    fun noEntriesMeanNoGaps() {
        assertEquals(emptyList<AbstinenceGap>(), gaps(emptyList()))
    }

    @Test
    fun entriesOnlyTodayLeaveNoGap() {
        assertEquals(emptyList<AbstinenceGap>(), gaps(listOf(entryOn(10, 16, hour = 8))))
        // Later today than "now" is still today.
        val wholeDay = listOf(entryOn(10, 16, hour = 23, minute = 59), entryOn(10, 16, hour = 0))
        assertEquals(emptyList<AbstinenceGap>(), gaps(wholeDay))
    }

    @Test
    fun anEntryFiveDaysAgoLeavesAFiveDayOpenGap() {
        val gap = gaps(listOf(entryOn(10, 11))).single()
        assertEquals(AbstinenceGap(date(10, 11), null, 5), gap)
        assertTrue(gap.isOpen)
    }

    @Test
    fun severalEntriesOnOneDayCountOnce() {
        val sameDay = listOf(entryOn(10, 11, hour = 0), entryOn(10, 11, hour = 9), entryOn(10, 11, hour = 23))
        assertEquals(listOf(AbstinenceGap(date(10, 11), null, 5)), gaps(sameDay))
        assertEquals(
            listOf(AbstinenceGap(date(10, 11), date(10, 16), 5)),
            gaps(sameDay + entryOn(10, 16, hour = 8) + entryOn(10, 16, hour = 9)),
        )
    }

    @Test
    fun gapsCountCalendarDaysNotTwentyFourHourBlocks() {
        // 23:59 on 1 Oct to 00:00 on 4 Oct is just over 48 hours, but 3 calendar days.
        val result = gaps(listOf(entryOn(10, 4, hour = 0), entryOn(10, 1, hour = 23, minute = 59)))
        assertEquals(
            listOf(AbstinenceGap(date(10, 1), date(10, 4), 3), AbstinenceGap(date(10, 4), null, 12)),
            result,
        )
        assertFalse(result.first().isOpen)
    }

    @Test
    fun consecutiveDaysAreAOneDayGap() {
        val lateAndEarly = listOf(entryOn(10, 15, hour = 23), entryOn(10, 16, hour = 1))
        assertEquals(listOf(AbstinenceGap(date(10, 15), date(10, 16), 1)), gaps(lateAndEarly))
        // Yesterday's entry leaves a 1-day open gap.
        val yesterday = listOf(entryOn(10, 15, hour = 23, minute = 59))
        assertEquals(listOf(AbstinenceGap(date(10, 15), null, 1)), gaps(yesterday))
    }

    @Test
    fun noOpenGapWhenTheLatestEntryIsToday() {
        val result = gaps(listOf(entryOn(10, 1), entryOn(10, 16)))
        assertEquals(listOf(AbstinenceGap(date(10, 1), date(10, 16), 15)), result)
        assertTrue(result.none { it.isOpen })
    }

    @Test
    fun entriesAfterTodayAreIgnored() {
        val future = listOf(entryOn(10, 17, hour = 0), entryOn(12, 1), entryAt(date(1, 1, year = 2027)))
        assertEquals(emptyList<AbstinenceGap>(), gaps(future))
        assertEquals(listOf(AbstinenceGap(date(10, 10), null, 6)), gaps(future + entryOn(10, 10)))
    }

    @Test
    fun gapsAreOldestFirstWhateverTheInputOrder() {
        val entries = listOf(entryOn(9, 1), entryOn(9, 10), entryOn(10, 1), entryOn(10, 10)).reversed()
        val result = gaps(entries)
        assertEquals(listOf(date(9, 1), date(9, 10), date(10, 1), date(10, 10)), result.map { it.start })
        assertEquals(listOf(date(9, 10), date(10, 1), date(10, 10), null), result.map { it.end })
        assertEquals(listOf(9, 21, 9, 6), result.map { it.days })
    }

    @Test
    fun gapsCrossTheYearAndTheLeapDay() {
        val newYear = gaps(listOf(entryOn(12, 30), entryAt(date(1, 2, year = 2027))), millisOf(2027, 1, 2))
        assertEquals(listOf(AbstinenceGap(date(12, 30), date(1, 2, year = 2027), 3)), newYear)

        val leap = gaps(listOf(entryAt(date(2, 28, year = 2028))), millisOf(2028, 3, 1))
        assertEquals(listOf(AbstinenceGap(date(2, 28, year = 2028), null, 2)), leap)
    }

    @Test
    fun theZoneDecidesTheCalendarDayOfAnEntry() {
        // 20:00 UTC on 13 Oct and 01:00 UTC on 14 Oct are 04:00 and 09:00 on 14 Oct in Shanghai.
        val entries = listOf(
            testEntry(millisOf(2026, 10, 13, 20, 0, zone = utc)),
            testEntry(millisOf(2026, 10, 14, 1, 0, zone = utc)),
        )
        // "now" is 04:00 UTC on 16 Oct: the same calendar day in both zones.
        assertEquals(listOf(AbstinenceGap(date(10, 14), null, 2)), gaps(entries, zone = TEST_ZONE))
        assertEquals(
            listOf(AbstinenceGap(date(10, 13), date(10, 14), 1), AbstinenceGap(date(10, 14), null, 2)),
            gaps(entries, zone = utc),
        )
    }

    @Test
    fun theZoneDecidesWhichDayIsToday() {
        // 02:00 on 16 Oct in Shanghai is still 15 Oct (18:00) in UTC; the entry is on 15 Oct in both.
        val earlyMorning = millisOf(2026, 10, 16, 2, 0)
        val entries = listOf(entryOn(10, 15))
        assertEquals(listOf(AbstinenceGap(date(10, 15), null, 1)), gaps(entries, earlyMorning, TEST_ZONE))
        assertEquals(emptyList<AbstinenceGap>(), gaps(entries, earlyMorning, utc))
    }

    @Test
    fun aDaylightSavingChangeDoesNotShiftTheCount() {
        // Berlin turns its clocks back on 25 Oct 2026: 23:30 on 24 Oct to 00:30 on 26 Oct is 26 hours.
        val berlin = ZoneId.of("Europe/Berlin")
        val entries = listOf(
            testEntry(millisOf(2026, 10, 24, 23, 30, zone = berlin)),
            testEntry(millisOf(2026, 10, 26, 0, 30, zone = berlin)),
        )
        val result = gaps(entries, millisOf(2026, 10, 26, 12, 0, zone = berlin), berlin)
        assertEquals(listOf(AbstinenceGap(date(10, 24), date(10, 26), 2)), result)
    }

    // ---- GapExclusion -------------------------------------------------------------------------

    @Test
    fun exclusionsRoundTripThroughTheirStorageForm() {
        val closed = GapExclusion(date(10, 1), date(10, 9))
        assertFalse(closed.orLater)
        assertEquals("2026-10-01/2026-10-09", closed.encode())
        assertEquals(closed, GapExclusion.decode(closed.encode()))

        val orLater = GapExclusion(date(10, 1), date(10, 16), orLater = true)
        assertEquals("2026-10-01/2026-10-16+", orLater.encode())
        assertEquals(orLater, GapExclusion.decode("2026-10-01/2026-10-16+"))

        assertEquals(GapExclusion(date(12, 31), date(1, 1, year = 2027)), GapExclusion.decode("2026-12-31/2027-01-01"))
    }

    @Test
    fun malformedStorageValuesDecodeToNull() {
        listOf(
            "",
            "/",
            "2026-10-01",
            "2026-10-01/", // the earlier open form, without the day it was excluded
            "2026-10-01/+",
            "/2026-10-09",
            "2026-10-01/2026-10-09/",
            "2026-10-01/2026-10-09++",
            "2026-10-01+/2026-10-09",
            "2026/10/01",
            "2026-10-1/2026-10-09",
            "2026-13-01/2026-10-09",
            "2026-02-30/2026-03-01",
            " 2026-10-01/2026-10-09",
            "2026-10-01/2026-10-09 ",
            "yesterday/today",
        ).forEach { raw -> assertNull("\"$raw\"", GapExclusion.decode(raw)) }
    }

    @Test
    fun anEndThatIsNotAfterTheStartDecodesToNull() {
        assertNull(GapExclusion.decode("2026-10-01/2026-10-01"))
        assertNull(GapExclusion.decode("2026-10-09/2026-10-01"))
        assertNull(GapExclusion.decode("2026-10-01/2026-10-01+"))
        assertNull(GapExclusion.decode("2026-10-09/2026-10-01+"))
        assertNotNull(GapExclusion.decode("2026-10-01/2026-10-02"))
        assertNotNull(GapExclusion.decode("2026-10-01/2026-10-02+"))
    }

    @Test
    fun aClosedExclusionMatchesOnlyTheSameStartAndEnd() {
        val exclusion = GapExclusion(date(10, 1), date(10, 9))
        assertTrue(exclusion.matches(AbstinenceGap(date(10, 1), date(10, 9), 8)))
        assertFalse(exclusion.matches(AbstinenceGap(date(10, 1), date(10, 5), 4))) // split by a new entry
        assertFalse(exclusion.matches(AbstinenceGap(date(10, 5), date(10, 9), 4)))
        assertFalse(exclusion.matches(AbstinenceGap(date(10, 1), date(10, 12), 11))) // its end entry was deleted
        assertFalse(exclusion.matches(AbstinenceGap(date(10, 1), null, 15)))
        assertFalse(exclusion.matches(AbstinenceGap(date(9, 30), date(10, 9), 9)))
    }

    @Test
    fun anOrLaterExclusionMatchesTheGapFromItsStartWhileOpenOrEndingOnItsDayOrLater() {
        // Excluded on 16 Oct while open since 1 Oct.
        val exclusion = GapExclusion(date(10, 1), date(10, 16), orLater = true)
        assertTrue(exclusion.matches(AbstinenceGap(date(10, 1), null, 15)))
        assertTrue(exclusion.matches(AbstinenceGap(date(10, 1), null, 20))) // still open days later
        assertTrue(exclusion.matches(AbstinenceGap(date(10, 1), date(10, 16), 15))) // closed that day
        assertTrue(exclusion.matches(AbstinenceGap(date(10, 1), date(10, 20), 19))) // or later
        assertFalse(exclusion.matches(AbstinenceGap(date(10, 1), date(10, 15), 14))) // split before that day
        assertFalse(exclusion.matches(AbstinenceGap(date(10, 1), date(10, 2), 1)))
        assertFalse(exclusion.matches(AbstinenceGap(date(10, 2), null, 14)))
        assertFalse(exclusion.matches(AbstinenceGap(date(9, 30), date(10, 16), 16)))
    }

    @Test
    fun anOpenGapIsExcludedUpToTheDayItWasExcluded() {
        val open = gaps(listOf(entryOn(10, 9))).single() // 7 open days
        assertEquals(GapExclusion(date(10, 9), today, orLater = true), open.toExclusion())
        assertEquals("2026-10-09/2026-10-16+", open.toExclusion().encode())

        val shorter = AbstinenceGap(date(10, 9), null, 3)
        assertEquals(GapExclusion(date(10, 9), date(10, 12), orLater = true), shorter.toExclusion())
    }

    @Test
    fun aGapIsExcludedByTheExclusionMadeFromIt() {
        val closed = AbstinenceGap(date(10, 1), date(10, 9), 8)
        val open = AbstinenceGap(date(10, 9), null, 7)
        assertEquals(GapExclusion(date(10, 1), date(10, 9), orLater = false), closed.toExclusion())

        assertFalse(closed.isExcludedBy(emptySet()))
        assertTrue(closed.isExcludedBy(setOf(closed.toExclusion())))
        assertTrue(open.isExcludedBy(setOf(GapExclusion(date(1, 1), date(1, 5)), open.toExclusion())))
        assertFalse(open.isExcludedBy(setOf(closed.toExclusion())))
        assertFalse(closed.isExcludedBy(setOf(open.toExclusion())))
    }

    // ---- gapLedger ----------------------------------------------------------------------------

    // Gaps, oldest first: 3, 1, 7 and 20 days closed, then 14 days open (2 Oct to today).
    private val ledgerEntries = listOf(entryOn(9, 1), entryOn(9, 4), entryOn(9, 5), entryOn(9, 12), entryOn(10, 2))

    @Test
    fun gapLedgerKeepsGapsOfAtLeastTheTierNewestFirst() {
        val all = gaps(ledgerEntries)
        assertEquals(listOf(3, 1, 7, 20, 14), all.map { it.days })

        assertEquals(listOf(14, 20, 7, 3), gapLedger(all, emptySet(), 3).counted.map { it.days })
        assertEquals(listOf(14, 20, 7), gapLedger(all, emptySet(), 7).counted.map { it.days })
        assertEquals(listOf(20), gapLedger(all, emptySet(), 15).counted.map { it.days })
        assertEquals(all.reversed(), gapLedger(all, emptySet(), 1).counted)
        assertEquals(GapLedger(emptyList(), emptyList()), gapLedger(all, emptySet(), 100))
        assertTrue(gapLedger(all, emptySet(), 3).excluded.isEmpty())
    }

    @Test
    fun gapLedgerSplitsCountedAndExcludedGaps() {
        val all = gaps(ledgerEntries)
        val exclusions = setOf(
            GapExclusion(date(9, 1), date(9, 4)),
            GapExclusion(date(9, 5), date(9, 12)),
            GapExclusion(date(10, 2), today, orLater = true),
        )
        val ledger = gapLedger(all, exclusions, 7)
        assertEquals(listOf(AbstinenceGap(date(9, 12), date(10, 2), 20)), ledger.counted)
        assertEquals(
            listOf(AbstinenceGap(date(10, 2), null, 14), AbstinenceGap(date(9, 5), date(9, 12), 7)),
            ledger.excluded,
        )
        // The excluded 3-day gap only shows up in tiers it reaches.
        val fromThreeDays = gapLedger(all, exclusions, 3)
        assertEquals(listOf(date(10, 2), date(9, 5), date(9, 1)), fromThreeDays.excluded.map { it.start })
    }

    @Test
    fun gapLedgerOfNoGapsIsEmpty() {
        val ledger = gapLedger(emptyList(), setOf(GapExclusion(date(10, 1), today, orLater = true)), 3)
        assertEquals(GapLedger(emptyList(), emptyList()), ledger)
    }

    // ---- computeAchievements: shape -----------------------------------------------------------

    @Test
    fun progressFollowsTheDeclarationOrderWithHonoursFirst() {
        listOf(emptyList<Entry>(), entriesWithGaps(3, 8), (1..5).map { entryOn(10, it, hour = 2) }).forEach { entries ->
            assertEquals(Achievement.entries.toList(), achievements(entries).map { it.achievement })
        }
        val kinds = Achievement.entries.map { it.kind }
        assertEquals(kinds.sorted(), kinds) // every HONOR before every SHAME
        assertTrue(AchievementKind.HONOR in kinds && AchievementKind.SHAME in kinds)
    }

    @Test
    fun onlyTheGapTiersAreGapBadges() {
        val gapBadges = Achievement.entries.filter { it.isGap }
        assertEquals(Achievement.entries.filter { it.name.startsWith("GAP_") }, gapBadges)
        assertTrue(gapBadges.all { it.kind == AchievementKind.HONOR })
        assertTrue(Achievement.entries.filterNot { it.isGap }.all { it.minGapDays == 0 })
        assertEquals(setOf(3, 7, 15, 30, 100), gapBadges.map { it.minGapDays }.toSet())
    }

    @Test
    fun anEmptyDiaryUnlocksNothing() {
        val list = achievements(emptyList())
        assertTrue(list.none { it.unlocked })
        assertTrue(list.all { it.progress == 0 && it.target == it.achievement.target && it.target > 0 })
    }

    @Test
    fun firstEntryUnlocksWithTheFirstEntry() {
        assertProgress(1, true, achievements(listOf(entryOn(10, 16))).of(Achievement.FIRST_ENTRY))
        assertProgress(1, true, achievements(entriesWithGaps(3, 3, 3)).of(Achievement.FIRST_ENTRY)) // capped
    }

    // ---- computeAchievements: gap badges ------------------------------------------------------

    @Test
    fun eachGapTierUnlocksExactlyAtItsLength() {
        listOf(
            Achievement.GAP_3_X1,
            Achievement.GAP_7_X1,
            Achievement.GAP_15_X1,
            Achievement.GAP_30_X1,
            Achievement.GAP_100_X1,
        ).forEach { badge ->
            val days = badge.minGapDays
            assertProgress(0, false, achievements(entriesWithGaps(days - 1)).of(badge))
            assertProgress(1, true, achievements(entriesWithGaps(days)).of(badge))
        }
    }

    @Test
    fun aLongGapCountsForEveryLowerTier() {
        val list = achievements(entriesWithGaps(8))
        assertProgress(1, true, list.of(Achievement.GAP_3_X1))
        assertProgress(1, false, list.of(Achievement.GAP_3_X5))
        assertProgress(1, true, list.of(Achievement.GAP_7_X1))
        assertProgress(1, false, list.of(Achievement.GAP_7_X5))
        assertProgress(0, false, list.of(Achievement.GAP_15_X1))
        assertProgress(0, false, list.of(Achievement.GAP_30_X1))
        assertProgress(0, false, list.of(Achievement.GAP_100_X1))
    }

    @Test
    fun gapCountsAddUpAcrossTiers() {
        // Gaps of 2, 3, 7, 15, 30 and 100 days: >= 3 five times, >= 7 four, >= 15 three, >= 30 two, >= 100 once.
        val list = achievements(entriesWithGaps(2, 3, 7, 15, 30, 100))
        assertProgress(1, true, list.of(Achievement.GAP_3_X1))
        assertProgress(5, true, list.of(Achievement.GAP_3_X5))
        assertProgress(5, false, list.of(Achievement.GAP_3_X20))
        assertProgress(1, true, list.of(Achievement.GAP_7_X1))
        assertProgress(4, false, list.of(Achievement.GAP_7_X5))
        assertProgress(4, false, list.of(Achievement.GAP_7_X10))
        assertProgress(1, true, list.of(Achievement.GAP_15_X1))
        assertProgress(3, true, list.of(Achievement.GAP_15_X3))
        assertProgress(1, true, list.of(Achievement.GAP_30_X1))
        assertProgress(2, false, list.of(Achievement.GAP_30_X3))
        assertProgress(1, true, list.of(Achievement.GAP_100_X1))
    }

    @Test
    fun gapProgressIsCappedAtTheTarget() {
        assertProgress(4, false, achievements(entriesWithGaps(3, 3, 3, 3)).of(Achievement.GAP_3_X5))

        val list = achievements(entriesWithGaps(*IntArray(25) { 3 }))
        assertProgress(1, true, list.of(Achievement.GAP_3_X1))
        assertProgress(5, true, list.of(Achievement.GAP_3_X5))
        assertProgress(20, true, list.of(Achievement.GAP_3_X20))
        assertProgress(0, false, list.of(Achievement.GAP_7_X1))
    }

    @Test
    fun theOpenGapCountsToo() {
        assertProgress(1, true, achievements(listOf(entryOn(10, 9))).of(Achievement.GAP_7_X1)) // 7 days ago
        assertProgress(0, false, achievements(listOf(entryOn(10, 10))).of(Achievement.GAP_7_X1))

        // A closed 3-day gap plus 7 open days.
        val list = achievements(listOf(entryOn(10, 6), entryOn(10, 9)))
        assertProgress(2, false, list.of(Achievement.GAP_3_X5))
        assertProgress(1, true, list.of(Achievement.GAP_7_X1))
    }

    @Test
    fun theOpenGapIsCountedInCalendarDaysAndFutureEntriesAddNoGap() {
        // 23:59 on 9 Oct to 00:01 on 16 Oct is 7 calendar days.
        val justAfterMidnight = millisOf(2026, 10, 16, 0, 1)
        val late = listOf(entryOn(10, 9, hour = 23, minute = 59))
        assertProgress(1, true, achievements(late, nowMillis = justAfterMidnight).of(Achievement.GAP_7_X1))

        val future = achievements(listOf(entryOn(10, 20), entryOn(11, 30)))
        assertTrue(future.gapBadges().none { it.progress > 0 })
    }

    // ---- computeAchievements: excluded gaps ---------------------------------------------------

    @Test
    fun anExcludedGapNoLongerCountsForAnyTier() {
        val entries = entriesWithGaps(8, 4)
        val (eight, four) = gaps(entries)
        assertEquals(listOf(8, 4), listOf(eight.days, four.days))

        val all = achievements(entries)
        assertProgress(2, false, all.of(Achievement.GAP_3_X5))
        assertProgress(1, true, all.of(Achievement.GAP_7_X1))

        val withoutEight = achievements(entries, setOf(eight.toExclusion()))
        assertProgress(1, true, withoutEight.of(Achievement.GAP_3_X1))
        assertProgress(1, false, withoutEight.of(Achievement.GAP_3_X5))
        assertProgress(0, false, withoutEight.of(Achievement.GAP_7_X1)) // locked again

        val withoutBoth = achievements(entries, setOf(eight.toExclusion(), four.toExclusion()))
        assertTrue(withoutBoth.gapBadges().all { it.progress == 0 && !it.unlocked })
    }

    @Test
    fun exclusionsOnlyAffectGapBadges() {
        val entries = entriesWithGaps(8, 4) + entryOn(10, 16, hour = 2) + entryOn(10, 16, hour = 3)
        val excluded = gaps(entries).map { it.toExclusion() }.toSet()
        val plain = achievements(entries)
        val withExclusions = achievements(entries, excluded)
        assertEquals(plain.filterNot { it.achievement.isGap }, withExclusions.filterNot { it.achievement.isGap })
        assertTrue(withExclusions.of(Achievement.FIRST_ENTRY).unlocked)
        assertTrue(withExclusions.of(Achievement.SAME_DAY_3).unlocked)
    }

    @Test
    fun anExclusionThatMatchesNoGapChangesNothing() {
        val entries = entriesWithGaps(8, 4)
        // Gaps: 4-12 Oct and 12-16 Oct.
        val unrelated = setOf(
            GapExclusion(date(1, 1), date(1, 9)),
            GapExclusion(date(10, 4), date(10, 13), orLater = true), // the gap from 4 Oct ends before 13 Oct
            GapExclusion(date(10, 12), date(10, 15)),
            GapExclusion(today, today.plusDays(1), orLater = true),
        )
        assertEquals(achievements(entries), achievements(entries, unrelated))
    }

    // Last entry on 1 Oct; on 16 Oct (today) the user excludes the 15 open days: forgot to log in between.
    private val lastEntry = listOf(entryOn(10, 1))
    private val excludedToday: Set<GapExclusion> get() = setOf(gaps(lastEntry).single().toExclusion())

    @Test
    fun anOpenGapExcludedTodayStaysExcludedWhileItStaysOpen() {
        assertEquals(setOf(GapExclusion(date(10, 1), today, orLater = true)), excludedToday)
        assertProgress(1, true, achievements(lastEntry).of(Achievement.GAP_15_X1))
        assertProgress(0, false, achievements(lastEntry, excludedToday).of(Achievement.GAP_15_X1))

        // Four days later, still nothing logged.
        val later = achievements(lastEntry, excludedToday, nowMillis = millisOf(2026, 10, 20, 12, 0))
        assertTrue(later.gapBadges().none { it.unlocked })
    }

    @Test
    fun closingAnExcludedOpenGapOnTheDayItWasExcludedKeepsItExcluded() {
        val closed = lastEntry + entryOn(10, 16, hour = 22)
        assertEquals(listOf(AbstinenceGap(date(10, 1), date(10, 16), 15)), gaps(closed))
        assertProgress(1, true, achievements(closed).of(Achievement.GAP_15_X1))
        assertTrue(achievements(closed, excludedToday).gapBadges().none { it.unlocked })

        // Two weeks later the new open gap from 16 Oct counts.
        val list = achievements(closed, excludedToday, nowMillis = millisOf(2026, 10, 30, 12, 0))
        assertProgress(1, true, list.of(Achievement.GAP_7_X1)) // 14 open days
        assertProgress(0, false, list.of(Achievement.GAP_15_X1))
    }

    @Test
    fun closingAnExcludedOpenGapAfterTheDayItWasExcludedKeepsItExcluded() {
        val closed = lastEntry + entryOn(10, 20)
        val on20th = millisOf(2026, 10, 20, 12, 0)
        assertEquals(listOf(AbstinenceGap(date(10, 1), date(10, 20), 19)), gaps(closed, on20th))
        assertProgress(1, true, achievements(closed, nowMillis = on20th).of(Achievement.GAP_15_X1))
        assertTrue(achievements(closed, excludedToday, nowMillis = on20th).gapBadges().none { it.unlocked })
    }

    @Test
    fun backfillingADayBeforeTheExclusionDaySplitsTheGapIntoTwoThatCount() {
        // The forgotten entry on 8 Oct, while the gap is still open: 7 days closed, 8 open.
        val backfilled = lastEntry + entryOn(10, 8)
        assertEquals(listOf(7, 8), gaps(backfilled).map { it.days })
        assertTrue(gaps(backfilled).none { it.isExcludedBy(excludedToday) })
        val list = achievements(backfilled, excludedToday)
        assertProgress(1, true, list.of(Achievement.GAP_7_X1))
        assertProgress(2, false, list.of(Achievement.GAP_7_X5))

        // Same once the gap has been closed on the exclusion day: 7 + 8 closed days.
        val closed = backfilled + entryOn(10, 16)
        assertEquals(listOf(7, 8), gaps(closed).map { it.days })
        assertProgress(2, false, achievements(closed, excludedToday).of(Achievement.GAP_7_X5))

        // The day right before the exclusion day still splits it.
        val dayBefore = lastEntry + entryOn(10, 15)
        assertTrue(gaps(dayBefore).none { it.isExcludedBy(excludedToday) })
        assertProgress(1, true, achievements(dayBefore, excludedToday).of(Achievement.GAP_7_X1)) // 14 days
    }

    @Test
    fun backfillingOnTheExclusionDayKeepsTheFirstPartExcluded() {
        // Closed on 20 Oct, then the 16 Oct entry is backfilled: 1-16 Oct stays out, 16-20 Oct counts.
        val on20th = millisOf(2026, 10, 20, 12, 0)
        val entries = lastEntry + entryOn(10, 20) + entryOn(10, 16)
        val all = gaps(entries, on20th)
        assertEquals(
            listOf(AbstinenceGap(date(10, 1), date(10, 16), 15), AbstinenceGap(date(10, 16), date(10, 20), 4)),
            all,
        )

        val ledger = gapLedger(all, excludedToday, 1)
        assertEquals(listOf(all[1]), ledger.counted)
        assertEquals(listOf(all[0]), ledger.excluded)
        val list = achievements(entries, excludedToday, nowMillis = on20th)
        assertProgress(1, true, list.of(Achievement.GAP_3_X1))
        assertProgress(0, false, list.of(Achievement.GAP_7_X1))
    }

    @Test
    fun loggingAnEntryInsideAnExcludedClosedGapSplitsItIntoGapsThatCountAgain() {
        val entries = listOf(entryOn(10, 1), entryOn(10, 16))
        val excluded = setOf(gaps(entries).single().toExclusion())
        assertTrue(achievements(entries, excluded).gapBadges().none { it.unlocked })

        // The forgotten entry on 8 Oct: gaps of 7 and 8 days, neither matching the old exclusion.
        val backfilled = entries + entryOn(10, 8)
        assertEquals(listOf(7, 8), gaps(backfilled).map { it.days })
        assertTrue(gaps(backfilled).none { it.isExcludedBy(excluded) })
        val list = achievements(backfilled, excluded)
        assertProgress(2, false, list.of(Achievement.GAP_3_X5))
        assertProgress(1, true, list.of(Achievement.GAP_7_X1))
        assertProgress(2, false, list.of(Achievement.GAP_7_X5))
    }

    // ---- computeAchievements: shame badges ----------------------------------------------------

    @Test
    fun streakBadgesUseTheLongestRunOfEntryDays() {
        assertProgress(2, false, achievements(listOf(entryOn(10, 1), entryOn(10, 2))).of(Achievement.STREAK_3))
        // Several entries on one day count once.
        val sameDay = listOf(entryOn(10, 1, hour = 8), entryOn(10, 1, hour = 20), entryOn(10, 2))
        assertProgress(2, false, achievements(sameDay).of(Achievement.STREAK_3))

        // The longest run, not the latest one.
        val three = achievements(listOf(entryOn(9, 1), entryOn(9, 2), entryOn(9, 3), entryOn(10, 10), entryOn(10, 11)))
        assertProgress(3, true, three.of(Achievement.STREAK_3))
        assertProgress(3, false, three.of(Achievement.STREAK_7))

        val eight = achievements((1..8).map { entryOn(10, it) })
        assertProgress(3, true, eight.of(Achievement.STREAK_3))
        assertProgress(7, true, eight.of(Achievement.STREAK_7)) // 8 days, capped at 7
    }

    @Test
    fun sameDayBadgesUseTheBusiestDay() {
        val aroundMidnight = listOf(entryOn(10, 1, hour = 23, minute = 59), entryOn(10, 2, hour = 0))
        assertProgress(1, false, achievements(aroundMidnight).of(Achievement.SAME_DAY_2))

        val two = achievements(listOf(entryOn(10, 1, hour = 8), entryOn(10, 1, hour = 22), entryOn(10, 5)))
        assertProgress(2, true, two.of(Achievement.SAME_DAY_2))
        assertProgress(2, false, two.of(Achievement.SAME_DAY_3))

        val four = achievements((8..11).map { entryOn(10, 3, hour = it) } + entryOn(10, 1))
        assertProgress(3, true, four.of(Achievement.SAME_DAY_3)) // 4 on 3 Oct, capped at 3
    }

    @Test
    fun sameDayFollowsTheZone() {
        // 20:00 UTC on 13 Oct and 01:00 UTC on 14 Oct are both on 14 Oct in Shanghai.
        val entries = listOf(
            testEntry(millisOf(2026, 10, 13, 20, 0, zone = utc)),
            testEntry(millisOf(2026, 10, 14, 1, 0, zone = utc)),
        )
        assertProgress(2, true, achievements(entries).of(Achievement.SAME_DAY_2))
        assertProgress(1, false, achievements(entries, zone = utc).of(Achievement.SAME_DAY_2))
    }

    @Test
    fun burstNeedsFiveEntriesWithinSevenCalendarDays() {
        assertEquals(7, BURST_WINDOW_DAYS)
        // 1-7 Oct is a window of 7 calendar days.
        val burst = listOf(entryOn(10, 1), entryOn(10, 3), entryOn(10, 5), entryOn(10, 6), entryOn(10, 7, hour = 23))
        assertProgress(5, true, achievements(burst).of(Achievement.BURST))

        // The same five spread over 1-8 Oct: no 7 consecutive days hold more than four.
        val spread = listOf(entryOn(10, 1), entryOn(10, 3), entryOn(10, 5), entryOn(10, 6), entryOn(10, 8, hour = 0))
        assertProgress(4, false, achievements(spread).of(Achievement.BURST))
    }

    @Test
    fun burstWindowEdges() {
        // One entry on the window's first day, four on its last.
        val lastDay = (1..4).map { entryOn(10, 7, hour = it) }
        assertProgress(5, true, achievements(listOf(entryOn(10, 1)) + lastDay).of(Achievement.BURST))
        // One day further apart.
        val dayAfter = (1..4).map { entryOn(10, 8, hour = it) }
        assertProgress(4, false, achievements(listOf(entryOn(10, 1)) + dayAfter).of(Achievement.BURST))

        assertProgress(5, true, achievements((1..5).map { entryOn(10, 2, hour = it) }).of(Achievement.BURST))
        val acrossMonths = listOf(entryOn(9, 28), entryOn(9, 30), entryOn(10, 1), entryOn(10, 2), entryOn(10, 4))
        assertProgress(5, true, achievements(acrossMonths).of(Achievement.BURST))
    }

    @Test
    fun nightOwlCountsEntriesFromMidnightToSixAm() {
        val almost = (1..4).map { entryOn(10, it, hour = 5, minute = 59) } +
            entryOn(10, 5, hour = 6) +
            entryOn(10, 6, hour = 23, minute = 59)
        assertProgress(4, false, achievements(almost).of(Achievement.NIGHT_OWL))

        val owl = (1..5).map { entryOn(10, it, hour = 0) }
        assertProgress(5, true, achievements(owl).of(Achievement.NIGHT_OWL))
    }

    @Test
    fun nightOwlFollowsTheZone() {
        // 20:00 UTC is 04:00 the next day in Shanghai.
        val entries = (1..5).map { testEntry(millisOf(2026, 10, it, 20, 0, zone = utc)) }
        assertProgress(5, true, achievements(entries).of(Achievement.NIGHT_OWL))
        assertProgress(0, false, achievements(entries, zone = utc).of(Achievement.NIGHT_OWL))
    }

    @Test
    fun regretCountsGuiltyAndEmptyAfterwards() {
        assertEquals(setOf(Mood.GUILTY, Mood.EMPTY), REGRET_MOODS)
        val regrets = (1..3).map { entryOn(10, it, moodAfter = Mood.GUILTY) } +
            (4..5).map { entryOn(10, it, moodAfter = Mood.EMPTY) }
        assertProgress(5, true, achievements(regrets).of(Achievement.REGRET))

        val otherMoods = (Mood.entries - REGRET_MOODS).mapIndexed { i, mood -> entryOn(9, i + 1, moodAfter = mood) }
        val guiltyBefore = entryOn(9, 20, moodBefore = Mood.GUILTY) // only the mood afterwards counts
        assertProgress(4, false, achievements(regrets.drop(1) + otherMoods + guiltyBefore).of(Achievement.REGRET))
    }

    @Test
    fun boredomCountsBoredBeforehand() {
        val bored = (1..5).map { entryOn(10, it, moodBefore = Mood.BORED) }
        assertProgress(5, true, achievements(bored).of(Achievement.BOREDOM))

        val otherMoods = (Mood.entries - Mood.BORED).mapIndexed { i, mood -> entryOn(9, i + 1, moodBefore = mood) }
        val boredAfter = entryOn(9, 20, moodAfter = Mood.BORED) // only the mood before counts
        assertProgress(4, false, achievements(bored.drop(1) + otherMoods + boredAfter).of(Achievement.BOREDOM))
    }

    @Test
    fun progressNeverExceedsTheTarget() {
        // Three bored, guilty entries a night (03:00-05:00) for 20 days in a row.
        val heavy = (1..20).flatMap { day ->
            (3..5).map { hour -> entryOn(9, day, hour = hour, moodBefore = Mood.BORED, moodAfter = Mood.GUILTY) }
        }
        val list = achievements(heavy)
        assertTrue(list.all { it.progress in 0..it.target })
        assertTrue(list.filter { it.achievement.kind == AchievementKind.SHAME }.all { it.unlocked })
    }

    // ---- newlyUnlocked ------------------------------------------------------------------------

    @Test
    fun newlyUnlockedIsEmptyWhenNothingChanges() {
        val list = achievements(entriesWithGaps(3, 8))
        assertEquals(emptyList<Achievement>(), newlyUnlocked(list, list))
        assertEquals(emptyList<Achievement>(), newlyUnlocked(emptyList(), emptyList()))
        assertEquals(emptyList<Achievement>(), newlyUnlocked(achievements(emptyList()), achievements(emptyList())))
    }

    @Test
    fun theVeryFirstEntryUnlocksFirstEntry() {
        val after = achievements(listOf(entryOn(10, 16)))
        assertEquals(listOf(Achievement.FIRST_ENTRY), newlyUnlocked(achievements(emptyList()), after))
    }

    @Test
    fun newlyUnlockedOnlyReportsTheBadgesThisSaveUnlocked() {
        // Before: 1 and 2 Oct, so FIRST_ENTRY and the 14-day open gap's tiers are unlocked already.
        // Saving 3 Oct makes a 3-day streak; the open gap shrinks to 13 days and keeps the same tiers.
        val before = listOf(entryOn(10, 1), entryOn(10, 2))
        val after = before + entryOn(10, 3, hour = 2)
        assertEquals(listOf(Achievement.STREAK_3), newlyUnlocked(achievements(before), achievements(after)))
    }

    @Test
    fun newlyUnlockedFollowsTheDeclarationOrderWhateverTheInputOrder() {
        // Two entries 7 days ago: the first entry, two gap tiers and a same-day double.
        val before = achievements(emptyList())
        val after = achievements(listOf(entryOn(10, 9, hour = 21), entryOn(10, 9, hour = 22)))
        val expected = listOf(
            Achievement.FIRST_ENTRY,
            Achievement.GAP_3_X1,
            Achievement.GAP_7_X1,
            Achievement.SAME_DAY_2,
        )
        assertEquals(expected, newlyUnlocked(before, after))
        assertEquals(expected, newlyUnlocked(before.reversed(), after.reversed()))
        assertEquals(expected, newlyUnlocked(before.shuffled(Random(7)), after.shuffled(Random(3))))
    }

    @Test
    fun aBadgeLockedAgainByAnEditIsNotReported() {
        // Moving the 9 Oct entry onto today: the 7-day gap disappears and a same-day double appears.
        val before = achievements(listOf(entryOn(10, 9), entryOn(10, 16, hour = 1)))
        val after = achievements(listOf(entryOn(10, 16, hour = 3), entryOn(10, 16, hour = 1)))
        assertTrue(before.of(Achievement.GAP_7_X1).unlocked)
        assertFalse(after.of(Achievement.GAP_7_X1).unlocked)
        assertEquals(listOf(Achievement.SAME_DAY_2), newlyUnlocked(before, after))
    }

    @Test
    fun excludingAGapLocksItsTiersWithoutReportingAnything() {
        val entries = entriesWithGaps(8)
        val counted = achievements(entries)
        val excluded = achievements(entries, gaps(entries).map { it.toExclusion() }.toSet())
        assertEquals(emptyList<Achievement>(), newlyUnlocked(counted, excluded))
        assertEquals(listOf(Achievement.GAP_3_X1, Achievement.GAP_7_X1), newlyUnlocked(excluded, counted))
    }

    @Test
    fun anAchievementMissingFromBeforeCountsAsLocked() {
        val after = achievements(listOf(entryOn(10, 16)))
        val beforeWithoutFirstEntry = after.filterNot { it.achievement == Achievement.FIRST_ENTRY }
        assertEquals(listOf(Achievement.FIRST_ENTRY), newlyUnlocked(beforeWithoutFirstEntry, after))
        assertEquals(emptyList<Achievement>(), newlyUnlocked(after, emptyList()))
    }
}
