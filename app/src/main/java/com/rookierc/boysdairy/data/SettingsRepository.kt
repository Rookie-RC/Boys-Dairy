package com.rookierc.boysdairy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rookierc.boysdairy.ai.AiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Salted PIN hash as stored on disk: Base64 salt and lower-case hex SHA-256. */
data class PinCredentials(val saltBase64: String, val hashHex: String)

/** Single app-wide DataStore instance (must be a top-level delegate). */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persistent settings. The interface language is intentionally not stored here: it is owned by
 * AppCompatDelegate's per-app locale support.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val themeMode: Flow<ThemeMode> = preferences.map { prefs ->
        val stored = prefs[KEY_THEME_MODE]
        ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    /** The provider the AI tab talks to; DeepSeek until the user picks another. Not secret, not backed up. */
    val aiProvider: Flow<AiProvider> = preferences
        .map { prefs -> providerOf(prefs) }
        .distinctUntilChanged()

    suspend fun setAiProvider(provider: AiProvider) {
        dataStore.edit { prefs -> prefs[KEY_AI_PROVIDER] = provider.id }
    }

    /**
     * The boy / girl edition of the interface; `null` until the user picks one (an unknown stored
     * value counts as not picked). A UI preference, not diary content: never written into a
     * backup, never sent to an AI provider, and "delete all entries" leaves it alone.
     */
    val edition: Flow<Edition?> = preferences
        .map { prefs -> Edition.fromId(prefs[KEY_EDITION]) }
        .distinctUntilChanged()

    suspend fun setEdition(edition: Edition) {
        dataStore.edit { prefs -> prefs[KEY_EDITION] = edition.id }
    }

    /**
     * When the diary was last exported successfully (epoch millis, wall clock); `null` = never.
     * A setting that drives the backup reminder, not diary content: never written into a backup.
     */
    val lastExportAt: Flow<Long?> = preferences
        .map { prefs -> prefs[KEY_LAST_EXPORT_AT] }
        .distinctUntilChanged()

    suspend fun setLastExportAt(epochMillis: Long) {
        dataStore.edit { prefs -> prefs[KEY_LAST_EXPORT_AT] = epochMillis }
    }

    /**
     * The home screen's backup reminder stays hidden until this moment (epoch millis, wall clock);
     * `null` = never snoozed. Not backed up.
     */
    val backupReminderSnoozedUntil: Flow<Long?> = preferences
        .map { prefs -> prefs[KEY_BACKUP_REMINDER_SNOOZED_UNTIL] }
        .distinctUntilChanged()

    suspend fun setBackupReminderSnoozedUntil(epochMillis: Long) {
        dataStore.edit { prefs -> prefs[KEY_BACKUP_REMINDER_SNOOZED_UNTIL] = epochMillis }
    }

    suspend fun loadPinCredentials(): PinCredentials? {
        val prefs = preferences.first()
        val salt = prefs[KEY_PIN_SALT]
        val hash = prefs[KEY_PIN_HASH]
        return if (salt != null && hash != null) PinCredentials(salt, hash) else null
    }

    suspend fun savePinCredentials(credentials: PinCredentials) {
        dataStore.edit { prefs ->
            prefs[KEY_PIN_SALT] = credentials.saltBase64
            prefs[KEY_PIN_HASH] = credentials.hashHex
        }
    }

    private fun providerOf(prefs: Preferences): AiProvider =
        AiProvider.fromId(prefs[KEY_AI_PROVIDER]) ?: AiProvider.DEFAULT

    private companion object {
        // Holds an AiProvider.id, which is a stable persistence key.
        val KEY_AI_PROVIDER = stringPreferencesKey("aiProvider")

        val KEY_PIN_SALT = stringPreferencesKey("pinSalt")
        val KEY_PIN_HASH = stringPreferencesKey("pinHash")
        val KEY_THEME_MODE = stringPreferencesKey("themeMode")

        // Holds an Edition.id; a persistence key that must never be renamed.
        val KEY_EDITION = stringPreferencesKey("edition")

        // Backup reminder; both are persistence keys and must never be renamed.
        val KEY_LAST_EXPORT_AT = longPreferencesKey("lastExportAt")
        val KEY_BACKUP_REMINDER_SNOOZED_UNTIL = longPreferencesKey("backupReminderSnoozedUntil")
    }
}
