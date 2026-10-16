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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate DataStore file for the encrypted API key (kept out of the general settings file). */
private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "secure")

/**
 * Holds the user's Claude API key. The key is encrypted with an AES-256/GCM key that lives in the
 * AndroidKeyStore (alias [KEY_ALIAS], non-exportable); the stored value is Base64 of
 * `iv || ciphertext || tag` in the `secure` DataStore file. The plain key is never logged and
 * exists only transiently in memory.
 */
class ApiKeyStore(context: Context) {
    private val dataStore: DataStore<Preferences> = context.applicationContext.secureDataStore

    private val crypto = ApiKeyCrypto { androidKeystoreKey() }

    /** True while an encrypted key is stored (does not decrypt it). */
    val hasKey: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[KEY_BLOB] != null }
        .distinctUntilChanged()

    /** Stores [key] (trimmed). A blank key clears the stored one. */
    suspend fun save(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val blob = withContext(Dispatchers.IO) { crypto.encrypt(trimmed) }
        dataStore.edit { prefs -> prefs[KEY_BLOB] = blob }
    }

    /**
     * The plain key, or null when none is stored or it can no longer be decrypted (for example the
     * Keystore key was lost). An undecryptable blob is deleted so [hasKey] turns false.
     */
    suspend fun load(): String? {
        val blob = try {
            dataStore.data.first()[KEY_BLOB]
        } catch (_: IOException) {
            null
        } ?: return null
        val key = withContext(Dispatchers.IO) { crypto.decryptOrNull(blob) }
        if (key == null) clear()
        return key
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_BLOB) }
    }

    private fun androidKeystoreKey(): SecretKey {
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

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "boysdairy_claude_key"
        val KEY_BLOB = stringPreferencesKey("claudeApiKeyBlob")
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
