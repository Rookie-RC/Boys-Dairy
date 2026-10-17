package com.rookierc.boysdairy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rookierc.boysdairy.domain.AbstinenceGap
import com.rookierc.boysdairy.domain.GapExclusion
import com.rookierc.boysdairy.domain.toExclusion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

/** Gaps marked "does not count", on a real Preferences DataStore file shared with the settings. */
class GapExclusionStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: GapExclusionStore

    // The stored key must never be renamed.
    private val key = stringSetPreferencesKey("excludedGaps")

    private val closed = AbstinenceGap(date(10, 1), date(10, 9), 8)
    private val later = AbstinenceGap(date(10, 9), date(10, 16), 7)

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") }
        store = GapExclusionStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun date(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private fun prefs(): Preferences = runBlocking { dataStore.data.first() }

    @Test
    fun nothingIsExcludedInitially() = runBlocking {
        assertEquals(emptySet<GapExclusion>(), store.exclusions.first())
        assertFalse(prefs().contains(key))
    }

    @Test
    fun anExcludedGapIsStoredByItsEncodedForm() = runBlocking {
        store.exclude(closed)

        assertEquals(setOf(GapExclusion(date(10, 1), date(10, 9))), store.exclusions.first())
        assertEquals(setOf("2026-10-01/2026-10-09"), prefs()[key])
        assertEquals(setOf(closed.toExclusion().encode()), prefs()[key])
    }

    @Test
    fun anOpenGapIsStoredUpToTheDayItWasExcluded() = runBlocking {
        // Open since 1 Oct, excluded on day 15 (16 Oct).
        store.exclude(AbstinenceGap(date(10, 1), null, 15))

        assertEquals(setOf("2026-10-01/2026-10-16+"), prefs()[key])
        assertEquals(setOf(GapExclusion(date(10, 1), date(10, 16), orLater = true)), store.exclusions.first())
    }

    @Test
    fun excludingAddsToTheSetAndTheSameGapIsStoredOnce() = runBlocking {
        store.exclude(closed)
        store.exclude(later)
        store.exclude(closed)

        assertEquals(setOf("2026-10-01/2026-10-09", "2026-10-09/2026-10-16"), prefs()[key])
        assertEquals(setOf(closed.toExclusion(), later.toExclusion()), store.exclusions.first())
    }

    @Test
    fun exclusionsSurviveANewStoreOverTheSameFile() = runBlocking {
        store.exclude(closed)

        assertEquals(setOf(closed.toExclusion()), GapExclusionStore(dataStore).exclusions.first())
    }

    @Test
    fun restoreDropsOnlyTheMatchingExclusion() = runBlocking {
        store.exclude(closed)
        store.exclude(later)

        store.restore(closed)

        assertEquals(setOf(later.toExclusion()), store.exclusions.first())
        assertEquals(setOf("2026-10-09/2026-10-16"), prefs()[key])
    }

    @Test
    fun restoringAClosedGapAlsoDropsTheOpenExclusionThatMatchesIt() = runBlocking {
        // Excluded on 9 Oct while still open, then again after the 9 Oct entry closed it.
        store.exclude(AbstinenceGap(date(10, 1), null, 8))
        store.exclude(closed)
        store.exclude(later)
        assertEquals(setOf("2026-10-01/2026-10-09+", "2026-10-01/2026-10-09", "2026-10-09/2026-10-16"), prefs()[key])

        store.restore(closed)

        assertEquals(setOf(later.toExclusion()), store.exclusions.first())
        assertEquals(setOf("2026-10-09/2026-10-16"), prefs()[key])
    }

    @Test
    fun restoreDropsAnOpenExclusionOnlyForGapsItStillMatches() = runBlocking {
        val openOn9th = AbstinenceGap(date(10, 1), null, 8) // stored as 2026-10-01/2026-10-09+

        // A backfilled 5 Oct entry split it: the 1-5 Oct gap was never excluded, nothing to drop.
        store.exclude(openOn9th)
        store.restore(AbstinenceGap(date(10, 1), date(10, 5), 4))
        assertEquals(setOf("2026-10-01/2026-10-09+"), prefs()[key])

        // Closed on 12 Oct, after the exclusion day: still matched, so restoring drops it.
        store.restore(AbstinenceGap(date(10, 1), date(10, 12), 11))
        assertFalse(prefs().contains(key))

        // While still open.
        store.exclude(openOn9th)
        store.restore(AbstinenceGap(date(10, 1), null, 15))
        assertFalse(prefs().contains(key))
    }

    @Test
    fun restoringAnOpenGapKeepsClosedExclusionsFromTheSameDay() = runBlocking {
        // A closed exclusion only ever matches that exact gap.
        store.exclude(closed)

        store.restore(AbstinenceGap(date(10, 1), null, 15))

        assertEquals(setOf(closed.toExclusion()), store.exclusions.first())
    }

    @Test
    fun restoringTheLastExclusionRemovesTheKey() = runBlocking {
        store.exclude(closed)
        store.exclude(later)

        store.restore(closed)
        assertTrue(prefs().contains(key))
        store.restore(later)

        assertEquals(emptySet<GapExclusion>(), store.exclusions.first())
        assertFalse(prefs().contains(key))
    }

    @Test
    fun restoreWithNothingStoredIsHarmless() = runBlocking {
        store.restore(closed)

        assertEquals(emptySet<GapExclusion>(), store.exclusions.first())
        assertFalse(prefs().contains(key))
    }

    @Test
    fun clearForgetsEveryExclusion() = runBlocking {
        store.exclude(closed)
        store.exclude(AbstinenceGap(date(10, 16), null, 1))

        store.clear()

        assertEquals(emptySet<GapExclusion>(), store.exclusions.first())
        assertFalse(prefs().contains(key))

        store.clear() // again, on nothing
        assertFalse(prefs().contains(key))
    }

    private val malformed =setOf("garbage", "", "2026-10-09/2026-10-01", "2026-10-01", "2026-10-01/")

    @Test
    fun malformedStoredValuesAreSkipped() = runBlocking {
        // "2026-10-01/" is the earlier open form, which no longer decodes.
        dataStore.edit { it[key] = malformed + "2026-10-01/2026-10-09" }

        assertEquals(setOf(closed.toExclusion()), store.exclusions.first())

        // Valid values can still be added next to them.
        store.exclude(later)
        assertEquals(setOf(closed.toExclusion(), later.toExclusion()), store.exclusions.first())
    }

    @Test
    fun restoreAlsoDropsMalformedValues() = runBlocking {
        dataStore.edit { it[key] = malformed + "2026-10-01/2026-10-09" + "2026-10-09/2026-10-16" }

        store.restore(closed)

        assertEquals(setOf("2026-10-09/2026-10-16"), prefs()[key])
        assertEquals(setOf(later.toExclusion()), store.exclusions.first())
    }

    @Test
    fun restoreRemovesTheKeyWhenOnlyMalformedValuesWereLeft() = runBlocking {
        dataStore.edit { it[key] = malformed }

        store.restore(closed) // matches nothing stored

        assertFalse(prefs().contains(key))
        assertEquals(emptySet<GapExclusion>(), store.exclusions.first())
    }

    @Test
    fun otherSettingsInTheSameFileAreUntouched() = runBlocking {
        val settings = SettingsRepository(dataStore)
        settings.setThemeMode(ThemeMode.DARK)
        settings.setEdition(Edition.GIRL)

        store.exclude(closed)
        store.restore(closed)
        store.exclude(later)
        store.clear()

        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        assertEquals(Edition.GIRL, settings.edition.first())
        assertTrue(prefs().asMap().keys.none { it.name == "excludedGaps" })
    }
}
