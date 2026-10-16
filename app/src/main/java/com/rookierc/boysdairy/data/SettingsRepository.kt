package com.rookierc.boysdairy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

    private companion object {
        val KEY_PIN_SALT = stringPreferencesKey("pinSalt")
        val KEY_PIN_HASH = stringPreferencesKey("pinHash")
        val KEY_THEME_MODE = stringPreferencesKey("themeMode")
    }
}
