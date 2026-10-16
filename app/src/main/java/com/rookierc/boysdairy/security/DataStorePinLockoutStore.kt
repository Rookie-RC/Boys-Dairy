package com.rookierc.boysdairy.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first
import java.io.IOException

/**
 * [PinLockoutStore] backed by Preferences DataStore. I/O failures are swallowed (an unreadable
 * record counts as "nothing stored", a failed write is dropped): the limiter keeps enforcing
 * the lockout from memory, and the PIN screen must never crash because of storage trouble.
 */
class DataStorePinLockoutStore(private val dataStore: DataStore<Preferences>) : PinLockoutStore {

    override suspend fun read(): PinLockoutState? {
        val prefs = try {
            dataStore.data.first()
        } catch (_: IOException) {
            return null
        }
        val failures = prefs[KEY_FAILURES]
        val lockedUntil = prefs[KEY_LOCKED_UNTIL]
        val updatedAt = prefs[KEY_UPDATED_AT]
        if (failures == null && lockedUntil == null) return null
        return PinLockoutState(
            failures = failures ?: 0,
            lockedUntilMillis = lockedUntil ?: 0L,
            updatedAtMillis = updatedAt ?: 0L,
        )
    }

    override suspend fun write(state: PinLockoutState?) {
        try {
            dataStore.edit { prefs ->
                if (state == null) {
                    prefs.remove(KEY_FAILURES)
                    prefs.remove(KEY_LOCKED_UNTIL)
                    prefs.remove(KEY_UPDATED_AT)
                } else {
                    prefs[KEY_FAILURES] = state.failures
                    prefs[KEY_LOCKED_UNTIL] = state.lockedUntilMillis
                    prefs[KEY_UPDATED_AT] = state.updatedAtMillis
                }
            }
        } catch (_: IOException) {
            // Best effort; see class docs.
        }
    }

    private companion object {
        val KEY_FAILURES = intPreferencesKey("pinFailures")
        val KEY_LOCKED_UNTIL = longPreferencesKey("pinLockedUntilMillis")
        val KEY_UPDATED_AT = longPreferencesKey("pinLockoutUpdatedAtMillis")
    }
}
