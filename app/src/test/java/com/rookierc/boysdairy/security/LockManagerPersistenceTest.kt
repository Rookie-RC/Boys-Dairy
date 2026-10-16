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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * End-to-end check of the persistent lockout with a real Preferences DataStore on disk: a new
 * [LockManager] built on the same file (a "process restart") must still be locked out.
 */
class LockManagerPersistenceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clock = 1_000_000L
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") }
        settings = SettingsRepository(dataStore)
        val salt = PinHasher.generateSalt()
        runBlocking {
            settings.savePinCredentials(PinCredentials(PinHasher.encodeSalt(salt), PinHasher.hash(salt, CORRECT_PIN)))
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** Builds what AppContainer builds; each call is a fresh "process" sharing the same files. */
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
    fun wrongPinsBeforeForceStopStillCountAfterRestart() = runBlocking {
        val first = startProcess()
        repeat(3) { first.verifyPin(WRONG_PIN) }

        val second = startProcess()
        assertEquals(PinCheckResult.Wrong(attemptsLeft = 1), second.verifyPin(WRONG_PIN))
        assertTrue(second.verifyPin(WRONG_PIN) is PinCheckResult.LockedOut)
    }

    @Test
    fun lockoutSurvivesForceStop() = runBlocking {
        val first = startProcess()
        repeat(4) { first.verifyPin(WRONG_PIN) }
        assertEquals(PinCheckResult.LockedOut(30_000L), first.verifyPin(WRONG_PIN))

        clock += 5_000L
        val second = startProcess() // force-stop + relaunch
        assertEquals(25_000L, second.lockoutRemainingMillis())
        // Even the correct PIN is refused while locked out.
        assertEquals(PinCheckResult.LockedOut(25_000L), second.unlock(CORRECT_PIN))
        assertEquals(LockState.Locked, second.state.value)

        clock += 25_000L
        assertEquals(0L, second.lockoutRemainingMillis())
        assertEquals(PinCheckResult.Success, second.unlock(CORRECT_PIN))
        assertEquals(LockState.Unlocked, second.state.value)
    }

    @Test
    fun correctPinClearsThePersistedFailures() = runBlocking {
        val first = startProcess()
        repeat(3) { first.verifyPin(WRONG_PIN) }
        assertEquals(PinCheckResult.Success, first.verifyPin(CORRECT_PIN))

        assertNull(DataStorePinLockoutStore(dataStore).read())
        val second = startProcess()
        assertEquals(PinCheckResult.Wrong(attemptsLeft = 4), second.verifyPin(WRONG_PIN))
    }

    @Test
    fun clockSetBackAfterLockoutDoesNotShortenIt() = runBlocking {
        val first = startProcess()
        repeat(5) { first.verifyPin(WRONG_PIN) }

        clock -= 600_000L
        val second = startProcess()
        assertEquals(30_000L, second.lockoutRemainingMillis())
        assertTrue(second.verifyPin(CORRECT_PIN) is PinCheckResult.LockedOut)
    }

    @Test
    fun lockoutIsAlreadyKnownWhenTheLockedStateIsPublished() = runBlocking {
        val first = startProcess()
        repeat(5) { first.verifyPin(WRONG_PIN) }

        val second = startProcess()
        // The UI starts its countdown as soon as state == Locked; the value must already be right.
        assertEquals(LockState.Locked, second.state.value)
        assertEquals(30_000L, second.lockoutRemainingMillis())
    }

    private companion object {
        const val CORRECT_PIN = "1234"
        const val WRONG_PIN = "0000"
    }
}
