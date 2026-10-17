package com.rookierc.boysdairy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rookierc.boysdairy.domain.AbstinenceGap
import com.rookierc.boysdairy.domain.GapExclusion
import com.rookierc.boysdairy.domain.toExclusion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Gaps the user marked as "does not count" on the achievement wall (see [GapExclusion]), kept in
 * the `settings` DataStore as a string set of [GapExclusion.encode] values. Derived from diary
 * dates, so it is cleared together with the entries ("delete all entries"); never written into a
 * backup and never sent to an AI provider.
 */
class GapExclusionStore(private val dataStore: DataStore<Preferences>) {

    /** Current exclusions; malformed stored values are skipped. */
    val exclusions: Flow<Set<GapExclusion>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[KEY_EXCLUDED_GAPS].orEmpty().mapNotNullTo(HashSet()) { GapExclusion.decode(it) } }
        .distinctUntilChanged()

    /** Takes [gap] out of the count. */
    suspend fun exclude(gap: AbstinenceGap) {
        val value = gap.toExclusion().encode()
        dataStore.edit { prefs -> prefs[KEY_EXCLUDED_GAPS] = prefs[KEY_EXCLUDED_GAPS].orEmpty() + value }
    }

    /** Counts [gap] again: drops every stored exclusion that matches it (and any malformed value). */
    suspend fun restore(gap: AbstinenceGap) {
        dataStore.edit { prefs ->
            val kept = prefs[KEY_EXCLUDED_GAPS].orEmpty().filterTo(HashSet()) { raw ->
                GapExclusion.decode(raw)?.matches(gap) == false
            }
            if (kept.isEmpty()) prefs.remove(KEY_EXCLUDED_GAPS) else prefs[KEY_EXCLUDED_GAPS] = kept
        }
    }

    /** Forgets every exclusion. */
    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_EXCLUDED_GAPS) }
    }

    private companion object {
        // A persistence key that must never be renamed.
        val KEY_EXCLUDED_GAPS = stringSetPreferencesKey("excludedGaps")
    }
}
