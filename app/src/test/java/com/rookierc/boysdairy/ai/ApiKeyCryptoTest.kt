package com.rookierc.boysdairy.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class ApiKeyCryptoTest {

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val key = newKey()
    private val crypto = ApiKeyCrypto { key }

    private val apiKey = "sk-ant-api03-EXAMPLE-0123456789"

    @Test
    fun roundTrip() {
        assertEquals(apiKey, crypto.decryptOrNull(crypto.encrypt(apiKey)))
    }

    @Test
    fun roundTripWithNonAsciiText() {
        val odd = "kéy-中文-🔑"
        assertEquals(odd, crypto.decryptOrNull(crypto.encrypt(odd)))
    }

    @Test
    fun theStoredBlobDoesNotContainThePlainKey() {
        val blob = crypto.encrypt(apiKey)
        assertFalse(blob.contains(apiKey))
        assertFalse(String(Base64.getDecoder().decode(blob), Charsets.ISO_8859_1).contains("sk-ant"))
    }

    @Test
    fun everyEncryptionUsesAFreshIv() {
        assertNotEquals(crypto.encrypt(apiKey), crypto.encrypt(apiKey))
    }

    @Test
    fun blobLayoutIsTwelveByteIvThenCiphertextWithTag() {
        val bytes = Base64.getDecoder().decode(crypto.encrypt(apiKey))
        assertEquals(12 + apiKey.length + 16, bytes.size)
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val bytes = Base64.getDecoder().decode(crypto.encrypt(apiKey))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        assertNull(crypto.decryptOrNull(Base64.getEncoder().encodeToString(bytes)))
    }

    @Test
    fun aDifferentKeyCannotDecrypt() {
        val blob = crypto.encrypt(apiKey)
        assertNull(ApiKeyCrypto { newKey() }.decryptOrNull(blob))
    }

    @Test
    fun malformedBlobsReturnNullInsteadOfThrowing() {
        assertNull(crypto.decryptOrNull(""))
        assertNull(crypto.decryptOrNull("not base64 !!!"))
        assertNull(crypto.decryptOrNull(Base64.getEncoder().encodeToString(ByteArray(5))))
        assertNull(crypto.decryptOrNull(Base64.getEncoder().encodeToString(ByteArray(12)))) // IV only
        assertNull(crypto.decryptOrNull(Base64.getEncoder().encodeToString(ByteArray(40)))) // garbage
    }
}
