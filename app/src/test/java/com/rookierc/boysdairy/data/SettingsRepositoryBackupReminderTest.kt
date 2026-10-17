package com.rookierc.boysdairy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
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

/** The backup-reminder timestamps (`lastExportAt`, `backupReminderSnoozedUntil`) on a real Preferences DataStore file. */
class SettingsRepositoryBackupReminderTest {

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

    private fun key(name: String) = longPreferencesKey(name)

    @Test
    fun bothDefaultToNull() = runBlocking {
        assertNull(settings.lastExportAt.first())
        assertNull(settings.backupReminderSnoozedUntil.first())
    }

    @Test
    fun lastExportAtIsPersistedUnderItsStableKey() = runBlocking {
        settings.setLastExportAt(1_790_000_000_000L)

        assertEquals(1_790_000_000_000L, settings.lastExportAt.first())
        assertEquals(1_790_000_000_000L, prefs()[key("lastExportAt")])
    }

    @Test
    fun snoozedUntilIsPersistedUnderItsStableKey() = runBlocking {
        settings.setBackupReminderSnoozedUntil(1_790_600_000_000L)

        assertEquals(1_790_600_000_000L, settings.backupReminderSnoozedUntil.first())
        assertEquals(1_790_600_000_000L, prefs()[key("backupReminderSnoozedUntil")])
    }

    @Test
    fun theTwoKeysAreIndependent() = runBlocking {
        settings.setLastExportAt(111L)

        assertNull(settings.backupReminderSnoozedUntil.first())

        settings.setBackupReminderSnoozedUntil(222L)

        assertEquals(111L, settings.lastExportAt.first())
        assertEquals(222L, settings.backupReminderSnoozedUntil.first())
    }

    @Test
    fun aNewerValueReplacesTheOldOne() = runBlocking {
        settings.setLastExportAt(1_000L)
        settings.setLastExportAt(2_000L)
        settings.setBackupReminderSnoozedUntil(3_000L)
        settings.setBackupReminderSnoozedUntil(4_000L)

        assertEquals(2_000L, settings.lastExportAt.first())
        assertEquals(4_000L, settings.backupReminderSnoozedUntil.first())
    }

    @Test
    fun valuesSurviveANewRepositoryOverTheSameFile() = runBlocking {
        settings.setLastExportAt(5_000L)
        settings.setBackupReminderSnoozedUntil(6_000L)

        val reopened = SettingsRepository(dataStore)
        assertEquals(5_000L, reopened.lastExportAt.first())
        assertEquals(6_000L, reopened.backupReminderSnoozedUntil.first())
    }

    @Test
    fun otherSettingsAreUntouched() = runBlocking {
        settings.setThemeMode(ThemeMode.DARK)
        settings.setAiProvider(AiProvider.CLAUDE)
        settings.setLastExportAt(7_000L)
        settings.setBackupReminderSnoozedUntil(8_000L)

        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        assertEquals(AiProvider.CLAUDE, settings.aiProvider.first())
    }
}
