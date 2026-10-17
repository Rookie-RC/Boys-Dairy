package com.rookierc.boysdairy.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.rookierc.boysdairy.data.PinCredentials
import com.rookierc.boysdairy.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** State transitions of [LockManager.unlockWithBiometric] on a real Preferences DataStore. */
class LockManagerBiometricTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = 1_000_000L
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

    private fun storePin() = runBlocking {
        val salt = PinHasher.generateSalt()
        settings.savePinCredentials(PinCredentials(PinHasher.encodeSalt(salt), PinHasher.hash(salt, CORRECT_PIN)))
    }

    private suspend fun startProcess(): LockManager {
        val manager = LockManager(
            settings = settings,
            scope = scope,
            limiter = PinAttemptLimiter(store = DataStorePinLockoutStore(dataStore), now = { clock }),
        )
        withTimeout(10_000) { manager.state.first { it != LockState.Loading } }
        return manager
    }

    @Test
    fun lockedBecomesUnlocked() = runBlocking {
        storePin()
        val manager = startProcess()
        assertEquals(LockState.Locked, manager.state.value)

        assertTrue(manager.unlockWithBiometric())
        assertEquals(LockState.Unlocked, manager.state.value)
    }

    @Test
    fun neverSkipsFirstRunSetup() = runBlocking {
        val manager = startProcess()
        assertEquals(LockState.NeedsSetup, manager.state.value)

        assertFalse(manager.unlockWithBiometric())
        assertEquals(LockState.NeedsSetup, manager.state.value)
    }

    @Test
    fun doesNothingWhenAlreadyUnlocked() = runBlocking {
        storePin()
        val manager = startProcess()
        assertEquals(PinCheckResult.Success, manager.unlock(CORRECT_PIN))

        assertFalse(manager.unlockWithBiometric())
        assertEquals(LockState.Unlocked, manager.state.value)

        // And locking again works as before.
        manager.lock()
        assertEquals(LockState.Locked, manager.state.value)
    }

    @Test
    fun worksDuringAPinLockoutWithoutTouchingIt() = runBlocking {
        storePin()
        val manager = startProcess()
        repeat(5) { manager.verifyPin(WRONG_PIN) }
        assertEquals(30_000L, manager.lockoutRemainingMillis())

        assertTrue(manager.unlockWithBiometric())
        assertEquals(LockState.Unlocked, manager.state.value)

        // The PIN lockout and its persisted record are exactly as before.
        assertEquals(30_000L, manager.lockoutRemainingMillis())
        manager.lock()
        assertEquals(PinCheckResult.LockedOut(30_000L), manager.unlock(CORRECT_PIN))
        assertEquals(LockState.Locked, manager.state.value)
        assertEquals(clock + 30_000L, DataStorePinLockoutStore(dataStore).read()?.lockedUntilMillis)
    }

    @Test
    fun doesNotResetTheWrongPinCount() = runBlocking {
        storePin()
        val manager = startProcess()
        repeat(2) { manager.verifyPin(WRONG_PIN) }

        assertTrue(manager.unlockWithBiometric())
        manager.lock()

        assertEquals(PinCheckResult.Wrong(attemptsLeft = 2), manager.verifyPin(WRONG_PIN))
    }

    private companion object {
        const val CORRECT_PIN = "1234"
        const val WRONG_PIN = "0000"
    }
}
