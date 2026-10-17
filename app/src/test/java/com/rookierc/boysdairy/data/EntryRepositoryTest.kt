package com.rookierc.boysdairy.data

import com.rookierc.boysdairy.FakeEntryDao
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.testEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryRepositoryTest {

    private val dao = FakeEntryDao()
    private val repository = EntryRepository(dao)

    @Test
    fun snapshotIsNewestFirstAndMatchesTheLiveFlow() = runBlocking {
        repository.add(testEntry(timestamp = 100))
        repository.add(testEntry(timestamp = 300))
        repository.add(testEntry(timestamp = 200))

        assertEquals(listOf(300L, 200L, 100L), repository.snapshot().map { it.timestamp })
        assertEquals(repository.entries.first().map { it.timestamp }, repository.snapshot().map { it.timestamp })
    }

    @Test
    fun snapshotMapsEveryField() = runBlocking {
        repository.add(
            testEntry(
                timestamp = 42,
                type = EntryType.PARTNER,
                durationMinutes = 17,
                moodBefore = Mood.BORED,
                moodAfter = Mood.GUILTY,
                pleasure = 9,
                note = "nöte 😀",
            ),
        )
        val stored = repository.snapshot().single()
        assertTrue(stored.id > 0)
        assertEquals(
            testEntry(42, EntryType.PARTNER, 17, Mood.BORED, Mood.GUILTY, 9, "nöte 😀", id = stored.id),
            stored,
        )
    }

    @Test
    fun keywordIsStoredReadBackAndUpdated() = runBlocking {
        val id = repository.add(testEntry(timestamp = 42, keyword = "Mia"))
        assertEquals("Mia", repository.get(id)?.keyword)
        assertEquals("Mia", dao.getById(id)?.keyword)

        repository.update(repository.get(id)!!.copy(keyword = null))
        assertNull(repository.get(id)?.keyword)
        assertNull(dao.getById(id)?.keyword)
    }

    @Test
    fun addAllInsertsNewRowsAndIgnoresSuppliedIds() = runBlocking {
        repository.add(testEntry(timestamp = 1))
        repository.addAll(listOf(testEntry(timestamp = 2, id = 1), testEntry(timestamp = 3, id = 1)))

        val all = repository.snapshot()
        assertEquals(3, all.size)
        assertEquals(3, all.map { it.id }.toSet().size) // no id clashes
    }

    @Test
    fun addAllWithNothingIsANoOp() = runBlocking {
        repository.addAll(emptyList())
        assertTrue(repository.snapshot().isEmpty())
    }

    @Test
    fun replaceAllSwapsTheWholeContentInOneCall() = runBlocking {
        repository.add(testEntry(timestamp = 1))
        repository.add(testEntry(timestamp = 2))

        repository.replaceAll(listOf(testEntry(timestamp = 10), testEntry(timestamp = 20)))

        assertEquals(listOf(20L, 10L), repository.snapshot().map { it.timestamp })
        assertEquals(1, dao.replaceAllCalls) // went through the transactional DAO method
    }

    @Test
    fun replaceAllWithEmptyListClearsTheDiary() = runBlocking {
        repository.add(testEntry(timestamp = 1))
        repository.replaceAll(emptyList())
        assertTrue(repository.snapshot().isEmpty())
    }

    @Test
    fun clearAllDeletesEveryEntryAndTheLiveFlowFollows() = runBlocking {
        repository.add(testEntry(timestamp = 1))
        repository.add(testEntry(timestamp = 2, type = EntryType.PARTNER))

        repository.clearAll()

        assertTrue(repository.snapshot().isEmpty())
        assertTrue(repository.entries.first().isEmpty())
    }

    @Test
    fun clearAllRunsAfterClearAllOnceTheRowsAreGone() = runBlocking {
        val rowsSeenByCallback = mutableListOf<Int>()
        val clearing = EntryRepository(dao, afterClearAll = { rowsSeenByCallback += dao.getAll().size })
        clearing.add(testEntry(timestamp = 1))
        clearing.add(testEntry(timestamp = 2))
        assertTrue(rowsSeenByCallback.isEmpty()) // only clearAll runs it

        clearing.clearAll()
        assertEquals(listOf(0), rowsSeenByCallback)

        clearing.clearAll() // once per call, even on an empty diary
        assertEquals(listOf(0, 0), rowsSeenByCallback)
    }

    @Test
    fun clearAllOnAnEmptyDiaryIsHarmlessAndNewEntriesStillWork() = runBlocking {
        repository.clearAll()
        assertTrue(repository.snapshot().isEmpty())

        repository.add(testEntry(timestamp = 5))
        assertEquals(listOf(5L), repository.snapshot().map { it.timestamp })
    }

    // ---- get / update -------------------------------------------------------------------------

    @Test
    fun getReturnsTheStoredEntryOrNull() = runBlocking {
        val id = repository.add(testEntry(timestamp = 7, type = EntryType.PARTNER, note = "hi"))

        assertEquals(testEntry(timestamp = 7, type = EntryType.PARTNER, note = "hi", id = id), repository.get(id))
        assertNull(repository.get(id + 100))

        repository.delete(id)
        assertNull(repository.get(id))
    }

    @Test
    fun updateOverwritesEveryFieldAndKeepsTheId() = runBlocking {
        val id = repository.add(testEntry(timestamp = 1_000, note = "old"))
        val otherId = repository.add(testEntry(timestamp = 2_000, note = "other"))

        val edited = testEntry(
            timestamp = 1_234,
            type = EntryType.PARTNER,
            durationMinutes = 42,
            moodBefore = Mood.EXCITED,
            moodAfter = Mood.ENERGETIC,
            pleasure = 10,
            note = "new",
            id = id,
        )
        repository.update(edited)

        assertEquals(edited, repository.get(id))
        assertEquals(2, repository.snapshot().size) // updated in place, nothing inserted
        assertEquals("other", repository.get(otherId)?.note) // other rows untouched
    }

    @Test
    fun updateKeepsTheTimestampToTheMillisecond() = runBlocking {
        val id = repository.add(testEntry(timestamp = 1_792_180_000_123L))
        val stored = repository.get(id)!!

        repository.update(stored.copy(pleasure = 9))

        assertEquals(1_792_180_000_123L, repository.get(id)?.timestamp)
    }

    @Test
    fun updateMovesTheEntryInTheNewestFirstOrder() = runBlocking {
        val a = repository.add(testEntry(timestamp = 100))
        repository.add(testEntry(timestamp = 200))

        repository.update(repository.get(a)!!.copy(timestamp = 300))

        assertEquals(listOf(300L, 200L), repository.entries.first().map { it.timestamp })
    }

    @Test
    fun updateOfADeletedEntryDoesNothing() = runBlocking {
        val id = repository.add(testEntry(timestamp = 1))
        repository.delete(id)

        repository.update(testEntry(timestamp = 5, id = id))

        assertTrue(repository.snapshot().isEmpty())
    }

    @Test
    fun updateRequiresAStoredId() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update(testEntry(timestamp = 1, id = 0)) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.update(testEntry(timestamp = 1, id = -1)) }
        }
    }

    @Test
    fun liveFlowBreaksTimestampTiesByNewestId() = runBlocking {
        val first = repository.add(testEntry(timestamp = 50, note = "first"))
        val second = repository.add(testEntry(timestamp = 50, note = "second"))

        assertEquals(listOf(second, first), repository.entries.first().map { it.id })
        assertEquals(listOf(second, first), repository.snapshot().map { it.id })
    }
}
