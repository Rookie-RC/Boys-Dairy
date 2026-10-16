package com.rookierc.boysdairy.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** [ApiKeyStore] on a real Preferences DataStore file, with a software AES key instead of the Keystore. */
class ApiKeyStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aesKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private lateinit var dataStore: DataStore<Preferences>

    // These names are the on-disk format: renaming them would orphan stored keys.
    private val legacyBlob = stringPreferencesKey("claudeApiKeyBlob")
    private val deepSeekSlot = stringPreferencesKey("api_key_deepseek")
    private val claudeSlot = stringPreferencesKey("api_key_claude")

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "secure.preferences_pb") }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    /** A "process" over the shared file; a fresh store also re-runs the one-time migration. */
    private fun newStore(key: SecretKey = aesKey) = ApiKeyStore(dataStore, ApiKeyCrypto { key })

    private fun prefs(): Preferences = runBlocking { dataStore.data.first() }

    @Test
    fun nothingIsStoredInitially() = runBlocking {
        val store = newStore()
        for (provider in AiProvider.entries) {
            assertFalse(store.hasKey(provider).first())
            assertNull(store.load(provider))
        }
    }

    @Test
    fun saveAndLoadRoundTripPerProvider() = runBlocking {
        val store = newStore()
        store.save(AiProvider.DEEPSEEK, "sk-deepseek")
        store.save(AiProvider.CLAUDE, "sk-ant-claude")

        assertEquals("sk-deepseek", store.load(AiProvider.DEEPSEEK))
        assertEquals("sk-ant-claude", store.load(AiProvider.CLAUDE))
        assertTrue(store.hasKey(AiProvider.DEEPSEEK).first())
        assertTrue(store.hasKey(AiProvider.CLAUDE).first())
    }

    @Test
    fun keysAreStoredUnderPerProviderNamesAndEncrypted() = runBlocking {
        val store = newStore()
        store.save(AiProvider.DEEPSEEK, "sk-deepseek")
        store.save(AiProvider.CLAUDE, "sk-ant-claude")

        val snapshot = prefs()
        val deepSeekBlob = snapshot[deepSeekSlot]
        val claudeBlob = snapshot[claudeSlot]
        assertNotNull(deepSeekBlob)
        assertNotNull(claudeBlob)
        assertFalse(deepSeekBlob!!.contains("sk-deepseek"))
        assertFalse(claudeBlob!!.contains("sk-ant-claude"))
        assertEquals("sk-deepseek", ApiKeyCrypto { aesKey }.decryptOrNull(deepSeekBlob))
        assertEquals("sk-ant-claude", ApiKeyCrypto { aesKey }.decryptOrNull(claudeBlob))
    }

    @Test
    fun theKeyIsTrimmedAndABlankKeyClearsTheSlot() = runBlocking {
        val store = newStore()
        store.save(AiProvider.DEEPSEEK, "  sk-padded \n")
        assertEquals("sk-padded", store.load(AiProvider.DEEPSEEK))

        store.save(AiProvider.DEEPSEEK, "   ")
        assertNull(store.load(AiProvider.DEEPSEEK))
        assertFalse(store.hasKey(AiProvider.DEEPSEEK).first())
    }

    @Test
    fun providersAreIndependent() = runBlocking {
        val store = newStore()
        store.save(AiProvider.DEEPSEEK, "sk-deepseek")

        assertTrue(store.hasKey(AiProvider.DEEPSEEK).first())
        assertFalse(store.hasKey(AiProvider.CLAUDE).first())
        assertNull(store.load(AiProvider.CLAUDE))

        store.save(AiProvider.CLAUDE, "sk-ant-claude")
        store.clear(AiProvider.DEEPSEEK)

        assertNull(store.load(AiProvider.DEEPSEEK))
        assertEquals("sk-ant-claude", store.load(AiProvider.CLAUDE))
    }

    @Test
    fun clearRemovesOnlyThatProvidersKey() = runBlocking {
        val store = newStore()
        store.save(AiProvider.DEEPSEEK, "a")
        store.save(AiProvider.CLAUDE, "b")

        store.clear(AiProvider.CLAUDE)

        assertFalse(store.hasKey(AiProvider.CLAUDE).first())
        assertTrue(store.hasKey(AiProvider.DEEPSEEK).first())
    }

    @Test
    fun replacingAKeyOverwritesIt() = runBlocking {
        val store = newStore()
        store.save(AiProvider.CLAUDE, "old")
        store.save(AiProvider.CLAUDE, "new")

        assertEquals("new", store.load(AiProvider.CLAUDE))
    }

    @Test
    fun aKeyOutlivesTheStoreInstance() = runBlocking {
        newStore().save(AiProvider.DEEPSEEK, "sk-deepseek")

        assertEquals("sk-deepseek", newStore().load(AiProvider.DEEPSEEK))
    }

    @Test
    fun anUndecryptableKeyIsDeletedAndReportedAsMissing() = runBlocking {
        newStore().save(AiProvider.DEEPSEEK, "sk-deepseek")
        newStore().save(AiProvider.CLAUDE, "sk-ant-claude")
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val lostKeystore = newStore(otherKey)

        assertTrue(lostKeystore.hasKey(AiProvider.DEEPSEEK).first())
        assertNull(lostKeystore.load(AiProvider.DEEPSEEK))
        assertFalse(lostKeystore.hasKey(AiProvider.DEEPSEEK).first())
        assertNull(prefs()[deepSeekSlot])
        // The other slot is only touched once something tries to read it.
        assertNotNull(prefs()[claudeSlot])
    }

    // ---- legacy single-blob migration ---------------------------------------------------------

    private fun writeLegacyBlob(plain: String) = runBlocking {
        val blob = ApiKeyCrypto { aesKey }.encrypt(plain)
        dataStore.edit { it[legacyBlob] = blob }
        blob
    }

    @Test
    fun theLegacyBlobMovesToTheClaudeSlotOnFirstLoad() = runBlocking {
        val blob = writeLegacyBlob("sk-ant-legacy")
        val store = newStore()

        assertEquals("sk-ant-legacy", store.load(AiProvider.CLAUDE))
        assertNull(store.load(AiProvider.DEEPSEEK))

        val snapshot = prefs()
        assertNull("legacy entry is removed", snapshot[legacyBlob])
        assertEquals("ciphertext is moved untouched", blob, snapshot[claudeSlot])
    }

    @Test
    fun hasKeyAlsoMigratesTheLegacyBlob() = runBlocking {
        writeLegacyBlob("sk-ant-legacy")
        val store = newStore()

        assertTrue(store.hasKey(AiProvider.CLAUDE).first())
        assertNull(prefs()[legacyBlob])
        assertFalse(store.hasKey(AiProvider.DEEPSEEK).first())
    }

    @Test
    fun saveAndClearMigrateFirstSoNothingLegacyLingers() = runBlocking {
        writeLegacyBlob("sk-ant-legacy")
        newStore().save(AiProvider.DEEPSEEK, "sk-deepseek")
        assertNull(prefs()[legacyBlob])
        assertEquals("sk-ant-legacy", newStore().load(AiProvider.CLAUDE))

        writeLegacyBlob("sk-ant-legacy-2")
        val store = newStore()
        store.clear(AiProvider.CLAUDE)
        assertNull(prefs()[legacyBlob])
        assertNull(store.load(AiProvider.CLAUDE))
    }

    @Test
    fun anExistingClaudeKeyWinsOverTheLegacyBlob() = runBlocking {
        val store0 = newStore()
        store0.save(AiProvider.CLAUDE, "sk-ant-new")
        writeLegacyBlob("sk-ant-stale")

        val store = newStore()
        assertEquals("sk-ant-new", store.load(AiProvider.CLAUDE))
        assertNull(prefs()[legacyBlob])
    }

    @Test
    fun migrationDoesNothingWithoutALegacyBlob() = runBlocking {
        val store = newStore()
        store.save(AiProvider.DEEPSEEK, "sk-deepseek")

        assertEquals("sk-deepseek", store.load(AiProvider.DEEPSEEK))
        assertNull(store.load(AiProvider.CLAUDE))
        assertNull(prefs()[legacyBlob])
    }
}
