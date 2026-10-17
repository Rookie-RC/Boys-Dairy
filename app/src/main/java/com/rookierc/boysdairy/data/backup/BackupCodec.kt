package com.rookierc.boysdairy.data.backup

import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.domain.normalizeKeyword
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON (de)serialisation of the diary. Format, version 2:
 *
 * ```
 * {"app":"boysdairy","formatVersion":2,"exportedAt":<epoch millis>,
 *  "entries":[{"timestamp":<epoch millis>,"type":"SOLO","durationMinutes":12,
 *              "moodBefore":"STRESSED","moodAfter":"RELAXED","pleasure":7,"note":"...",
 *              "keyword":"..."}]}
 * ```
 *
 * Version history: 1 had no `keyword`; 2 adds it as an optional string, written only when the
 * entry has one. Both versions are read; `keyword` is optional in either (missing or `null` =
 * none) and normalised like the form does it ([normalizeKeyword]: trimmed, blank = none, never
 * kept on a partner entry, never shortened).
 *
 * Enums are written by name. Row ids, the PIN, the API key and all settings are deliberately
 * not part of a backup.
 */
object BackupCodec {
    const val APP_ID = "boysdairy"
    const val FORMAT_VERSION = 2

    const val MIN_DURATION_MINUTES = 1
    const val MAX_DURATION_MINUTES = 600
    const val MIN_PLEASURE = 1
    const val MAX_PLEASURE = 10

    private const val KEY_APP = "app"
    private const val KEY_VERSION = "formatVersion"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_TYPE = "type"
    private const val KEY_DURATION = "durationMinutes"
    private const val KEY_MOOD_BEFORE = "moodBefore"
    private const val KEY_MOOD_AFTER = "moodAfter"
    private const val KEY_PLEASURE = "pleasure"
    private const val KEY_NOTE = "note"
    private const val KEY_KEYWORD = "keyword"

    fun encode(entries: List<Entry>, exportedAtMillis: Long): String {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
                .put(KEY_TIMESTAMP, entry.timestamp)
                .put(KEY_TYPE, entry.type.name)
                .put(KEY_DURATION, entry.durationMinutes)
                .put(KEY_MOOD_BEFORE, entry.moodBefore.name)
                .put(KEY_MOOD_AFTER, entry.moodAfter.name)
                .put(KEY_PLEASURE, entry.pleasure)
                .put(KEY_NOTE, entry.note)
            // Absent rather than null: an entry without a keyword looks exactly like in version 1.
            entry.keyword?.let { obj.put(KEY_KEYWORD, it) }
            array.put(obj)
        }
        return JSONObject()
            .put(KEY_APP, APP_ID)
            .put(KEY_VERSION, FORMAT_VERSION)
            .put(KEY_EXPORTED_AT, exportedAtMillis)
            .put(KEY_ENTRIES, array)
            .toString(2)
    }

    /**
     * Parses a backup. The returned entries have id 0. Out-of-range pleasure and duration values
     * are clamped.
     *
     * @throws BackupException [BackupError.INVALID_FORMAT] (not JSON, wrong app marker, missing or
     *   badly typed fields, unknown enum names), [BackupError.UNSUPPORTED_VERSION] (newer format),
     *   or [BackupError.EMPTY] (no entries).
     */
    fun decode(json: String): List<Entry> {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw invalid("not a JSON object", e)
        }

        if (root.opt(KEY_APP) != APP_ID) throw invalid("wrong app marker")

        val version = (root.opt(KEY_VERSION) as? Number)?.toLong() ?: throw invalid("missing formatVersion")
        if (version > FORMAT_VERSION) {
            throw BackupException(BackupError.UNSUPPORTED_VERSION, "formatVersion $version")
        }
        if (version < 1) throw invalid("bad formatVersion")

        val array = root.opt(KEY_ENTRIES) as? JSONArray ?: throw invalid("missing entries")
        if (array.length() == 0) throw BackupException(BackupError.EMPTY, "no entries")

        val entries = ArrayList<Entry>(array.length())
        for (index in 0 until array.length()) {
            val obj = array.opt(index) as? JSONObject ?: throw invalid("entry $index is not an object")
            entries.add(decodeEntry(obj, index))
        }
        return entries
    }

    private fun decodeEntry(obj: JSONObject, index: Int): Entry {
        fun number(key: String): Long =
            (obj.opt(key) as? Number)?.toLong() ?: throw invalid("entry $index: $key missing or not a number")

        fun text(key: String): String =
            obj.opt(key) as? String ?: throw invalid("entry $index: $key missing or not a string")

        fun <E : Enum<E>> enumValue(key: String, values: Array<E>): E {
            val name = text(key)
            return values.firstOrNull { it.name == name } ?: throw invalid("entry $index: unknown $key")
        }

        /** Optional string: missing or JSON null = `null`; any other non-string is malformed. */
        fun optionalText(key: String): String? = when (val value = obj.opt(key)) {
            null, JSONObject.NULL -> null
            is String -> value
            else -> throw invalid("entry $index: $key not a string")
        }

        val type = enumValue(KEY_TYPE, EntryType.entries.toTypedArray())
        return Entry(
            id = 0,
            timestamp = number(KEY_TIMESTAMP),
            type = type,
            durationMinutes = number(KEY_DURATION)
                .coerceIn(MIN_DURATION_MINUTES.toLong(), MAX_DURATION_MINUTES.toLong()).toInt(),
            moodBefore = enumValue(KEY_MOOD_BEFORE, Mood.entries.toTypedArray()),
            moodAfter = enumValue(KEY_MOOD_AFTER, Mood.entries.toTypedArray()),
            pleasure = number(KEY_PLEASURE)
                .coerceIn(MIN_PLEASURE.toLong(), MAX_PLEASURE.toLong()).toInt(),
            note = text(KEY_NOTE),
            keyword = normalizeKeyword(optionalText(KEY_KEYWORD), type),
        )
    }

    private fun invalid(message: String, cause: Throwable? = null) =
        BackupException(BackupError.INVALID_FORMAT, message, cause)
}
