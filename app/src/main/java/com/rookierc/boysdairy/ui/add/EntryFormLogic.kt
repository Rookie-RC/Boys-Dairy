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
