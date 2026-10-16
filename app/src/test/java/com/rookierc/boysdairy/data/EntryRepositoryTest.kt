package com.rookierc.boysdairy.data

import com.rookierc.boysdairy.FakeEntryDao
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.testEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
}
