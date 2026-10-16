package com.rookierc.boysdairy.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate DataStore file for the encrypted API key (kept out of the general settings file). */
private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure")

/**
 * Holds the user's API keys, one per [AiProvider]. Each key is encrypted with an AES-256/GCM key
 * that lives in the AndroidKeyStore (alias [KEY_ALIAS], non-exportable) and stored as Base64 of
 * `iv || ciphertext || tag` under a per-provider name (`api_key_<provider id>`) in the `secure`
 * DataStore file. The plain keys are never logged and exist only transiently in memory.
 *
 * Before multiple providers existed there was a single blob (`claudeApiKeyBlob`); the first access
 * moves it to the Claude slot and deletes it. The ciphertext is untouched, so it still decrypts.
 */
class ApiKeyStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val crypto: ApiKeyCrypto,
) {
    constructor(context: Context) : this(
        dataStore = context.applicationContext.secureDataStore,
        crypto = ApiKeyCrypto { androidKeystoreKey() },
    )

    private val migrated = AtomicBoolean(false)

    /** True while an encrypted key is stored for [provider] (does not decrypt it). */
    fun hasKey(provider: AiProvider): Flow<Boolean> {
        val slot = slotOf(provider)
        return flow {
            migrateLegacyBlob()
            emitAll(
                dataStore.data
                    .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
                    .map { prefs -> prefs[slot] != null }
                    .distinctUntilChanged(),
            )
        }
    }

    /** Stores [key] (trimmed) for [provider]. A blank key clears the stored one. */
    suspend fun save(provider: AiProvider, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clear(provider)
            return
        }
        migrateLegacyBlob()
        val blob = withContext(Dispatchers.IO) { crypto.encrypt(trimmed) }
        dataStore.edit { prefs -> prefs[slotOf(provider)] = blob }
    }

    /**
     * The plain key for [provider], or null when none is stored or it can no longer be decrypted
     * (for example the Keystore key was lost). An undecryptable blob is deleted so [hasKey] turns
     * false.
     */
    suspend fun load(provider: AiProvider): String? {
        val blob = try {
            migrateLegacyBlob()
            dataStore.data.first()[slotOf(provider)]
        } catch (_: IOException) {
            null
        } ?: return null
        val key = withContext(Dispatchers.IO) { crypto.decryptOrNull(blob) }
        if (key == null) clear(provider)
        return key
    }

    suspend fun clear(provider: AiProvider) {
        migrateLegacyBlob()
        dataStore.edit { prefs -> prefs.remove(slotOf(provider)) }
    }

    /**
     * Moves the pre-multi-provider blob to the Claude slot (unless that already has a key). Runs
     * once per process; after a disk error it is simply retried on the next access.
     */
    private suspend fun migrateLegacyBlob() {
        if (migrated.get()) return
        try {
            dataStore.edit { prefs ->
                val legacy = prefs[LEGACY_KEY_BLOB] ?: return@edit
                val claudeSlot = slotOf(AiProvider.CLAUDE)
                if (prefs[claudeSlot] == null) prefs[claudeSlot] = legacy
                prefs.remove(LEGACY_KEY_BLOB)
            }
            migrated.set(true)
        } catch (_: IOException) {
            // Leave the legacy blob where it is; the next call tries again.
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /**
         * Keystore alias of the AES key. It was created for the Claude key and now protects every
         * provider's key. Do not rename it: existing ciphertext would become undecryptable.
         */
        const val KEY_ALIAS = "boysdairy_claude_key"

        /** Name of the single blob stored before there were multiple providers. */
        val LEGACY_KEY_BLOB = stringPreferencesKey("claudeApiKeyBlob")

        fun slotOf(provider: AiProvider) = stringPreferencesKey("api_key_${provider.id}")

        fun androidKeystoreKey(): SecretKey {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}

/**
 * AES/GCM envelope used by [ApiKeyStore]; the [keyProvider] seam lets JVM tests use a software key
 * instead of the AndroidKeyStore. Blob format: Base64(iv (12 bytes) || ciphertext+tag).
 */
internal class ApiKeyCrypto(private val keyProvider: () -> SecretKey) {

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(cipher.iv + ciphertext)
    }

    /** Null if the blob is malformed, was tampered with, or the key does not match. */
    fun decryptOrNull(blob: String): String? = try {
        val bytes = Base64.getDecoder().decode(blob)
        if (bytes.size <= IV_BYTES) {
            null
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyProvider(),
                GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
            )
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
