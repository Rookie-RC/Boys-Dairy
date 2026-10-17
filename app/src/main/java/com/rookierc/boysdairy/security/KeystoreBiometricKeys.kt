package com.rookierc.boysdairy.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * [BiometricKeys] on the AndroidKeyStore: a non-exportable AES-256/GCM key (alias [KEY_ALIAS])
 * that needs a strong biometric check for every single use and is invalidated by the system as
 * soon as a fingerprint is added or all of them are removed. Nothing is ever logged.
 */
class KeystoreBiometricKeys : BiometricKeys {

    override fun newKeyCipher(): BiometricCipher = guarded {
        deleteKey()
        BiometricCipher.Ready(encryptCipher(generateKey()))
    }

    override fun existingKeyCipher(): BiometricCipher = guarded {
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
        if (key == null) BiometricCipher.Invalidated else BiometricCipher.Ready(encryptCipher(key))
    }

    override fun deleteKey() {
        try {
            val keyStore = keyStore()
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: GeneralSecurityException) {
            // Best effort: a key that cannot be deleted cannot be used either.
        } catch (_: IOException) {
        } catch (_: ProviderException) {
        }
    }

    private inline fun guarded(block: () -> BiometricCipher): BiometricCipher = try {
        block()
    } catch (_: KeyPermanentlyInvalidatedException) {
        BiometricCipher.Invalidated
    } catch (_: UnrecoverableKeyException) {
        BiometricCipher.Invalidated
    } catch (_: GeneralSecurityException) {
        BiometricCipher.Unavailable
    } catch (_: IOException) {
        BiometricCipher.Unavailable
    } catch (_: ProviderException) {
        BiometricCipher.Unavailable
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun encryptCipher(key: SecretKey): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }

    private fun generateKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // A newly enrolled fingerprint (or removing all of them) kills the key, so knowing the
            // phone's screen lock and adding a finger is not enough to get in.
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // 0 seconds = a strong biometric check for every single operation.
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    // -1 = authentication required for every use (biometric only).
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /**
         * Keystore alias of the fingerprint key. Separate from the API-key alias
         * (`boysdairy_claude_key`), which must never require authentication. Do not rename.
         */
        const val KEY_ALIAS = "boysdairy_biometric_key"

        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** [BiometricCapabilityChecker] backed by androidx's [BiometricManager] (strong biometrics only). */
class AndroidBiometricCapabilityChecker(context: Context) : BiometricCapabilityChecker {
    private val manager = BiometricManager.from(context.applicationContext)

    override fun check(): BiometricCapability =
        biometricCapabilityOf(manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
}
