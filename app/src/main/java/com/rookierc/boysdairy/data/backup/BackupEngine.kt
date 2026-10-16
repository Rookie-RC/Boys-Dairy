package com.rookierc.boysdairy.data.backup

import com.rookierc.boysdairy.data.EntryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The export/import logic behind [BackupManager], decoupled from Android's ContentResolver so it
 * can be unit-tested with plain streams. Every failure is reported as [BackupResult.Failed];
 * only coroutine cancellation propagates.
 */
internal class BackupEngine(
    private val entryRepository: EntryRepository,
    private val clock: () -> Long,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Writes every entry as JSON to the stream from [openOutput]. Fails with EMPTY, before the
     * destination is opened (and truncated), when there is nothing to export.
     */
    suspend fun export(openOutput: () -> OutputStream?): BackupResult = withContext(ioDispatcher) {
        guarded {
            val entries = entryRepository.snapshot()
            if (entries.isEmpty()) return@guarded BackupResult.Failed(BackupError.EMPTY)

            val bytes = BackupCodec.encode(entries, clock()).toByteArray(Charsets.UTF_8)
            val output = openOutput() ?: return@guarded BackupResult.Failed(BackupError.IO)
            output.use { it.write(bytes) }
            BackupResult.Exported(entries.size)
        }
    }

    /** Reads a backup from the stream from [openInput] and applies it according to [mode]. */
    suspend fun import(mode: ImportMode, openInput: () -> InputStream?): BackupResult =
        withContext(ioDispatcher) {
            guarded {
                val incoming = BackupCodec.decode(readText(openInput() ?: throw IOException("no input stream")))
                when (mode) {
                    ImportMode.MERGE -> {
                        val toAdd = selectEntriesToAdd(entryRepository.snapshot(), incoming)
                        entryRepository.addAll(toAdd)
                        BackupResult.Imported(added = toAdd.size, skippedDuplicates = incoming.size - toAdd.size)
                    }
                    ImportMode.REPLACE -> {
                        entryRepository.replaceAll(incoming)
                        BackupResult.Imported(added = incoming.size, skippedDuplicates = 0)
                    }
                }
            }
        }

    private fun readText(input: InputStream): String = input.use { stream ->
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_BYTES)
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size() > MAX_BACKUP_BYTES) {
                throw BackupException(BackupError.INVALID_FORMAT, "file too large")
            }
        }
        buffer.toString(Charsets.UTF_8.name())
    }

    private suspend fun guarded(block: suspend () -> BackupResult): BackupResult =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: BackupException) {
            BackupResult.Failed(e.error)
        } catch (e: IOException) {
            BackupResult.Failed(BackupError.IO)
        } catch (e: SecurityException) {
            BackupResult.Failed(BackupError.IO)
        } catch (e: RuntimeException) {
            // Database errors (SQLiteException, ...) and anything else unexpected.
            BackupResult.Failed(BackupError.IO)
        }

    companion object {
        /** Largest backup file accepted; real backups are a few hundred KB. */
        const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
        private const val CHUNK_BYTES = 16 * 1024
    }
}
