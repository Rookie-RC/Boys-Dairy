package com.rookierc.boysdairy.domain

import com.rookierc.boysdairy.TEST_ZONE
import com.rookierc.boysdairy.millisOf
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

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

    // ---- achievements -------------------------------------------------------------------------

    private fun achievements(entries: List<Entry>, nowMillis: Long = now) =
        computeAchievements(entries, nowMillis, TEST_ZONE)

    private fun List<AchievementProgress>.of(achievement: Achievement) = single { it.achievement == achievement }

    private fun assertProgress(expected: Int, unlocked: Boolean, actual: AchievementProgress) {
        assertEquals("progress of ${actual.achievement}", expected, actual.progress)
        assertEquals("unlocked of ${actual.achievement}", unlocked, actual.unlocked)
        assertEquals(actual.achievement.target, actual.target)
    }

    /** [count] entries on distinct days, far enough apart to never form a streak. */
    private fun spreadEntries(count: Int, build: (Long) -> Entry = { testEntry(it) }): List<Entry> =
        (0 until count).map { build(millisOf(2026, 10, 16) - it * 2L * 86_400_000L) }

    @Test
    fun noEntriesMeansNothingIsUnlockedAndOrderFollowsTheEnum() {
        val list = achievements(emptyList())
        assertEquals(Achievement.entries.toList(), list.map { it.achievement })
        assertTrue(list.none { it.unlocked })
        assertTrue(list.all { it.progress == 0 && it.target == it.achievement.target && it.target > 0 })
    }

    @Test
    fun firstEntryUnlocksImmediately() {
        assertProgress(1, true, achievements(listOf(entryOn(10, 16))).of(Achievement.FIRST_ENTRY))
    }

    @Test
    fun totalMilestonesUnlockExactlyAtTheTarget() {
        val nine = achievements(spreadEntries(9))
        assertProgress(9, false, nine.of(Achievement.TOTAL_10))

        val ten = achievements(spreadEntries(10))
        assertProgress(10, true, ten.of(Achievement.TOTAL_10))
        assertProgress(10, false, ten.of(Achievement.TOTAL_50))

        val many = achievements(spreadEntries(150))
        assertProgress(50, true, many.of(Achievement.TOTAL_50))
        assertProgress(100, true, many.of(Achievement.TOTAL_100)) // capped at the target
    }

    @Test
    fun streakBadgesUseTheLongestRun() {
        val two = achievements(listOf(entryOn(10, 1), entryOn(10, 2)))
        assertProgress(2, false, two.of(Achievement.STREAK_3))

        val three = achievements(listOf(entryOn(10, 1), entryOn(10, 2), entryOn(10, 3), entryOn(10, 10)))
        assertProgress(3, true, three.of(Achievement.STREAK_3))
        assertProgress(3, false, three.of(Achievement.STREAK_7))

        val week = achievements((1..8).map { entryOn(10, it) })
        assertProgress(7, true, week.of(Achievement.STREAK_7)) // 8 days, capped at 7
    }

    @Test
    fun gapBadgesCountTheGapBetweenTwoEntries() {
        // Gaps 6, 5, 3 and 1 day since the latest entry.
        val sixDays = achievements(listOf(entryOn(10, 1), entryOn(10, 7), entryOn(10, 12), entryOn(10, 15)))
        assertProgress(6, false, sixDays.of(Achievement.GAP_7))

        // Gaps 7, 4, 3.
        val sevenDays = achievements(listOf(entryOn(10, 1), entryOn(10, 8), entryOn(10, 12), entryOn(10, 15)))
        assertProgress(7, true, sevenDays.of(Achievement.GAP_7))
        assertProgress(7, false, sevenDays.of(Achievement.GAP_30))
    }

    @Test
    fun gapBadgesAlsoCountTheGapSinceTheLatestEntry() {
        assertProgress(6, false, achievements(listOf(entryOn(10, 10))).of(Achievement.GAP_7))
        assertProgress(7, true, achievements(listOf(entryOn(10, 9))).of(Achievement.GAP_7))
        assertProgress(29, false, achievements(listOf(entryOn(9, 17))).of(Achievement.GAP_30))
        assertProgress(30, true, achievements(listOf(entryOn(9, 16))).of(Achievement.GAP_30))
    }

    @Test
    fun gapIsCountedInCalendarDaysAndFutureEntriesDoNotGoNegative() {
        // 23:59 six days ago -> today 00:01 is 7 calendar days.
        val justAfterMidnight = millisOf(2026, 10, 16, 0, 1)
        val gap = achievements(listOf(entryOn(10, 9, hour = 23, minute = 59)), justAfterMidnight)
        assertProgress(7, true, gap.of(Achievement.GAP_7))

        assertProgress(0, false, achievements(listOf(entryOn(10, 20))).of(Achievement.GAP_7))
    }

    @Test
    fun partnerBadgeOnlyCountsPartnerEntries() {
        val almost = spreadEntries(9) { testEntry(it, type = EntryType.PARTNER) } +
            spreadEntries(5).map { it.copy(timestamp = it.timestamp - 3_600_000L) }
        assertProgress(9, false, achievements(almost).of(Achievement.PARTNER_10))

        val done = spreadEntries(10) { testEntry(it, type = EntryType.PARTNER) }
        assertProgress(10, true, achievements(done).of(Achievement.PARTNER_10))
    }

    @Test
    fun nightOwlAndEarlyBirdUseTheHourBuckets() {
        val night = (1..4).map { entryOn(10, it, hour = 5, minute = 59) } + entryOn(10, 5, hour = 6, minute = 0)
        val a = achievements(night)
        assertProgress(4, false, a.of(Achievement.NIGHT_OWL))
        assertProgress(1, false, a.of(Achievement.EARLY_BIRD))

        val owl = (1..5).map { entryOn(10, it, hour = 0, minute = 0) }
        assertProgress(5, true, achievements(owl).of(Achievement.NIGHT_OWL))

        val morning = (1..5).map { entryOn(10, it, hour = 11, minute = 59) } + entryOn(10, 6, hour = 12, minute = 0)
        val b = achievements(morning)
        assertProgress(5, true, b.of(Achievement.EARLY_BIRD))
        assertProgress(0, false, b.of(Achievement.NIGHT_OWL))
    }

    @Test
    fun nightOwlFollowsTheZone() {
        // 20:00 UTC is 04:00 in Shanghai.
        val entries = (1..5).map { testEntry(millisOf(2026, 10, it, 20, 0, zone = utc)) }
        assertProgress(5, true, computeAchievements(entries, now, TEST_ZONE).of(Achievement.NIGHT_OWL))
        assertProgress(0, false, computeAchievements(entries, now, utc).of(Achievement.NIGHT_OWL))
    }

    @Test
    fun marathonNeedsSixtyMinutes() {
        val short = listOf(testEntry(now, durationMinutes = MARATHON_MINUTES - 1))
        assertProgress(0, false, achievements(short).of(Achievement.MARATHON))

        val long = listOf(testEntry(now, durationMinutes = MARATHON_MINUTES), testEntry(now - 1, durationMinutes = 90))
        assertProgress(1, true, achievements(long).of(Achievement.MARATHON))
    }

    @Test
    fun peakPleasureNeedsATen() {
        assertProgress(0, false, achievements(listOf(testEntry(now, pleasure = 9))).of(Achievement.PEAK_PLEASURE))
        assertProgress(1, true, achievements(listOf(testEntry(now, pleasure = 10))).of(Achievement.PEAK_PLEASURE))
    }

    @Test
    fun sageModeCountsRelaxedAfterwards() {
        val almost = spreadEntries(9) { testEntry(it, moodAfter = Mood.RELAXED) } +
            testEntry(millisOf(2026, 1, 1), moodAfter = Mood.GUILTY)
        assertProgress(9, false, achievements(almost).of(Achievement.SAGE_MODE))

        val done = spreadEntries(12) { testEntry(it, moodAfter = Mood.RELAXED) }
        assertProgress(10, true, achievements(done).of(Achievement.SAGE_MODE))
    }

    @Test
    fun progressNeverExceedsTheTarget() {
        val heavy = (1..20).flatMap { day ->
            listOf(
                testEntry(millisOf(2026, 9, day, 3, 0), type = EntryType.PARTNER, durationMinutes = 120, pleasure = 10),
                testEntry(millisOf(2026, 9, day, 9, 0), type = EntryType.PARTNER),
            )
        }
        val list = achievements(heavy)
        assertTrue(list.all { it.progress in 0..it.target })
        assertFalse(list.of(Achievement.TOTAL_50).unlocked) // 40 entries
        assertTrue(list.of(Achievement.STREAK_7).unlocked)
    }

    // ---- newlyUnlocked ------------------------------------------------------------------------

    @Test
    fun newlyUnlockedIsEmptyWhenNothingChanges() {
        val list = achievements(spreadEntries(3))
        assertEquals(emptyList<Achievement>(), newlyUnlocked(list, list))
        assertEquals(emptyList<Achievement>(), newlyUnlocked(emptyList(), emptyList()))
        assertEquals(emptyList<Achievement>(), newlyUnlocked(achievements(emptyList()), achievements(emptyList())))
    }

    @Test
    fun theVeryFirstEntryUnlocksFirstEntry() {
        val before = achievements(emptyList())
        val after = achievements(listOf(entryOn(10, 16)))
        assertEquals(listOf(Achievement.FIRST_ENTRY), newlyUnlocked(before, after))
    }

    @Test
    fun newlyUnlockedOnlyReportsTheBadgesThisSaveUnlocked() {
        // Before: 9 entries (FIRST_ENTRY already unlocked). The 10th is a marathon 3 days before the
        // oldest one: no streak, no 7-day gap, 1 of 5 for NIGHT_OWL, not a 10th RELAXED (SAGE_MODE).
        val nine = spreadEntries(9)
        val tenth = testEntry(millisOf(2026, 9, 27, 2, 30), durationMinutes = MARATHON_MINUTES, moodAfter = Mood.GUILTY)
        val unlocked = newlyUnlocked(achievements(nine), achievements(nine + tenth))
        assertEquals(listOf(Achievement.TOTAL_10, Achievement.MARATHON), unlocked)
    }

    @Test
    fun newlyUnlockedFollowsTheEnumOrderWhateverTheInputOrder() {
        val before = achievements(emptyList())
        val after = achievements(listOf(testEntry(now, durationMinutes = 90, pleasure = 10)))
        val expected = listOf(Achievement.FIRST_ENTRY, Achievement.MARATHON, Achievement.PEAK_PLEASURE)
        assertEquals(expected, newlyUnlocked(before.reversed(), after.reversed()))
        assertEquals(expected, newlyUnlocked(before.shuffled(Random(7)), after.shuffled(Random(3))))
    }

    @Test
    fun aBadgeLockedAgainByAnEditIsNotReported() {
        // Editing the only marathon down to 30 minutes and its pleasure up to 10.
        val before = achievements(listOf(testEntry(now, durationMinutes = 75, pleasure = 7)))
        val after = achievements(listOf(testEntry(now, durationMinutes = 30, pleasure = 10)))
        assertTrue(before.of(Achievement.MARATHON).unlocked)
        assertFalse(after.of(Achievement.MARATHON).unlocked)
        assertEquals(listOf(Achievement.PEAK_PLEASURE), newlyUnlocked(before, after))
    }

    @Test
    fun anAchievementMissingFromBeforeCountsAsLocked() {
        val after = achievements(listOf(entryOn(10, 16)))
        val beforeWithoutFirstEntry = after.filterNot { it.achievement == Achievement.FIRST_ENTRY }
        assertEquals(listOf(Achievement.FIRST_ENTRY), newlyUnlocked(beforeWithoutFirstEntry, after))
        assertEquals(emptyList<Achievement>(), newlyUnlocked(after, emptyList()))
    }
}
