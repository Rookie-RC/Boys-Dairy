package com.rookierc.boysdairy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class DateLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // ---- daysSince ----------------------------------------------------------------------------

    @Test
    fun daysSinceSameDayIsZero() {
        val day = LocalDate.of(2026, 10, 16)
        assertEquals(0L, daysSince(day, day))
    }

    @Test
    fun daysSinceSameDayDifferentTimesIsZero() {
        val last = millis(2026, 10, 16, 0, 5)
        val now = millis(2026, 10, 16, 23, 55)
        assertEquals(0L, daysSince(last, now, zone))
    }

    @Test
    fun daysSinceAcrossMidnightIsOneEvenWhenUnder24Hours() {
        val last = millis(2026, 10, 15, 23, 59)
        val now = millis(2026, 10, 16, 0, 1)
        assertEquals(1L, daysSince(last, now, zone))
    }

    @Test
    fun daysSinceAcrossMonthBoundary() {
        assertEquals(1L, daysSince(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 1)))
        assertEquals(1L, daysSince(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1)))
        assertEquals(1L, daysSince(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29)))
        assertEquals(16L, daysSince(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 16)))
    }

    @Test
    fun daysSinceAcrossYearBoundary() {
        assertEquals(1L, daysSince(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun daysSinceNeverNegative() {
        assertEquals(0L, daysSince(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 16)))
    }

    // ---- buildMonthGrid -----------------------------------------------------------------------

    @Test
    fun monthStartingOnMondayHasNoLeadingBlanks() {
        // 1 June 2026 is a Monday.
        val grid = buildMonthGrid(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), grid.first())
        assertEquals(30, grid.filterNotNull().size)
    }

    @Test
    fun monthStartingOnThursdayHasThreeLeadingBlanks() {
        // 1 October 2026 is a Thursday: Mon, Tue, Wed are blank.
        val grid = buildMonthGrid(YearMonth.of(2026, 10))
        assertNull(grid[0])
        assertNull(grid[1])
        assertNull(grid[2])
        assertEquals(LocalDate.of(2026, 10, 1), grid[3])
        assertEquals(31, grid.filterNotNull().size)
    }

    @Test
    fun monthStartingOnSundayHasSixLeadingBlanks() {
        // 1 February 2026 is a Sunday.
        val grid = buildMonthGrid(YearMonth.of(2026, 2))
        assertEquals(6, grid.takeWhile { it == null }.size)
        assertEquals(LocalDate.of(2026, 2, 1), grid[6])
        assertEquals(28, grid.filterNotNull().size)
    }

    @Test
    fun leapFebruary2028Has29DaysAndStartsOnTuesday() {
        // 1 February 2028 is a Tuesday: one leading blank (Monday).
        val grid = buildMonthGrid(YearMonth.of(2028, 2))
        assertNull(grid[0])
        assertEquals(LocalDate.of(2028, 2, 1), grid[1])
        assertEquals(29, grid.filterNotNull().size)
        assertEquals(LocalDate.of(2028, 2, 29), grid.filterNotNull().last())
    }

    @Test
    fun nonLeapFebruaryHas28Days() {
        assertEquals(28, buildMonthGrid(YearMonth.of(2027, 2)).filterNotNull().size)
    }

    @Test
    fun gridSizeIsAlwaysWholeWeeksAndDaysAreConsecutive() {
        for (month in 1..12) {
            val yearMonth = YearMonth.of(2026, month)
            val grid = buildMonthGrid(yearMonth)
            assertEquals(0, grid.size % 7)
            val days = grid.filterNotNull()
            assertEquals(yearMonth.lengthOfMonth(), days.size)
            assertEquals(yearMonth.atDay(1), days.first())
            assertEquals(yearMonth.atEndOfMonth(), days.last())
        }
    }

    // ---- grouping / markers / filtering -------------------------------------------------------

    private fun entry(timestamp: Long, type: EntryType, note: String = "") = Entry(
        timestamp = timestamp,
        type = type,
        durationMinutes = 10,
        moodBefore = Mood.NEUTRAL,
        moodAfter = Mood.RELAXED,
        pleasure = 5,
        note = note,
    )

    @Test
    fun entriesAreGroupedByLocalCalendarDay() {
        val entries = listOf(
            entry(millis(2026, 10, 16, 23, 59), EntryType.SOLO),
            entry(millis(2026, 10, 16, 0, 1), EntryType.SOLO),
            entry(millis(2026, 10, 15, 12, 0), EntryType.SOLO),
        )
        val grouped = groupEntriesByDate(entries, zone)
        assertEquals(2, grouped.size)
        assertEquals(2, grouped.getValue(LocalDate.of(2026, 10, 16)).size)
        assertEquals(1, grouped.getValue(LocalDate.of(2026, 10, 15)).size)
    }

    @Test
    fun partnerMarkerWinsOverSoloOnTheSameDay() {
        val entries = listOf(
            entry(millis(2026, 10, 16, 9, 0), EntryType.SOLO),
            entry(millis(2026, 10, 16, 21, 0), EntryType.PARTNER),
            entry(millis(2026, 10, 15, 9, 0), EntryType.SOLO),
        )
        val markers = buildDayMarkers(entries, zone)
        assertEquals(EntryType.PARTNER, markers[LocalDate.of(2026, 10, 16)])
        assertEquals(EntryType.SOLO, markers[LocalDate.of(2026, 10, 15)])
        assertNull(markers[LocalDate.of(2026, 10, 14)])
    }

    @Test
    fun noteFilterIsCaseInsensitiveSubstringMatch() {
        val entries = listOf(
            entry(1L, EntryType.SOLO, note = "Felt Great today"),
            entry(2L, EntryType.SOLO, note = "tired"),
            entry(3L, EntryType.SOLO, note = "今天心情不错"),
        )
        assertEquals(listOf(1L), filterEntriesByNote(entries, "great").map { it.timestamp })
        assertEquals(listOf(3L), filterEntriesByNote(entries, "心情").map { it.timestamp })
        assertTrue(filterEntriesByNote(entries, "zzz").isEmpty())
    }

    @Test
    fun blankFilterReturnsEverything() {
        val entries = listOf(entry(1L, EntryType.SOLO, note = "a"), entry(2L, EntryType.SOLO))
        assertEquals(entries, filterEntriesByNote(entries, ""))
        assertEquals(entries, filterEntriesByNote(entries, "   "))
    }
}
