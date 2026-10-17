package com.rookierc.boysdairy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The saved AI provider choice and the "send my notes too" switch on a real Preferences DataStore file. */
class SettingsRepositoryAiTest {

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
    fun theProviderDefaultsToDeepSeek() = runBlocking {
        assertEquals(AiProvider.DEEPSEEK, settings.aiProvider.first())
    }

    @Test
    fun theProviderIsPersistedByItsStableId() = runBlocking {
        settings.setAiProvider(AiProvider.CLAUDE)

        assertEquals(AiProvider.CLAUDE, settings.aiProvider.first())
        assertEquals("claude", prefs()[key("aiProvider")])

        settings.setAiProvider(AiProvider.DEEPSEEK)
        assertEquals(AiProvider.DEEPSEEK, SettingsRepository(dataStore).aiProvider.first())
    }

    @Test
    fun theChoiceSurvivesANewRepositoryOverTheSameFile() = runBlocking {
        settings.setAiProvider(AiProvider.CLAUDE)

        assertEquals(AiProvider.CLAUDE, SettingsRepository(dataStore).aiProvider.first())
    }

    @Test
    fun anUnknownStoredProviderMeansTheDefault() = runBlocking {
        dataStore.edit { it[key("aiProvider")] = "gpt" }

        assertEquals(AiProvider.DEEPSEEK, settings.aiProvider.first())
    }

    @Test
    fun otherSettingsAreUntouched() = runBlocking {
        settings.setThemeMode(ThemeMode.DARK)
        settings.setAiProvider(AiProvider.CLAUDE)

        assertEquals(ThemeMode.DARK, settings.themeMode.first())
    }

    // ---- "Send my notes too" ----------------------------------------------------------------

    // The on-disk name: renaming it would silently turn the switch off for existing users.
    private val includeNotesKey = booleanPreferencesKey("aiIncludeNotes")

    @Test
    fun notesAreNotSentUntilTurnedOn() = runBlocking {
        assertFalse(settings.aiIncludeNotes.first())
        assertNull(prefs()[includeNotesKey])
    }

    @Test
    fun theNotesSwitchIsStoredUnderItsStableBooleanKey() = runBlocking {
        settings.setAiIncludeNotes(true)
        assertTrue(settings.aiIncludeNotes.first())
        assertEquals(true, prefs()[includeNotesKey])

        settings.setAiIncludeNotes(false)
        assertFalse(settings.aiIncludeNotes.first())
        assertEquals(false, prefs()[includeNotesKey])
    }

    @Test
    fun theNotesSwitchSurvivesANewRepositoryOverTheSameFile() = runBlocking {
        settings.setAiIncludeNotes(true)

        assertTrue(SettingsRepository(dataStore).aiIncludeNotes.first())
    }

    @Test
    fun theNotesSwitchLeavesTheProviderAlone() = runBlocking {
        settings.setAiProvider(AiProvider.CLAUDE)

        settings.setAiIncludeNotes(true)
        settings.setAiIncludeNotes(false)

        assertEquals(AiProvider.CLAUDE, settings.aiProvider.first())
    }
}
