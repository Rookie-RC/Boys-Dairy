package com.rookierc.boysdairy.data.backup

import com.rookierc.boysdairy.domain.Entry
import com.rookierc.boysdairy.domain.EntryType
import com.rookierc.boysdairy.domain.Mood
import com.rookierc.boysdairy.testEntry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val sample = listOf(
        testEntry(
            timestamp = 1_760_000_000_000L,
            type = EntryType.PARTNER,
            durationMinutes = 45,
            moodBefore = Mood.EXCITED,
            moodAfter = Mood.ENERGETIC,
            pleasure = 9,
            note = "Line one\nLine \"two\" \\ with 😀 and 中文",
            id = 77,
        ),
        testEntry(timestamp = 1_760_000_100_000L, note = ""),
        testEntry(timestamp = 1_760_000_200_000L, note = "with a muse", keyword = "Mia 小蓝 😀"),
    )

    private fun errorOf(json: String): BackupError {
        try {
            BackupCodec.decode(json)
        } catch (e: BackupException) {
            return e.error
        }
        fail("expected BackupException")
        throw AssertionError()
    }

    /** A valid document with one entry; [mutate] edits the entry object, [root] the envelope. */
    private fun document(
        root: (JSONObject) -> Unit = {},
        mutate: (JSONObject) -> Unit = {},
    ): String {
        val entry = JSONObject(
            """{"timestamp":1760000000000,"type":"SOLO","durationMinutes":12,"moodBefore":"STRESSED","moodAfter":"RELAXED","pleasure":7,"note":"hi"}""",
        )
        mutate(entry)
        val doc = JSONObject()
            .put("app", "boysdairy")
            .put("formatVersion", 1)
            .put("exportedAt", 1L)
            .put("entries", JSONArray().put(entry))
        root(doc)
        return doc.toString()
    }

    // ---- round trip ---------------------------------------------------------------------------

    @Test
    fun roundTripPreservesEverythingExceptIds() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample, exportedAtMillis = 5))
        assertEquals(sample.map { it.copy(id = 0) }, decoded)
    }

    @Test
    fun roundTripCoversEveryEnumValue() {
        val entries = Mood.entries.mapIndexed { i, mood ->
            testEntry(
                timestamp = 1_000L + i,
                type = EntryType.entries[i % EntryType.entries.size],
                moodBefore = mood,
                moodAfter = Mood.entries[(i + 1) % Mood.entries.size],
            )
        }
        assertEquals(entries, BackupCodec.decode(BackupCodec.encode(entries, 0)))
    }

    @Test
    fun roundTripKeepsNegativeAndHugeTimestamps() {
        val entries = listOf(testEntry(timestamp = -86_400_000L), testEntry(timestamp = Long.MAX_VALUE / 2))
        assertEquals(entries, BackupCodec.decode(BackupCodec.encode(entries, 0)))
    }

    // ---- format -------------------------------------------------------------------------------

    @Test
    fun encodedDocumentHasTheDocumentedShape() {
        val root = JSONObject(BackupCodec.encode(sample, exportedAtMillis = 1_760_000_999_000L))

        assertEquals(setOf("app", "formatVersion", "exportedAt", "entries"), root.keys().asSequence().toSet())
        assertEquals("boysdairy", root.getString("app"))
        assertEquals(2, root.getInt("formatVersion"))
        assertEquals(BackupCodec.FORMAT_VERSION, root.getInt("formatVersion"))
        assertEquals(1_760_000_999_000L, root.getLong("exportedAt"))

        val entries = root.getJSONArray("entries")
        assertEquals(3, entries.length())
        val first = entries.getJSONObject(0)
        assertEquals(
            setOf("timestamp", "type", "durationMinutes", "moodBefore", "moodAfter", "pleasure", "note"),
            first.keys().asSequence().toSet(),
        )
        assertEquals(1_760_000_000_000L, first.getLong("timestamp"))
        assertEquals("PARTNER", first.getString("type"))
        assertEquals(45, first.getInt("durationMinutes"))
        assertEquals("EXCITED", first.getString("moodBefore"))
        assertEquals("ENERGETIC", first.getString("moodAfter"))
        assertEquals(9, first.getInt("pleasure"))
    }

    @Test
    fun keywordIsWrittenOnlyWhenThereIsOne() {
        val entries = JSONObject(BackupCodec.encode(sample, 0)).getJSONArray("entries")
        assertFalse(entries.getJSONObject(0).has("keyword")) // partner
        assertFalse(entries.getJSONObject(1).has("keyword")) // solo without one: no key, not null
        val withKeyword = entries.getJSONObject(2)
        assertEquals(
            setOf("timestamp", "type", "durationMinutes", "moodBefore", "moodAfter", "pleasure", "note", "keyword"),
            withKeyword.keys().asSequence().toSet(),
        )
        assertEquals("Mia 小蓝 😀", withKeyword.getString("keyword"))
        assertFalse(BackupCodec.encode(listOf(testEntry(timestamp = 1)), 0).contains("keyword"))
    }

    @Test
    fun encodedDocumentNeverContainsIdsOrSecrets() {
        val text = BackupCodec.encode(sample, 0)
        assertFalse(text.contains("\"id\""))
        assertFalse(text.contains("pin", ignoreCase = true))
        assertFalse(text.contains("apiKey", ignoreCase = true))
        assertFalse(text.contains("salt", ignoreCase = true))
        assertFalse(text.contains("theme", ignoreCase = true))
    }

    @Test
    fun encodingNoEntriesIsAllowedButDecodingItReportsEmpty() {
        val text = BackupCodec.encode(emptyList(), 0)
        assertEquals(0, JSONObject(text).getJSONArray("entries").length())
        assertEquals(BackupError.EMPTY, errorOf(text))
    }

    // ---- not a backup / malformed -------------------------------------------------------------

    @Test
    fun notJsonIsInvalidFormat() {
        assertEquals(BackupError.INVALID_FORMAT, errorOf("this is not json"))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(""))
        assertEquals(BackupError.INVALID_FORMAT, errorOf("{\"app\":"))
    }

    @Test
    fun jsonArrayAtTheTopLevelIsInvalidFormat() {
        assertEquals(BackupError.INVALID_FORMAT, errorOf("[]"))
    }

    @Test
    fun wrongOrMissingAppMarkerIsInvalidFormat() {
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("app", "someotherapp") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.remove("app") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("app", 5) })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("app", "BoysDairy") })))
    }

    @Test
    fun missingOrMalformedFormatVersionIsInvalidFormat() {
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.remove("formatVersion") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("formatVersion", "1") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("formatVersion", 0) })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("formatVersion", -3) })))
    }

    @Test
    fun missingOrNonArrayEntriesIsInvalidFormat() {
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.remove("entries") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("entries", "nope") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("entries", JSONObject()) })))
    }

    @Test
    fun entryThatIsNotAnObjectIsInvalidFormat() {
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("entries", JSONArray().put(5)) })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(root = { it.put("entries", JSONArray().put("x")) })))
    }

    @Test
    fun everyMissingEntryFieldIsInvalidFormat() {
        val fields = listOf("timestamp", "type", "durationMinutes", "moodBefore", "moodAfter", "pleasure", "note")
        for (field in fields) {
            assertEquals("missing $field", BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.remove(field) })))
        }
    }

    @Test
    fun nullEntryFieldsAreInvalidFormat() {
        val fields = listOf("timestamp", "type", "durationMinutes", "moodBefore", "moodAfter", "pleasure", "note")
        for (field in fields) {
            assertEquals(
                "null $field",
                BackupError.INVALID_FORMAT,
                errorOf(document(mutate = { it.put(field, JSONObject.NULL) })),
            )
        }
    }

    @Test
    fun unknownEnumNamesAreInvalidFormat() {
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.put("type", "TRIO") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.put("moodBefore", "FURIOUS") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.put("moodAfter", "") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.put("type", "solo") })))
    }

    @Test
    fun wronglyTypedFieldsAreInvalidFormat() {
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.put("timestamp", "1760000000000") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.put("pleasure", "7") })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.put("durationMinutes", true) })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.put("note", 5) })))
        assertEquals(BackupError.INVALID_FORMAT, errorOf(document(mutate = { it.put("type", 1) })))
    }

    @Test
    fun oneBadEntryAmongGoodOnesRejectsTheWholeFile() {
        val good = JSONObject(document()).getJSONArray("entries").getJSONObject(0)
        val bad = JSONObject(good.toString()).put("type", "NOPE")
        val text = JSONObject(document()).put("entries", JSONArray().put(good).put(bad).put(good)).toString()
        assertEquals(BackupError.INVALID_FORMAT, errorOf(text))
    }

    // ---- versions / empty ---------------------------------------------------------------------

    @Test
    fun newerFormatVersionIsUnsupported() {
        assertEquals(BackupError.UNSUPPORTED_VERSION, errorOf(document(root = { it.put("formatVersion", 3) })))
        assertEquals(BackupError.UNSUPPORTED_VERSION, errorOf(document(root = { it.put("formatVersion", 999) })))
    }

    @Test
    fun unsupportedVersionWinsOverEntryProblems() {
        // A future format may legitimately change the entry shape.
        val text = document(root = { it.put("formatVersion", 3).put("entries", JSONArray().put(JSONObject().put("x", 1))) })
        assertEquals(BackupError.UNSUPPORTED_VERSION, errorOf(text))
    }

    @Test
    fun emptyEntriesArrayIsEmpty() {
        assertEquals(BackupError.EMPTY, errorOf(document(root = { it.put("entries", JSONArray()) })))
    }

    // ---- lenience -----------------------------------------------------------------------------

    @Test
    fun unknownExtraKeysAreIgnored() {
        val entries = BackupCodec.decode(
            document(
                root = { it.put("futureField", "x") },
                mutate = { it.put("extra", 1) },
            ),
        )
        assertEquals(1, entries.size)
    }

    @Test
    fun anyMoodIsAcceptedForBeforeAndAfter() {
        val entries = BackupCodec.decode(
            document(mutate = { it.put("moodBefore", "ENERGETIC").put("moodAfter", "ANXIOUS") }),
        )
        assertEquals(Mood.ENERGETIC, entries.single().moodBefore)
        assertEquals(Mood.ANXIOUS, entries.single().moodAfter)
    }

    @Test
    fun decodedEntriesAlwaysHaveIdZero() {
        val decoded: List<Entry> = BackupCodec.decode(BackupCodec.encode(sample, 0))
        assertTrue(decoded.all { it.id == 0L })
    }

    // ---- keyword (format 2) -------------------------------------------------------------------

    @Test
    fun versionOneBackupWithoutKeywordsStillImports() {
        // The exact shape version 1 of the app wrote.
        val v1 = """
            {"app":"boysdairy","formatVersion":1,"exportedAt":5,"entries":[
              {"timestamp":1760000000000,"type":"SOLO","durationMinutes":12,"moodBefore":"STRESSED",
               "moodAfter":"RELAXED","pleasure":7,"note":"hi"},
              {"timestamp":1760000100000,"type":"PARTNER","durationMinutes":30,"moodBefore":"EXCITED",
               "moodAfter":"ENERGETIC","pleasure":9,"note":""}]}
        """.trimIndent()
        val entries = BackupCodec.decode(v1)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.keyword == null })
        assertEquals(testEntry(1_760_000_000_000L, EntryType.SOLO, 12, Mood.STRESSED, Mood.RELAXED, 7, "hi"), entries[0])
    }

    @Test
    fun versionTwoRoundTripKeepsKeywords() {
        val entries = listOf(
            testEntry(timestamp = 3, keyword = "Mia"),
            testEntry(timestamp = 2, keyword = null),
            testEntry(timestamp = 1, type = EntryType.PARTNER),
        )
        val text = BackupCodec.encode(entries, 0)
        assertEquals(2, JSONObject(text).getInt("formatVersion"))
        assertEquals(entries, BackupCodec.decode(text))
    }

    @Test
    fun missingOrNullKeywordIsNone() {
        val v2 = { mutate: (JSONObject) -> Unit -> document(root = { it.put("formatVersion", 2) }, mutate = mutate) }
        assertNull(BackupCodec.decode(v2 {}).single().keyword)
        assertNull(BackupCodec.decode(v2 { it.put("keyword", JSONObject.NULL) }).single().keyword)
        assertEquals("Mia", BackupCodec.decode(v2 { it.put("keyword", "Mia") }).single().keyword)
    }

    @Test
    fun importedKeywordsAreNormalisedLikeTheForm() {
        fun keyword(type: String, value: String) = BackupCodec.decode(
            document(root = { it.put("formatVersion", 2) }, mutate = { it.put("type", type).put("keyword", value) }),
        ).single().keyword

        assertEquals("Mia", keyword("SOLO", "  Mia \n"))
        assertNull(keyword("SOLO", ""))
        assertNull(keyword("SOLO", "   "))
        assertNull(keyword("PARTNER", "Mia")) // partner entries never keep one
        // Not shortened: the 30-character limit only applies to typing in the form.
        assertEquals("x".repeat(80), keyword("SOLO", "x".repeat(80)))
    }

    @Test
    fun aKeywordThatIsNotAStringIsInvalidFormat() {
        for (bad in listOf<Any>(5, true, JSONObject(), JSONArray().put("Mia"))) {
            assertEquals(
                "keyword $bad",
                BackupError.INVALID_FORMAT,
                errorOf(document(root = { it.put("formatVersion", 2) }, mutate = { it.put("keyword", bad) })),
            )
        }
    }

    @Test
    fun keywordIsOptionalInEitherVersion() {
        // Version 1 never wrote it, but a keyword found there is read the same way.
        assertEquals("Mia", BackupCodec.decode(document(mutate = { it.put("keyword", "Mia") })).single().keyword)
        assertNull(BackupCodec.decode(document(root = { it.put("formatVersion", 2) })).single().keyword)
    }

    // ---- clamping -----------------------------------------------------------------------------

    @Test
    fun pleasureIsClampedToOneToTen() {
        fun pleasure(value: Int) =
            BackupCodec.decode(document(mutate = { it.put("pleasure", value) })).single().pleasure

        assertEquals(1, pleasure(0))
        assertEquals(1, pleasure(-5))
        assertEquals(1, pleasure(1))
        assertEquals(10, pleasure(10))
        assertEquals(10, pleasure(11))
        assertEquals(10, pleasure(99))
        assertEquals(10, pleasure(Int.MAX_VALUE))
    }

    @Test
    fun durationIsClampedToOneToSixHundred() {
        fun duration(value: Long) =
            BackupCodec.decode(document(mutate = { it.put("durationMinutes", value) })).single().durationMinutes

        assertEquals(1, duration(0))
        assertEquals(1, duration(-20))
        assertEquals(1, duration(1))
        assertEquals(600, duration(600))
        assertEquals(600, duration(601))
        assertEquals(600, duration(100_000))
        assertEquals(600, duration(Long.MAX_VALUE))
    }

    @Test
    fun fractionalNumbersAreTruncatedThenClamped() {
        val entry = BackupCodec.decode(
            document(mutate = { it.put("pleasure", 7.9).put("durationMinutes", 12.2) }),
        ).single()
        assertEquals(7, entry.pleasure)
        assertEquals(12, entry.durationMinutes)
    }
}
