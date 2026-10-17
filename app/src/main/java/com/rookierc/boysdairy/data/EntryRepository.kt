package com.rookierc.boysdairy.data

import com.rookierc.boysdairy.domain.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * @param afterClearAll runs once [clearAll] has deleted the rows: drops data derived from them that
 *   lives elsewhere (the achievement wall's gap exclusions).
 */
class EntryRepository(
    private val dao: EntryDao,
    private val afterClearAll: suspend () -> Unit = {},
) {

    /** All entries, newest first. */
    val entries: Flow<List<Entry>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun add(entry: Entry): Long = dao.insert(entry.toEntity())

    /** The stored entry with [id], or `null` if there is none (e.g. it has been deleted). */
    suspend fun get(id: Long): Entry? = dao.getById(id)?.toDomain()

    /**
     * Overwrites the stored entry that has [Entry.id] with every field of [entry]. The id must be
     * the one of a stored entry (> 0); if that row no longer exists nothing happens.
     */
    suspend fun update(entry: Entry) {
        require(entry.id > 0) { "update() needs the id of a stored entry" }
        dao.update(entry.toEntity())
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** One-shot copy of every entry, newest first. */
    suspend fun snapshot(): List<Entry> = dao.getAll().map { it.toDomain() }

    /** Inserts [entries] as new rows (their ids are ignored and assigned by the database). */
    suspend fun addAll(entries: List<Entry>) {
        if (entries.isEmpty()) return
        dao.insertAll(entries.map { it.toNewRow() })
    }

    /**
     * Replaces the whole diary with [entries] in a single transaction (new ids are assigned).
     * If the insert fails the previous content is kept.
     */
    suspend fun replaceAll(entries: List<Entry>) {
        dao.replaceAll(entries.map { it.toNewRow() })
    }

    /**
     * Deletes every entry, then runs `afterClearAll`. Settings, the PIN and API keys live elsewhere
     * and are not touched.
     */
    suspend fun clearAll() {
        dao.deleteAll()
        afterClearAll()
    }

    private fun Entry.toNewRow(): EntryEntity = copy(id = 0).toEntity()
}
