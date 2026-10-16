package com.rookierc.boysdairy.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Pure-JVM PIN hashing helpers (no Android dependencies, unit-testable). */
object PinHasher {
    const val PIN_LENGTH = 4
    const val SALT_BYTES = 16

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    fun generateSalt(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    /** Lower-case hex SHA-256 over `salt || pin(UTF-8)`. */
    fun hash(salt: ByteArray, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        digest.update(pin.toByteArray(Charsets.UTF_8))
        return digest.digest().toHex()
    }

    /** Constant-time comparison of the freshly computed hash with the stored one. */
    fun verify(salt: ByteArray, pin: String, expectedHashHex: String): Boolean {
        val actual = hash(salt, pin).toByteArray(Charsets.US_ASCII)
        val expected = expectedHashHex.toByteArray(Charsets.US_ASCII)
        return MessageDigest.isEqual(actual, expected)
    }

    fun encodeSalt(salt: ByteArray): String = Base64.getEncoder().encodeToString(salt)

    fun decodeSalt(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            out.append(HEX_DIGITS[value ushr 4])
            out.append(HEX_DIGITS[value and 0x0F])
        }
        return out.toString()
    }
}
