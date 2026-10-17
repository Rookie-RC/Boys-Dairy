package com.rookierc.boysdairy.domain

import com.rookierc.boysdairy.testEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SoloKeywordTest {

    private fun solo(timestamp: Long, keyword: String?) =
        testEntry(timestamp = timestamp, type = EntryType.SOLO, keyword = keyword)

    private fun partner(timestamp: Long, keyword: String? = null) =
        testEntry(timestamp = timestamp, type = EntryType.PARTNER, keyword = keyword)

    // ---- normalizeKeyword ---------------------------------------------------------------------

    @Test
    fun soloKeywordsAreTrimmed() {
        assertEquals("Mia", normalizeKeyword("  Mia \n", EntryType.SOLO))
        assertEquals("小 蓝", normalizeKeyword("小 蓝", EntryType.SOLO))
    }

    @Test
    fun blankOrMissingKeywordIsNull() {
        assertNull(normalizeKeyword(null, EntryType.SOLO))
        assertNull(normalizeKeyword("", EntryType.SOLO))
        assertNull(normalizeKeyword(" \t\n ", EntryType.SOLO))
    }

    @Test
    fun partnerEntriesNeverKeepAKeyword() {
        assertNull(normalizeKeyword("Mia", EntryType.PARTNER))
        assertNull(normalizeKeyword(null, EntryType.PARTNER))
    }

    @Test
    fun normalizingNeverShortensALongKeyword() {
        val long = "x".repeat(80)
        assertEquals(long, normalizeKeyword(" $long ", EntryType.SOLO))
    }

    @Test
    fun keywordKeyIgnoresCaseAndSurroundingBlanks() {
        assertEquals(keywordKey("mia"), keywordKey("  MIA "))
        assertEquals(keywordKey("Ünal"), keywordKey("ünal"))
        assertTrue(keywordKey("mia") != keywordKey("m ia"))
    }

    // ---- keywordCounts / topKeywords ----------------------------------------------------------

    @Test
    fun countsMergeSpellingsAndShowTheLatestOne() {
        val entries = listOf(
            solo(300, "MIA"),
            solo(100, "mia"),
            solo(200, " Mia "),
            solo(150, "Lena"),
        )
        assertEquals(
            listOf(KeywordCount("MIA", 3, 300), KeywordCount("Lena", 1, 150)),
            keywordCounts(entries),
        )
    }

    @Test
    fun latestSpellingIsByTimestampNotByListOrder() {
        // Oldest first on purpose: the newest use comes last in the list.
        val entries = listOf(solo(100, "mia"), solo(200, "Mia"), solo(300, "MIA!"), solo(400, "mIA"))
        assertEquals(KeywordCount("mIA", 3, 400), keywordCounts(entries).first { it.keyword.lowercase() == "mia" })
    }

    @Test
    fun twoUsesAtTheSameInstantKeepTheSpellingMetFirst() {
        assertEquals(listOf(KeywordCount("Mia", 2, 100)), keywordCounts(listOf(solo(100, "Mia"), solo(100, "mia"))))
    }

    @Test
    fun partnerEntriesAndBlankKeywordsAreIgnored() {
        val entries = listOf(
            partner(500, keyword = "Mia"), // cannot happen through the form or import, ignored anyway
            solo(400, null),
            solo(300, "   "),
            solo(200, "Mia"),
        )
        assertEquals(listOf(KeywordCount("Mia", 1, 200)), keywordCounts(entries))
        assertTrue(keywordCounts(listOf(solo(1, null), partner(2))).isEmpty())
        assertTrue(keywordCounts(emptyList()).isEmpty())
    }

    @Test
    fun mostUsedFirstThenMostRecentlyUsed() {
        val entries = listOf(
            solo(900, "a"),
            solo(800, "b"),
            solo(700, "b"),
            solo(600, "c"),
            solo(500, "c"),
            solo(100, "d"),
            solo(90, "d"),
            solo(80, "d"),
        )
        // d is used most; b and c tie on 2, b was used more recently.
        assertEquals(listOf("d", "b", "c", "a"), keywordCounts(entries).map { it.keyword })
    }

    @Test
    fun fullTiesKeepTheOrderOfTheList() {
        val entries = listOf(solo(100, "x"), solo(100, "y"), solo(100, "z"))
        assertEquals(listOf("x", "y", "z"), keywordCounts(entries).map { it.keyword })
    }

    @Test
    fun topKeywordsIsCappedAtTheLimit() {
        val entries = (1..8).map { solo(it.toLong(), "k$it") }
        val top = topKeywords(entries, limit = 5)
        assertEquals(5, top.size)
        // All used once: most recent first.
        assertEquals(listOf("k8", "k7", "k6", "k5", "k4"), top.map { it.keyword })
        assertEquals(entries.size, topKeywords(entries, limit = 50).size)
        assertTrue(topKeywords(entries, limit = 0).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { topKeywords(entries, limit = -1) }
    }

    // ---- recentKeywords -----------------------------------------------------------------------

    @Test
    fun recentKeywordsAreDistinctMostRecentFirstInTheLatestSpelling() {
        val entries = listOf(
            solo(600, "Mia"),
            partner(550),
            solo(500, "lena"),
            solo(400, "MIA"),
            solo(300, "Zoe"),
            solo(200, "LENA"),
        )
        assertEquals(listOf("Mia", "lena", "Zoe"), recentKeywords(entries, limit = 5))
    }

    @Test
    fun recentKeywordsIgnoreHowOftenOneWasUsed() {
        val entries = listOf(solo(900, "new"), solo(300, "old"), solo(200, "old"), solo(100, "old"))
        assertEquals(listOf("new", "old"), recentKeywords(entries, limit = 5))
    }

    @Test
    fun recentKeywordsAreCappedAndSortedByTimestamp() {
        // Not newest first on purpose.
        val entries = listOf(3L, 7L, 1L, 6L, 2L, 5L, 4L).map { solo(it, "k$it") }
        assertEquals(listOf("k7", "k6", "k5", "k4", "k3"), recentKeywords(entries, limit = 5))
        assertTrue(recentKeywords(emptyList(), limit = 5).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { recentKeywords(entries, limit = -1) }
    }

    @Test
    fun recentKeywordsAtTheSameInstantKeepTheListOrder() {
        val entries = listOf(solo(100, "b"), solo(100, "a"), solo(100, "b"))
        assertEquals(listOf("b", "a"), recentKeywords(entries, limit = 5))
    }
}
