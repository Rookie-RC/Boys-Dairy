package com.rookierc.boysdairy

import com.rookierc.boysdairy.data.EntryDao
import com.rookierc.boysdairy.data.EntryEntity
import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneId

val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

/** Epoch millis of a local date-time in [TEST_ZONE] (or [zone]). */
fun millisOf(
    year: Int,
    month: Int,
    day: Int,
    hour: Int = 12,
    minute: Int = 0,
    zone: ZoneId = TEST_ZONE,
): Long = LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

fun testEntry(
    timestamp: Long,
    type: EntryType = EntryType.SOLO,
    durationMinutes: Int = 10,
    moodBefore: Mood = Mood.NEUTRAL,
    moodAfter: Mood = Mood.RELAXED,
    pleasure: Int = 5,
    note: String = "",
    id: Long = 0,
) = Entry(
    id = id,
    timestamp = timestamp,
    type = type,
    durationMinutes = durationMinutes,
    moodBefore = moodBefore,
    moodAfter = moodAfter,
    pleasure = pleasure,
    note = note,
)

/** In-memory [EntryDao] mimicking Room's auto-generated ids and ordering. */
class FakeEntryDao : EntryDao {
    private val rows = MutableStateFlow<List<EntryEntity>>(emptyList())
    private var nextId = 1L

    var replaceAllCalls = 0
        private set

    private fun sorted(list: List<EntryEntity>) = list.sortedWith(compareByDescending<EntryEntity> { it.timestamp }.thenByDescending { it.id })

    override fun observeAll(): Flow<List<EntryEntity>> = rows.map { sorted(it) }

    override suspend fun getAll(): List<EntryEntity> = sorted(rows.value)

    override suspend fun getById(id: Long): EntryEntity? = rows.value.firstOrNull { it.id == id }

    /** Like Room's @Update: replaces the row with the same id; a missing row is a no-op. */
    override suspend fun update(entry: EntryEntity) {
        rows.value = rows.value.map { if (it.id == entry.id) entry else it }
    }

    override suspend fun insert(entry: EntryEntity): Long {
        val stored = entry.copy(id = nextId++)
        rows.value = rows.value + stored
        return stored.id
    }

    override suspend fun insertAll(entries: List<EntryEntity>) {
        rows.value = rows.value + entries.map { it.copy(id = nextId++) }
    }

    override suspend fun deleteById(id: Long) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override suspend fun replaceAll(entries: List<EntryEntity>) {
        replaceAllCalls++
        deleteAll()
        insertAll(entries)
    }
}
