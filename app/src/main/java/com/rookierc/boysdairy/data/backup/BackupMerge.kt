package com.rookierc.boysdairy.data.backup

import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType

/** Two entries count as the same diary entry when all of these match. */
private data class EntryIdentity(
    val timestamp: Long,
    val type: EntryType,
    val durationMinutes: Int,
    val note: String,
)

private fun Entry.identity() = EntryIdentity(timestamp, type, durationMinutes, note)

/**
 * The subset of [incoming] to insert on a MERGE import: everything whose (timestamp, type,
 * durationMinutes, note) is neither already in [existing] nor repeated earlier in [incoming].
 * Order is preserved.
 *
 * The keyword (backup format 2) is deliberately not part of that identity, like the moods and the
 * pleasure score: a format-1 backup (no keywords) still recognises the entries it shares with a
 * diary that has keywords since. A duplicate is skipped as a whole, so MERGE never changes an
 * entry already on the device: its keyword (or lack of one) wins over the file's, and inside the
 * file the first copy wins.
 */
internal fun selectEntriesToAdd(existing: List<Entry>, incoming: List<Entry>): List<Entry> {
    val seen = HashSet<EntryIdentity>(existing.size + incoming.size)
    existing.mapTo(seen) { it.identity() }
    return incoming.filter { seen.add(it.identity()) }
}
