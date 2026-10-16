package com.rookierc.boysdairy.data.backup

/** How an imported backup is combined with the entries already on the device. */
enum class ImportMode {
    /** Keep existing entries and add the ones from the file that are not already present. */
    MERGE,

    /** Delete every existing entry first, then insert the ones from the file. */
    REPLACE,
}

enum class BackupError {
    /** The file could not be read or written (permission revoked, storage full, ...). */
    IO,

    /** Not a Boys' Diary backup, or required data is missing or malformed. */
    INVALID_FORMAT,

    /** Written by a newer app version whose format this version does not understand. */
    UNSUPPORTED_VERSION,

    /** The backup (or the diary being exported) contains no entries. */
    EMPTY,
}

sealed interface BackupResult {
    data class Exported(val count: Int) : BackupResult
    data class Imported(val added: Int, val skippedDuplicates: Int) : BackupResult
    data class Failed(val error: BackupError) : BackupResult
}

/**
 * Thrown by [BackupCodec.decode]. The message is for developers only and never contains diary
 * content.
 */
class BackupException(
    val error: BackupError,
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: error.name, cause)
