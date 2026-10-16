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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The saved AI provider choice on a real Preferences DataStore file. */
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
}
