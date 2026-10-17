package com.rookierc.boysdairy.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.rookierc.boysdairy.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

/**
 * [BiometricUnlock] on a real settings DataStore with a fake key store: software AES/GCM ciphers
 * instead of the AndroidKeyStore, and switches for "fingerprints changed" / "Keystore trouble".
 */
class BiometricUnlockTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settings: SettingsRepository
    private val keys = FakeKeys()
    private var capability = BiometricCapability.AVAILABLE
    private lateinit var biometric: BiometricUnlock

    @Before
    fun setUp() {
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "settings.preferences_pb") }
        settings = SettingsRepository(dataStore)
        biometric = BiometricUnlock(settings, keys, { capability }, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private suspend fun turnOn() {
        val cipher = notNull(biometric.prepareEnable())
        assertTrue(biometric.completeEnable(cipher))
    }

    private fun <T : Any> notNull(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    // ---- Off by default ------------------------------------------------------------------------

    @Test
    fun offByDefaultAndTheKeyIsNotTouched() = runBlocking {
        assertFalse(biometric.enabled.first())
        assertEquals(BiometricUnlockPreparation.NotOffered, biometric.prepareUnlock())
        assertEquals(0, keys.existingCalls)
        assertFalse(keys.hasKey)
    }

    // ---- Turning it on -------------------------------------------------------------------------

    @Test
    fun turningOnStoresNothingUntilTheFingerprintCheckWentThrough() = runBlocking {
        val cipher = notNull(biometric.prepareEnable())
        assertTrue(keys.hasKey)
        assertFalse(biometric.enabled.first())

        assertTrue(biometric.completeEnable(cipher))
        assertTrue(biometric.enabled.first())
        assertTrue(keys.hasKey)
        assertTrue(biometric.prepareUnlock() is BiometricUnlockPreparation.Ready)
    }

    @Test
    fun turningOnMakesAFreshKey() = runBlocking {
        turnOn()
        val firstGeneration = keys.generation
        biometric.disable()

        turnOn()
        assertEquals(firstGeneration + 1, keys.generation)
    }

    @Test
    fun aCancelledConfirmationLeavesItOffAndDeletesTheHalfMadeKey() = runBlocking {
        notNull(biometric.prepareEnable())

        biometric.abortEnable()

        assertFalse(biometric.enabled.first())
        assertFalse(keys.hasKey)
    }

    @Test
    fun aCheckTheKeystoreDidNotAuthoriseDoesNotTurnItOn() = runBlocking {
        notNull(biometric.prepareEnable())
        // An uninitialised cipher stands for one the Keystore refuses (no real authentication).
        assertFalse(biometric.completeEnable(Cipher.getInstance(TRANSFORMATION)))
        assertFalse(biometric.enabled.first())
        assertFalse(keys.hasKey)

        notNull(biometric.prepareEnable())
        assertFalse(biometric.completeEnable(null))
        assertFalse(biometric.enabled.first())
    }

    @Test
    fun cannotBeTurnedOnWithoutAnEnrolledFingerprint() = runBlocking {
        capability = BiometricCapability.NOT_ENROLLED
        assertNull(biometric.prepareEnable())
        assertEquals(0, keys.newCalls)

        capability = BiometricCapability.UNSUPPORTED
        assertNull(biometric.prepareEnable())
        assertEquals(0, keys.newCalls)
        assertFalse(biometric.enabled.first())
    }

    @Test
    fun keystoreTroubleWhileTurningOnStoresNothing() = runBlocking {
        keys.failing = true

        assertNull(biometric.prepareEnable())
        assertFalse(biometric.enabled.first())
        assertFalse(keys.hasKey)
    }

    // ---- Unlocking -----------------------------------------------------------------------------

    @Test
    fun anUnlockCipherWorksOnceAndOnlyOnce() = runBlocking {
        turnOn()
        val ready = biometric.prepareUnlock() as BiometricUnlockPreparation.Ready

        assertTrue(biometric.verify(ready.cipher))
        // GCM encryption cannot be repeated with the same cipher: one prompt, one unlock.
        assertFalse(biometric.verify(ready.cipher))
    }

    @Test
    fun aMissingOrUnauthorisedCipherIsNoProof() = runBlocking {
        turnOn()

        assertFalse(biometric.verify(null))
        assertFalse(biometric.verify(Cipher.getInstance(TRANSFORMATION)))
    }

    @Test
    fun aNewFingerprintTurnsItOffAndDeletesTheKey() = runBlocking {
        turnOn()
        keys.invalidated = true // e.g. someone enrolled their own finger

        assertEquals(BiometricUnlockPreparation.KeyChanged, biometric.prepareUnlock())
        assertFalse(biometric.enabled.first())
        assertFalse(keys.hasKey)

        // From now on there is simply no fingerprint unlock until it is turned on again.
        assertEquals(BiometricUnlockPreparation.NotOffered, biometric.prepareUnlock())
    }

    @Test
    fun aMissingKeyCountsAsChanged() = runBlocking {
        turnOn()
        keys.deleteKey()

        assertEquals(BiometricUnlockPreparation.KeyChanged, biometric.prepareUnlock())
        assertFalse(biometric.enabled.first())
    }

    @Test
    fun keystoreTroubleWhileUnlockingKeepsItOn() = runBlocking {
        turnOn()
        keys.failing = true

        assertEquals(BiometricUnlockPreparation.Failed, biometric.prepareUnlock())
        assertTrue(biometric.enabled.first())
        assertTrue(keys.hasKey)
    }

    @Test
    fun noFingerprintRightNowHidesItButKeepsItOn() = runBlocking {
        turnOn()
        val callsBefore = keys.existingCalls

        for (now in listOf(BiometricCapability.NOT_ENROLLED, BiometricCapability.UNSUPPORTED)) {
            capability = now
            assertEquals(BiometricUnlockPreparation.NotOffered, biometric.prepareUnlock())
        }
        assertEquals(callsBefore, keys.existingCalls)
        assertTrue(biometric.enabled.first())
        assertTrue(keys.hasKey)
    }

    // ---- Turning it off ------------------------------------------------------------------------

    @Test
    fun turningOffIsImmediateAndDeletesTheKey() = runBlocking {
        turnOn()

        biometric.disable()

        assertFalse(biometric.enabled.first())
        assertFalse(keys.hasKey)
        assertEquals(BiometricUnlockPreparation.NotOffered, biometric.prepareUnlock())
    }

    /** In-memory stand-in for [KeystoreBiometricKeys]. */
    private class FakeKeys : BiometricKeys {
        var hasKey = false
        var invalidated = false
        var failing = false
        var generation = 0
        var newCalls = 0
        var existingCalls = 0
        private var key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        override fun newKeyCipher(): BiometricCipher {
            newCalls++
            if (failing) return BiometricCipher.Unavailable
            key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            hasKey = true
            invalidated = false
            generation++
            return BiometricCipher.Ready(Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) })
        }

        override fun existingKeyCipher(): BiometricCipher {
            existingCalls++
            return when {
                failing -> BiometricCipher.Unavailable
                !hasKey || invalidated -> BiometricCipher.Invalidated
                else -> BiometricCipher.Ready(Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) })
            }
        }

        override fun deleteKey() {
            hasKey = false
            invalidated = false
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
