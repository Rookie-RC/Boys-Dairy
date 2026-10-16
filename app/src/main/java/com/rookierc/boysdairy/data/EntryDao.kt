package com.rookierc.boysdairy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    /** One-shot read, newest first (id breaks ties so the order is stable). */
    @Query("SELECT * FROM entries ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<EntryEntity>

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    /** Wipes the table and inserts [entries] atomically: either both happen or neither. */
    @Transaction
    suspend fun replaceAll(entries: List<EntryEntity>) {
        deleteAll()
        insertAll(entries)
    }
}
