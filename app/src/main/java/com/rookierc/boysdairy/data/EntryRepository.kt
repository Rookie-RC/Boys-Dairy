package com.rookierc.boysdairy.data

import com.rookierc.boysdairy.domain.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EntryRepository(private val dao: EntryDao) {

    /** All entries, newest first. */
    val entries: Flow<List<Entry>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun add(entry: Entry): Long = dao.insert(entry.toEntity())

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

    private fun Entry.toNewRow(): EntryEntity = copy(id = 0).toEntity()
}
