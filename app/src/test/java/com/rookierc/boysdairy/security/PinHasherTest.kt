package com.rookierc.boysdairy.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val salt = ByteArray(PinHasher.SALT_BYTES) { it.toByte() }

    @Test
    fun samePinAndSaltProduceSameHash() {
        assertEquals(PinHasher.hash(salt, "1234"), PinHasher.hash(salt, "1234"))
    }

    @Test
    fun differentPinProducesDifferentHash() {
        assertNotEquals(PinHasher.hash(salt, "1234"), PinHasher.hash(salt, "1235"))
    }

    @Test
    fun differentSaltProducesDifferentHash() {
        val otherSalt = ByteArray(PinHasher.SALT_BYTES) { (it + 1).toByte() }
        assertNotEquals(PinHasher.hash(salt, "1234"), PinHasher.hash(otherSalt, "1234"))
    }

    @Test
    fun hashIsLowerCaseHexSha256() {
        val hash = PinHasher.hash(salt, "0000")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun verifyAcceptsCorrectPin() {
        val stored = PinHasher.hash(salt, "4821")
        assertTrue(PinHasher.verify(salt, "4821", stored))
    }

    @Test
    fun verifyRejectsWrongPin() {
        val stored = PinHasher.hash(salt, "4821")
        assertFalse(PinHasher.verify(salt, "4822", stored))
        assertFalse(PinHasher.verify(salt, "", stored))
    }

    @Test
    fun verifyRejectsWrongSalt() {
        val stored = PinHasher.hash(salt, "4821")
        val otherSalt = ByteArray(PinHasher.SALT_BYTES) { 0x7F }
        assertFalse(PinHasher.verify(otherSalt, "4821", stored))
    }

    @Test
    fun verifyRejectsMalformedStoredHash() {
        assertFalse(PinHasher.verify(salt, "4821", "not-a-hash"))
    }

    @Test
    fun generatedSaltsHaveExpectedLengthAndDiffer() {
        val first = PinHasher.generateSalt()
        val second = PinHasher.generateSalt()
        assertEquals(PinHasher.SALT_BYTES, first.size)
        assertEquals(PinHasher.SALT_BYTES, second.size)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun saltBase64RoundTrips() {
        val encoded = PinHasher.encodeSalt(salt)
        assertTrue(PinHasher.decodeSalt(encoded).contentEquals(salt))
    }
}
