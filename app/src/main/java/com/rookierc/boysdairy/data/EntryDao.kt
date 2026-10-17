package com.rookierc.boysdairy.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    /** All rows, newest first (id breaks ties so the order is stable). */
    @Query("SELECT * FROM entries ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    /** One-shot read, newest first (id breaks ties so the order is stable). */
    @Query("SELECT * FROM entries ORDER BY timestamp DESC, id DESC")
    suspend fun getAll(): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)

    /** Overwrites the row with the same id; does nothing if there is no such row. */
    @Update
    suspend fun update(entry: EntryEntity)

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
