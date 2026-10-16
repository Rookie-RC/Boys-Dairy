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
 */
internal fun selectEntriesToAdd(existing: List<Entry>, incoming: List<Entry>): List<Entry> {
    val seen = HashSet<EntryIdentity>(existing.size + incoming.size)
    existing.mapTo(seen) { it.identity() }
    return incoming.filter { seen.add(it.identity()) }
}
