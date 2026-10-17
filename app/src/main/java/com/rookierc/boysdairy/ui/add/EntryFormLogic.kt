package com.rookierc.boysdairy.ui.add

import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * Pure rules of the add / edit form, kept out of the ViewModel so they can be unit tested.
 */

/**
 * Puts the diary back the way it was before a save or delete in the form. Handed out by
 * [AddEntryViewModel] and run by the navigation layer after the form (and its ViewModel) is gone,
 * so it must not depend on the ViewModel's scope; it only captures the repository and the entry.
 */
typealias EntryUndo = suspend () -> Unit

/** Everything the user can change in the form; used to tell whether there are unsaved changes. */
data class EntryFormValues(
    val dateTime: LocalDateTime,
    val type: EntryType,
    val durationMinutes: Int,
    val moodBefore: Mood,
    val moodAfter: Mood,
    val pleasure: Int,
    val note: String,
)

/** True if [this] differs from [baseline]. Leading / trailing blanks of the note do not count. */
internal fun EntryFormValues.differsFrom(baseline: EntryFormValues): Boolean =
    copy(note = note.trim()) != baseline.copy(note = baseline.note.trim())

/** The minute-precision local date-time the form shows for a stored [timestamp][Long]. */
internal fun Long.toFormDateTime(zone: ZoneId): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)

/**
 * Start time of a new entry back-filled for [date]: that day at the current hour and minute,
 * pulled back to [now] if that would lie in the future.
 */
internal fun backfillDateTime(date: LocalDate, now: LocalDateTime): LocalDateTime {
    val nowMinutes = now.truncatedTo(ChronoUnit.MINUTES)
    val candidate = LocalDateTime.of(date, nowMinutes.toLocalTime())
    return if (candidate.isAfter(nowMinutes)) nowMinutes else candidate
}

/**
 * Timestamp to store for the date-time [chosen] in the form.
 *
 * When editing ([original] != null) and the user left the time alone, the original timestamp is
 * kept to the millisecond: backup merging recognises duplicates by exact timestamp, so a
 * re-computed (rounded) value would turn an edited entry into a "new" one on the next import.
 * Otherwise the chosen minute is converted in [zone] and never placed after [nowMillis].
 */
internal fun resolveTimestamp(
    chosen: LocalDateTime,
    original: Entry?,
    zone: ZoneId,
    nowMillis: Long,
): Long {
    if (original != null && chosen == original.timestamp.toFormDateTime(zone)) {
        return original.timestamp
    }
    return chosen.atZone(zone).toInstant().toEpochMilli().coerceAtMost(nowMillis)
}

/**
 * The note after the user typed [proposed] while it was [current]. A change that would make the
 * note longer than [maxLength] is refused; anything that does not make it longer (deleting,
 * replacing) is accepted, so an old note that is already over the limit is never cut short.
 */
internal fun acceptNoteEdit(current: String, proposed: String, maxLength: Int): String =
    if (proposed.length <= maxLength || proposed.length <= current.length) proposed else current

/** The note to store: unchanged (to the character) if only surrounding blanks differ from [originalNote]. */
internal fun noteToStore(note: String, originalNote: String?): String {
    val trimmed = note.trim()
    return if (originalNote != null && originalNote.trim() == trimmed) originalNote else trimmed
}

/** The quick-time chips under the date-time button, as minutes before now: just now, 30 min, 1 h, 2 h. */
internal val QUICK_TIME_OFFSETS_MINUTES: List<Int> = listOf(0, 30, 60, 120)

/**
 * Form date-time of a quick-time chip: [minutesAgo] real minutes before [nowMillis], shown in
 * [zone] and cut to the minute (the form's precision). Computed on the instant, so it crosses
 * midnight, and a daylight-saving change, like the clock on the wall did.
 */
internal fun quickTimeDateTime(nowMillis: Long, minutesAgo: Int, zone: ZoneId): LocalDateTime =
    Instant.ofEpochMilli(nowMillis)
        .minus(minutesAgo.toLong(), ChronoUnit.MINUTES)
        .atZone(zone)
        .toLocalDateTime()
        .truncatedTo(ChronoUnit.MINUTES)

/**
 * The chip (its minutes, from [offsets]) whose time is exactly [dateTime] at [nowMillis], or
 * `null` if none matches; used to highlight it.
 */
internal fun matchingQuickTime(
    dateTime: LocalDateTime,
    nowMillis: Long,
    zone: ZoneId,
    offsets: List<Int> = QUICK_TIME_OFFSETS_MINUTES,
): Int? = offsets.firstOrNull { quickTimeDateTime(nowMillis, it, zone) == dateTime }

/**
 * What "undo" after saving an edit writes back: the entry exactly as it was loaded (same id,
 * timestamp to the millisecond, every other field untouched), or `null` if the save did not
 * change anything, so there is nothing to undo.
 */
internal fun revertOfEdit(original: Entry, saved: Entry): Entry? = original.takeIf { it != saved }
