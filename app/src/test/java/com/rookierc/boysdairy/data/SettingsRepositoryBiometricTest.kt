package com.rookierc.boysdairy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

/** The fingerprint-unlock switch on a real Preferences DataStore file. */
class SettingsRepositoryBiometricTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsRepository

    // The on-disk name: renaming it would silently turn the feature off for existing users.
    private val key = booleanPreferencesKey("biometricUnlock")

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

    @Test
    fun offUntilTurnedOn() = runBlocking {
        assertFalse(settings.biometricUnlock.first())
        assertNull(prefs()[key])
    }

    @Test
    fun storedUnderItsStableBooleanKey() = runBlocking {
        settings.setBiometricUnlock(true)
        assertTrue(settings.biometricUnlock.first())
        assertEquals(true, prefs()[key])

        settings.setBiometricUnlock(false)
        assertFalse(settings.biometricUnlock.first())
        assertEquals(false, prefs()[key])
    }

    @Test
    fun survivesANewRepositoryOverTheSameFile() = runBlocking {
        settings.setBiometricUnlock(true)

        assertTrue(SettingsRepository(dataStore).biometricUnlock.first())
    }

    @Test
    fun otherSettingsAreUntouched() = runBlocking {
        settings.setThemeMode(ThemeMode.DARK)
        settings.setAiProvider(AiProvider.CLAUDE)
        settings.setEdition(Edition.GIRL)
        settings.savePinCredentials(PinCredentials("salt", "hash"))

        settings.setBiometricUnlock(true)
        settings.setBiometricUnlock(false)

        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        assertEquals(AiProvider.CLAUDE, settings.aiProvider.first())
        assertEquals(Edition.GIRL, settings.edition.first())
        assertEquals(PinCredentials("salt", "hash"), settings.loadPinCredentials())
    }
}
