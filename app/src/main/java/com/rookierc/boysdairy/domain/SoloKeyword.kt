package com.rookierc.boysdairy.domain

import java.util.Locale

/*
 * The optional one-word keyword of solo entries ("today's muse"). Pure rules shared by the entry
 * form, backup import, the history search, the form's suggestions and the full report.
 *
 * Spellings that differ only in letter case or surrounding blanks count as the same keyword
 * ([keywordKey]); wherever one is shown, it is the spelling of its most recent use.
 */

/**
 * The keyword to keep for an entry of [type]: [raw] trimmed, `null` when that leaves nothing, and
 * always `null` for anything but [EntryType.SOLO]. Never shortens it: the length limit is a rule
 * of the form's input, not of stored data.
 */
fun normalizeKeyword(raw: String?, type: EntryType): String? {
    if (type != EntryType.SOLO) return null
    return raw?.trim()?.takeIf { it.isNotEmpty() }
}

/** What spellings of one keyword are merged under: trimmed, letter case folded. */
fun keywordKey(keyword: String): String = keyword.trim().lowercase(Locale.ROOT)

/**
 * One keyword of [keywordCounts].
 *
 * @property keyword the (trimmed) spelling of its most recent use.
 * @property count solo entries that used it, in any spelling.
 * @property lastUsedMillis timestamp of the most recent of them.
 */
data class KeywordCount(
    val keyword: String,
    val count: Int,
    val lastUsedMillis: Long,
)

/**
 * Every keyword of the solo entries in [entries] (other types and blank keywords are ignored),
 * merged by [keywordKey]. Most used first; equal counts: most recently used first; still equal:
 * the one met first in [entries].
 */
fun keywordCounts(entries: List<Entry>): List<KeywordCount> =
    // Stable sort: full ties keep the order in which the keywords were first met.
    groupKeywords(entries).sortedWith(
        compareByDescending<KeywordCount> { it.count }.thenByDescending { it.lastUsedMillis },
    )

/** The [limit] most used keywords of [entries] (see [keywordCounts]); the full report's leaderboard. */
fun topKeywords(entries: List<Entry>, limit: Int): List<KeywordCount> {
    require(limit >= 0) { "limit must not be negative" }
    return keywordCounts(entries).take(limit)
}

/**
 * Up to [limit] distinct keywords of the solo entries in [entries], most recently used first (by
 * entry timestamp; equal: the one met first in [entries]), each in the spelling of its latest
 * use. The form offers them as one-tap suggestions.
 */
fun recentKeywords(entries: List<Entry>, limit: Int): List<String> {
    require(limit >= 0) { "limit must not be negative" }
    return groupKeywords(entries)
        .sortedByDescending { it.lastUsedMillis }
        .take(limit)
        .map { it.keyword }
}

/**
 * Solo keywords merged by [keywordKey], in the order they are first met in [entries]. Of two uses
 * at the same instant, the spelling met first is kept.
 */
private fun groupKeywords(entries: List<Entry>): Collection<KeywordCount> {
    val byKey = LinkedHashMap<String, KeywordCount>()
    for (entry in entries) {
        if (entry.type != EntryType.SOLO) continue
        val keyword = entry.keyword?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val key = keywordKey(keyword)
        val known = byKey[key]
        byKey[key] = when {
            known == null -> KeywordCount(keyword, 1, entry.timestamp)
            entry.timestamp > known.lastUsedMillis -> KeywordCount(keyword, known.count + 1, entry.timestamp)
            else -> known.copy(count = known.count + 1)
        }
    }
    return byKey.values
}
