package com.rookierc.boysdairy.security

import com.rookierc.boysdairy.data.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.Cipher

/** A cipher for the fingerprint prompt's CryptoObject, or why there is none. */
sealed interface BiometricCipher {
    /** Initialised for ENCRYPT_MODE with the fingerprint-bound key; usable only after a successful prompt. */
    class Ready(val cipher: Cipher) : BiometricCipher

    /**
     * The key is gone for good: a fingerprint was added, all fingerprints were removed, or the key
     * is missing. Fingerprint unlock has to be set up again.
     */
    data object Invalidated : BiometricCipher

    /** Anything else went wrong (Keystore trouble); worth another try later. */
    data object Unavailable : BiometricCipher
}

/**
 * The Keystore operations behind fingerprint unlock, behind an interface so JVM tests can use a
 * software cipher instead of the AndroidKeyStore (same idea as `ApiKeyStore`'s key provider).
 * Implementations never log and never put secrets into exception messages.
 */
interface BiometricKeys {
    /** Replaces any existing key with a fresh one and returns a cipher for it (turning the feature on). */
    fun newKeyCipher(): BiometricCipher

    /** A cipher for the existing key (unlocking); [BiometricCipher.Invalidated] when there is no usable key. */
    fun existingKeyCipher(): BiometricCipher

    /** Deletes the key; a missing key is fine. */
    fun deleteKey()
}

/** Reads what the device offers right now; may change while the app runs (fingerprints enrolled / removed). */
fun interface BiometricCapabilityChecker {
    fun check(): BiometricCapability
}

/** What the lock screen can do with a fingerprint right now. */
sealed interface BiometricUnlockPreparation {
    /** Show the 👆 key; [cipher] goes into the prompt's CryptoObject. */
    class Ready(val cipher: Cipher) : BiometricUnlockPreparation

    /** The setting is off or the device cannot do it now: no fingerprint UI. */
    data object NotOffered : BiometricUnlockPreparation

    /** The enrolled fingerprints changed: the feature was just turned off and the key deleted. */
    data object KeyChanged : BiometricUnlockPreparation

    /** Keystore trouble; no fingerprint this time, the PIN works as always. */
    data object Failed : BiometricUnlockPreparation
}

/**
 * Fingerprint unlock, protected against "someone who knows the phone's screen lock enrols their
 * own finger": the setting alone never unlocks anything. Every unlock needs a successful
 * [Cipher.doFinal] with an AES key that the AndroidKeyStore only releases after a strong biometric
 * check *for that very operation*, and that is invalidated as soon as the set of enrolled
 * fingerprints changes. An invalidated key turns the feature off ([prepareUnlock]).
 *
 * Only the lock screen uses it to unlock. Changing the PIN and deleting all entries still require
 * the PIN.
 */
class BiometricUnlock(
    private val settings: SettingsRepository,
    private val keys: BiometricKeys,
    private val capabilityChecker: BiometricCapabilityChecker,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val enabled: Flow<Boolean> = settings.biometricUnlock

    suspend fun capability(): BiometricCapability = withContext(ioDispatcher) { capabilityChecker.check() }

    /**
     * For the lock screen. Only touches the key when the feature is on and usable; when the key
     * turns out to be invalidated the feature is switched off and the key deleted.
     */
    suspend fun prepareUnlock(): BiometricUnlockPreparation {
        val isOn = enabled.first()
        if (!isOn || capability() != BiometricCapability.AVAILABLE) return BiometricUnlockPreparation.NotOffered
        return when (val result = withContext(ioDispatcher) { keys.existingKeyCipher() }) {
            is BiometricCipher.Ready -> BiometricUnlockPreparation.Ready(result.cipher)
            BiometricCipher.Invalidated -> {
                disable()
                BiometricUnlockPreparation.KeyChanged
            }
            BiometricCipher.Unavailable -> BiometricUnlockPreparation.Failed
        }
    }

    /**
     * First step of turning the feature on: a fresh key bound to the fingerprints enrolled now, and
     * a cipher for the confirmation prompt. `null` when that is not possible right now. Nothing is
     * stored until [completeEnable].
     */
    suspend fun prepareEnable(): Cipher? {
        if (capability() != BiometricCapability.AVAILABLE) return null
        return when (val result = withContext(ioDispatcher) { keys.newKeyCipher() }) {
            is BiometricCipher.Ready -> result.cipher
            BiometricCipher.Invalidated, BiometricCipher.Unavailable -> {
                withContext(ioDispatcher) { keys.deleteKey() }
                null
            }
        }
    }

    /**
     * Second step: the confirmation prompt succeeded with [cipher] (from its result's
     * CryptoObject). Turns the feature on only if the key really works; otherwise cleans up.
     */
    suspend fun completeEnable(cipher: Cipher?): Boolean {
        if (!verify(cipher)) {
            abortEnable()
            return false
        }
        settings.setBiometricUnlock(true)
        return true
    }

    /** The confirmation prompt was cancelled or failed: the half-made key goes away, nothing is stored. */
    suspend fun abortEnable() {
        if (!enabled.first()) withContext(ioDispatcher) { keys.deleteKey() }
    }

    /** Turns the feature off right away and deletes the key. */
    suspend fun disable() {
        settings.setBiometricUnlock(false)
        withContext(ioDispatcher) { keys.deleteKey() }
    }

    /**
     * The proof that a fingerprint was really checked: one encryption with the authenticated
     * [cipher]. The Keystore refuses it unless the prompt authorised exactly this operation, so a
     * faked "success" callback (or a missing CryptoObject) does not unlock anything.
     */
    suspend fun verify(cipher: Cipher?): Boolean {
        if (cipher == null) return false
        return withContext(ioDispatcher) {
            try {
                cipher.doFinal(CHALLENGE)
                true
            } catch (_: GeneralSecurityException) {
                false
            } catch (_: IllegalStateException) {
                // Not initialised, or already used.
                false
            } catch (_: ProviderException) {
                false
            }
        }
    }

    private companion object {
        /** Any bytes will do: the result is thrown away, only success matters. */
        val CHALLENGE = ByteArray(16) { it.toByte() }
    }
}
