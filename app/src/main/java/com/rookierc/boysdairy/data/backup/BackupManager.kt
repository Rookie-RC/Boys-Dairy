package com.rookierc.boysdairy.data.backup

import android.content.ContentResolver
import android.net.Uri
import com.rookierc.boysdairy.data.EntryRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Exports the diary to, and imports it from, a user-chosen document (Storage Access Framework
 * URI). Nothing leaves the device unless the user picks a cloud-backed location themselves.
 *
 * The file contains diary entries only: no PIN, no API key, no settings.
 */
class BackupManager(
    private val contentResolver: ContentResolver,
    entryRepository: EntryRepository,
    clock: () -> Long = System::currentTimeMillis,
) {
    private val engine = BackupEngine(entryRepository, clock)

    /**
     * Writes every entry as JSON to [uri], replacing its content. Fails with EMPTY (leaving the
     * file untouched) if there is nothing to export.
     */
    suspend fun export(uri: Uri): BackupResult =
        engine.export { contentResolver.openOutputStream(uri, "wt") }

    /** Reads a backup from [uri] and applies it according to [mode]. */
    suspend fun import(uri: Uri, mode: ImportMode): BackupResult =
        engine.import(mode) { contentResolver.openInputStream(uri) }

    companion object {
        const val MIME_TYPE = "application/json"

        /** e.g. `boysdairy-backup-20261016.json` */
        fun suggestedFileName(today: LocalDate): String =
            "boysdairy-backup-${today.format(DateTimeFormatter.BASIC_ISO_DATE)}.json"
    }
}
