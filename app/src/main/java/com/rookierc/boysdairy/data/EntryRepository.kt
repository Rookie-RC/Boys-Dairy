package com.rookierc.boysdairy.data

import com.rookierc.boysdairy.domain.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EntryRepository(private val dao: EntryDao) {

    /** All entries, newest first. */
    val entries: Flow<List<Entry>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun add(entry: Entry): Long = dao.insert(entry.toEntity())

    suspend fun delete(id: Long) = dao.deleteById(id)
}
