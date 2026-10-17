package com.rookierc.boysdairy.data.backup

import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupMergeTest {

    private val base = testEntry(
        timestamp = 1_000L,
        type = EntryType.SOLO,
        durationMinutes = 10,
        note = "hello",
    )

    @Test
    fun everythingIsNewWhenNothingExists() {
        val incoming = listOf(base, base.copy(timestamp = 2_000L))
        assertEquals(incoming, selectEntriesToAdd(emptyList(), incoming))
    }

    @Test
    fun exactDuplicatesAreSkipped() {
        assertTrue(selectEntriesToAdd(listOf(base), listOf(base)).isEmpty())
    }

    @Test
    fun idMoodsAndPleasureDoNotMakeAnEntryDifferent() {
        val existing = listOf(base.copy(id = 5, moodBefore = Mood.BORED, moodAfter = Mood.GUILTY, pleasure = 2))
        val incoming = listOf(base.copy(id = 0, moodBefore = Mood.EXCITED, moodAfter = Mood.ENERGETIC, pleasure = 9))
        assertTrue(selectEntriesToAdd(existing, incoming).isEmpty())
    }

    @Test
    fun differentTimestampTypeDurationOrNoteMeansNewEntry() {
        val variants = listOf(
            base.copy(timestamp = 1_001L),
            base.copy(type = EntryType.PARTNER),
            base.copy(durationMinutes = 11),
            base.copy(note = "hello!"),
            base.copy(note = ""),
        )
        assertEquals(variants, selectEntriesToAdd(listOf(base), variants))
    }

    @Test
    fun noteComparisonIsExact() {
        assertEquals(listOf(base.copy(note = "Hello")), selectEntriesToAdd(listOf(base), listOf(base.copy(note = "Hello"))))
    }

    @Test
    fun repeatsInsideTheIncomingListAreSkippedToo() {
        val result = selectEntriesToAdd(emptyList(), listOf(base, base.copy(id = 9), base.copy(timestamp = 5)))
        assertEquals(listOf(base, base.copy(timestamp = 5)), result)
    }

    @Test
    fun orderOfNewEntriesIsPreserved() {
        val a = base.copy(timestamp = 30)
        val b = base.copy(timestamp = 10)
        val c = base.copy(timestamp = 20)
        assertEquals(listOf(a, b, c), selectEntriesToAdd(listOf(base), listOf(a, base, b, c)))
    }

    @Test
    fun partialOverlapKeepsOnlyTheNewOnes() {
        val existing = listOf(base, base.copy(timestamp = 2_000L))
        val incoming = listOf(base.copy(timestamp = 2_000L), base.copy(timestamp = 3_000L))
        assertEquals(listOf(base.copy(timestamp = 3_000L)), selectEntriesToAdd(existing, incoming))
    }

    // ---- keyword (backup format 2) ------------------------------------------------------------

    @Test
    fun aVersionOneBackupStillMatchesEntriesThatGotAKeywordSince() {
        // Exported before keywords existed, imported after the entry was given one on the device.
        val onDevice = base.copy(id = 3, keyword = "Mia")
        val v1 = BackupCodec.encode(listOf(base), 0).replace("\"formatVersion\": 2", "\"formatVersion\": 1")
        assertTrue(v1.contains("\"formatVersion\": 1"))
        val fromOldBackup = BackupCodec.decode(v1).single()
        assertNull(fromOldBackup.keyword)
        assertTrue(selectEntriesToAdd(listOf(onDevice), listOf(fromOldBackup)).isEmpty())
    }

    @Test
    fun aDuplicateWithAKeywordDoesNotTouchTheEntryOnTheDevice() {
        // Choice: the device's copy wins, MERGE only ever inserts. The file's keyword is dropped
        // with the rest of the duplicate rather than filled into the existing entry.
        assertTrue(selectEntriesToAdd(listOf(base), listOf(base.copy(keyword = "Mia"))).isEmpty())
        assertTrue(selectEntriesToAdd(listOf(base.copy(keyword = "Mia")), listOf(base.copy(keyword = "Zoe"))).isEmpty())
    }

    @Test
    fun insideOneFileTheFirstCopyWinsWhateverItsKeyword() {
        val first = base.copy(keyword = null)
        val second = base.copy(keyword = "Mia")
        assertEquals(listOf(first), selectEntriesToAdd(emptyList(), listOf(first, second)))
        assertEquals(listOf(second), selectEntriesToAdd(emptyList(), listOf(second, first)))
    }

    @Test
    fun newEntriesKeepTheirKeywordThroughAMerge() {
        val incoming = BackupCodec.decode(
            BackupCodec.encode(listOf(base.copy(timestamp = 9_000L, keyword = "Mia"), base), 0),
        )
        assertEquals(listOf(base.copy(timestamp = 9_000L, keyword = "Mia")), selectEntriesToAdd(listOf(base), incoming))
    }

    @Test
    fun suggestedFileNameUsesTheCompactIsoDate() {
        assertEquals("boysdairy-backup-20261016.json", BackupManager.suggestedFileName(LocalDate.of(2026, 10, 16)))
        assertEquals("boysdairy-backup-20270105.json", BackupManager.suggestedFileName(LocalDate.of(2027, 1, 5)))
    }

    @Test
    fun mimeTypeIsJson() {
        assertEquals("application/json", BackupManager.MIME_TYPE)
    }
}
