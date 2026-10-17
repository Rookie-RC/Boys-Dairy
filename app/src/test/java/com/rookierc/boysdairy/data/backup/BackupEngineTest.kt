package com.rookierc.boysdairy.data.backup

import com.rookierc.boysdairy.FakeEntryDao
import com.rookierc.boysdairy.data.EntryRepository
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.testEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class BackupEngineTest {

    private val repository = EntryRepository(FakeEntryDao())
    private val engine = BackupEngine(repository, clock = { 1_760_000_000_000L }, ioDispatcher = Dispatchers.Unconfined)

    private fun streamOf(text: String): () -> InputStream? = { ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) }

    private val a = testEntry(timestamp = 1_000L, durationMinutes = 10, note = "a")
    private val b = testEntry(timestamp = 2_000L, type = EntryType.PARTNER, durationMinutes = 20, note = "b")
    private val c = testEntry(timestamp = 3_000L, durationMinutes = 30, note = "c")

    private fun backupOf(vararg entries: com.rookierc.boysdairy.domain.Entry) =
        BackupCodec.encode(entries.toList(), 0)

    // ---- export -------------------------------------------------------------------------------

    @Test
    fun exportWritesAllEntriesAsJson() = runBlocking {
        repository.addAll(listOf(a, b))
        val out = ByteArrayOutputStream()

        val result = engine.export { out }

        assertEquals(BackupResult.Exported(2), result)
        val json = JSONObject(out.toString(Charsets.UTF_8.name()))
        assertEquals(1_760_000_000_000L, json.getLong("exportedAt"))
        assertEquals(2, json.getJSONArray("entries").length())
        // And the file can be read back by the importer.
        assertEquals(listOf(b, a).map { it.copy(id = 0) }, BackupCodec.decode(out.toString(Charsets.UTF_8.name())))
    }

    @Test
    fun exportWithNothingToExportFailsWithEmptyAndNeverOpensTheDestination() = runBlocking {
        var opened = false
        val result = engine.export {
            opened = true
            ByteArrayOutputStream()
        }
        assertEquals(BackupResult.Failed(BackupError.EMPTY), result)
        assertFalse("destination must stay untouched", opened)
    }

    @Test
    fun exportFailsWithIoWhenNoStreamCanBeOpened() = runBlocking {
        repository.add(a)
        assertEquals(BackupResult.Failed(BackupError.IO), engine.export { null })
    }

    @Test
    fun exportMapsWriteErrorsAndPermissionLossToIo() = runBlocking {
        repository.add(a)
        val failingWrite = object : OutputStream() {
            override fun write(b: Int) = throw IOException("disk full")
        }
        assertEquals(BackupResult.Failed(BackupError.IO), engine.export { failingWrite })
        assertEquals(
            BackupResult.Failed(BackupError.IO),
            engine.export { throw SecurityException("permission revoked") },
        )
    }

    // ---- import: merge ------------------------------------------------------------------------

    @Test
    fun mergeAddsNewEntriesAndSkipsDuplicates() = runBlocking {
        repository.addAll(listOf(a, b))

        val result = engine.import(ImportMode.MERGE, streamOf(backupOf(b, c)))

        assertEquals(BackupResult.Imported(added = 1, skippedDuplicates = 1), result)
        assertEquals(listOf(3_000L, 2_000L, 1_000L), repository.snapshot().map { it.timestamp })
    }

    @Test
    fun mergingTheSameFileTwiceAddsNothingTheSecondTime() = runBlocking {
        val file = backupOf(a, b, c)
        assertEquals(BackupResult.Imported(3, 0), engine.import(ImportMode.MERGE, streamOf(file)))
        assertEquals(BackupResult.Imported(0, 3), engine.import(ImportMode.MERGE, streamOf(file)))
        assertEquals(3, repository.snapshot().size)
    }

    @Test
    fun mergeCountsRepeatsInsideTheFileAsSkipped() = runBlocking {
        val result = engine.import(ImportMode.MERGE, streamOf(backupOf(a, a, b)))
        assertEquals(BackupResult.Imported(added = 2, skippedDuplicates = 1), result)
    }

    // ---- import: replace ----------------------------------------------------------------------

    @Test
    fun replaceWipesExistingEntriesFirst() = runBlocking {
        repository.addAll(listOf(a, b))

        val result = engine.import(ImportMode.REPLACE, streamOf(backupOf(c)))

        assertEquals(BackupResult.Imported(added = 1, skippedDuplicates = 0), result)
        assertEquals(listOf(c), repository.snapshot().map { it.copy(id = 0) })
    }

    @Test
    fun replaceKeepsEverythingFromTheFileEvenIfItRepeatsEntries() = runBlocking {
        val result = engine.import(ImportMode.REPLACE, streamOf(backupOf(a, a)))
        assertEquals(BackupResult.Imported(added = 2, skippedDuplicates = 0), result)
        assertEquals(2, repository.snapshot().size)
    }

    // ---- import: failures leave the diary alone -----------------------------------------------

    @Test
    fun invalidFileFailsAndLeavesExistingEntriesUntouched() = runBlocking {
        repository.addAll(listOf(a, b))
        for (mode in ImportMode.entries) {
            assertEquals(
                BackupResult.Failed(BackupError.INVALID_FORMAT),
                engine.import(mode, streamOf("definitely not a backup")),
            )
        }
        assertEquals(2, repository.snapshot().size)
    }

    @Test
    fun keywordsSurviveExportAndReplaceImport() = runBlocking {
        val solo = testEntry(timestamp = 4_000L, note = "d", keyword = "Mia")
        repository.addAll(listOf(a, solo))
        val out = ByteArrayOutputStream()
        engine.export { out }

        repository.clearAll()
        assertEquals(BackupResult.Imported(2, 0), engine.import(ImportMode.REPLACE, streamOf(out.toString(Charsets.UTF_8.name()))))
        assertEquals(listOf("Mia", null), repository.snapshot().map { it.keyword })
    }

    @Test
    fun mergingAnOldBackupKeepsTheKeywordsOnTheDevice() = runBlocking {
        // A version-1 file (no keywords) of entries that were given a keyword later on.
        val v1 = backupOf(a, c).replace("\"formatVersion\": 2", "\"formatVersion\": 1")
        assertTrue(v1.contains("\"formatVersion\": 1"))
        repository.addAll(listOf(a.copy(keyword = "Mia"), c.copy(keyword = "Zoe")))

        assertEquals(BackupResult.Imported(0, 2), engine.import(ImportMode.MERGE, streamOf(v1)))
        assertEquals(listOf("Zoe", "Mia"), repository.snapshot().map { it.keyword })
    }

    @Test
    fun wrongAppFileIsInvalidFormat() = runBlocking {
        val other = """{"app":"other","formatVersion":1,"entries":[]}"""
        assertEquals(BackupResult.Failed(BackupError.INVALID_FORMAT), engine.import(ImportMode.MERGE, streamOf(other)))
    }

    @Test
    fun newerVersionFileIsUnsupported() = runBlocking {
        val current = BackupCodec.FORMAT_VERSION
        val future = backupOf(a).replace("\"formatVersion\": $current", "\"formatVersion\": ${current + 1}")
        assertTrue(future.contains("\"formatVersion\": ${current + 1}"))
        assertEquals(
            BackupResult.Failed(BackupError.UNSUPPORTED_VERSION),
            engine.import(ImportMode.REPLACE, streamOf(future)),
        )
    }

    @Test
    fun emptyBackupFailsWithEmptyAndReplaceDoesNotWipeTheDiary() = runBlocking {
        repository.add(a)
        val emptyFile = BackupCodec.encode(emptyList(), 0)
        assertEquals(BackupResult.Failed(BackupError.EMPTY), engine.import(ImportMode.REPLACE, streamOf(emptyFile)))
        assertEquals(1, repository.snapshot().size)
    }

    @Test
    fun unreadableSourceIsIo() = runBlocking {
        assertEquals(BackupResult.Failed(BackupError.IO), engine.import(ImportMode.MERGE) { null })
        assertEquals(
            BackupResult.Failed(BackupError.IO),
            engine.import(ImportMode.MERGE) { throw SecurityException("no longer granted") },
        )
        val brokenStream = object : InputStream() {
            override fun read(): Int = throw IOException("card removed")
        }
        assertEquals(BackupResult.Failed(BackupError.IO), engine.import(ImportMode.MERGE) { brokenStream })
    }

    @Test
    fun hugeFilesAreRejectedInsteadOfLoadedIntoMemory() = runBlocking {
        val endless = object : InputStream() {
            override fun read(): Int = 'x'.code
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                java.util.Arrays.fill(b, off, off + len, 'x'.code.toByte())
                return len
            }
        }
        assertEquals(
            BackupResult.Failed(BackupError.INVALID_FORMAT),
            engine.import(ImportMode.MERGE) { endless },
        )
    }

    @Test
    fun importedEntriesGetClampedValues() = runBlocking {
        val text = backupOf(a).replace("\"durationMinutes\": 10", "\"durationMinutes\": 5000")
        engine.import(ImportMode.REPLACE, streamOf(text))
        assertEquals(600, repository.snapshot().single().durationMinutes)
    }

    @Test
    fun exportThenReplaceImportRoundTripsTheDiary() = runBlocking {
        repository.addAll(listOf(a, b, c))
        val out = ByteArrayOutputStream()
        engine.export { out }
        repository.add(testEntry(timestamp = 9_999L, note = "added later"))

        engine.import(ImportMode.REPLACE, streamOf(out.toString(Charsets.UTF_8.name())))

        assertEquals(listOf(c, b, a), repository.snapshot().map { it.copy(id = 0) })
    }
}
