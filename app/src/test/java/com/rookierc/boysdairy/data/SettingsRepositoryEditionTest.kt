package com.rookierc.boysdairy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rookierc.boysdairy.ai.AiProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The saved boy / girl edition on a real Preferences DataStore file. */
class SettingsRepositoryEditionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") }
        settings = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun prefs(): Preferences = runBlocking { dataStore.data.first() }

    private fun key(name: String) = stringPreferencesKey(name)

    @Test
    fun theEditionIsUnsetUntilChosen() = runBlocking {
        assertNull(settings.edition.first())
    }

    @Test
    fun theEditionIsPersistedByItsStableId() = runBlocking {
        settings.setEdition(Edition.GIRL)

        assertEquals(Edition.GIRL, settings.edition.first())
        assertEquals("girl", prefs()[key("edition")])

        settings.setEdition(Edition.BOY)
        assertEquals(Edition.BOY, settings.edition.first())
        assertEquals("boy", prefs()[key("edition")])
    }

    @Test
    fun theChoiceSurvivesANewRepositoryOverTheSameFile() = runBlocking {
        settings.setEdition(Edition.GIRL)

        assertEquals(Edition.GIRL, SettingsRepository(dataStore).edition.first())
    }

    @Test
    fun anUnknownStoredEditionCountsAsUnset() = runBlocking {
        dataStore.edit { it[key("edition")] = "robot" }

        assertNull(settings.edition.first())
    }

    @Test
    fun theIdsAreStable() {
        assertEquals(listOf("boy", "girl"), Edition.entries.map { it.id })
        assertEquals(Edition.BOY, Edition.fromId("boy"))
        assertEquals(Edition.GIRL, Edition.fromId("girl"))
        assertNull(Edition.fromId(null))
        assertNull(Edition.fromId("GIRL"))
    }

    @Test
    fun otherSettingsAreUntouched() = runBlocking {
        settings.setThemeMode(ThemeMode.DARK)
        settings.setAiProvider(AiProvider.CLAUDE)
        settings.setEdition(Edition.GIRL)

        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        assertEquals(AiProvider.CLAUDE, settings.aiProvider.first())
    }
}
