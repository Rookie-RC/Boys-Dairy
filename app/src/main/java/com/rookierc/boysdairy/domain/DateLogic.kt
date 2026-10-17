package com.rookierc.boysdairy.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Converts epoch milliseconds to a calendar date in [zone]. */
fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/**
 * Number of calendar days from [last] to [today]. This is a calendar-day difference, not a
 * 24-hour floor: 23:59 yesterday vs 00:01 today is 1. Never negative.
 */
fun daysSince(last: LocalDate, today: LocalDate): Long =
    ChronoUnit.DAYS.between(last, today).coerceAtLeast(0L)

/** Same as [daysSince] for two instants, evaluated in [zone]. */
fun daysSince(lastMillis: Long, nowMillis: Long, zone: ZoneId): Long =
    daysSince(lastMillis.toLocalDate(zone), nowMillis.toLocalDate(zone))

/**
 * Builds the cell list for a month calendar whose weeks start on Monday.
 * Leading cells before the 1st and trailing cells after the last day are `null`;
 * the result size is always a multiple of 7.
 */
fun buildMonthGrid(month: YearMonth): List<LocalDate?> {
    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1 // Monday = 1 -> 0 blanks
    val cells = ArrayList<LocalDate?>(42)
    repeat(leadingBlanks) { cells.add(null) }
    for (day in 1..month.lengthOfMonth()) {
        cells.add(month.atDay(day))
    }
    while (cells.size % 7 != 0) {
        cells.add(null)
    }
    return cells
}

fun groupEntriesByDate(entries: List<Entry>, zone: ZoneId): Map<LocalDate, List<Entry>> =
    entries.groupBy { it.timestamp.toLocalDate(zone) }

/**
 * For every day that has entries: [EntryType.PARTNER] if at least one partner entry exists
 * that day, otherwise [EntryType.SOLO].
 */
fun buildDayMarkers(entries: List<Entry>, zone: ZoneId): Map<LocalDate, EntryType> =
    buildDaySummaries(entries, zone).mapValues { (_, summary) -> summary.type }

/** What the calendar shows for one day: the marker type (same rule as [buildDayMarkers]) and how many entries. */
data class DaySummary(val type: EntryType, val count: Int)

/** For every day that has entries: its [DaySummary]. Days without entries are absent. */
fun buildDaySummaries(entries: List<Entry>, zone: ZoneId): Map<LocalDate, DaySummary> =
    groupEntriesByDate(entries, zone).mapValues { (_, dayEntries) ->
        val type = if (dayEntries.any { it.type == EntryType.PARTNER }) EntryType.PARTNER else EntryType.SOLO
        DaySummary(type = type, count = dayEntries.size)
    }

/** Case-insensitive substring match on the note; a blank query returns everything. */
fun filterEntriesByNote(entries: List<Entry>, query: String): List<Entry> =
    filterEntries(entries, query, type = null)

/**
 * Entries whose note contains [query] (case-insensitive, trimmed; blank = no text filter) and
 * whose type is [type] (`null` = any type). Keeps the input order.
 */
fun filterEntries(entries: List<Entry>, query: String, type: EntryType?): List<Entry> {
    val needle = query.trim()
    if (needle.isEmpty() && type == null) return entries
    return entries.filter { entry ->
        (type == null || entry.type == type) &&
            (needle.isEmpty() || entry.note.contains(needle, ignoreCase = true))
    }
}

/**
 * Groups entries by calendar month in [zone]. Months appear in the order of their first entry
 * and entries keep their input order inside a month, so a newest-first list gives newest-first
 * months with newest-first entries.
 */
fun groupEntriesByMonth(entries: List<Entry>, zone: ZoneId): List<Pair<YearMonth, List<Entry>>> =
    entries.groupBy { YearMonth.from(it.timestamp.toLocalDate(zone)) }.toList()
