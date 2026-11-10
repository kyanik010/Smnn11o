package com.idealplayer.app.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinHasher {
    private const val FORMAT = "pbkdf2_sha256"
    private const val DEFAULT_ITERATIONS = 120_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    fun hash(pin: String): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return encode(pin = pin, salt = salt, iterations = DEFAULT_ITERATIONS)
    }

    fun verify(pin: String, storedHash: String): Boolean {
        if (isLegacySha256(storedHash)) {
            return MessageDigest.isEqual(legacySha256(pin).toByteArray(), storedHash.toByteArray())
        }

        val parts = storedHash.split("$")
        if (parts.size != 4 || parts[0] != FORMAT) return false

        return runCatching {
            val iterations = parts[1].toInt()
            val salt = Base64.getDecoder().decode(parts[2])
            val expected = Base64.getDecoder().decode(parts[3])
            val actual = pbkdf2(pin, salt, iterations)
            MessageDigest.isEqual(actual, expected)
        }.getOrDefault(false)
    }

    fun needsRehash(storedHash: String): Boolean {
        if (isLegacySha256(storedHash)) return true

        val parts = storedHash.split("$")
        val iterations = parts.getOrNull(1)?.toIntOrNull() ?: return true
        return parts.size != 4 || parts[0] != FORMAT || iterations < DEFAULT_ITERATIONS
    }

    internal fun encode(pin: String, salt: ByteArray, iterations: Int): String {
        val hash = pbkdf2(pin, salt, iterations)
        val encoder = Base64.getEncoder().withoutPadding()
        return listOf(
            FORMAT,
            iterations.toString(),
            encoder.encodeToString(salt),
            encoder.encodeToString(hash)
        ).joinToString("$")
    }

    internal fun legacySha256(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf2(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun isLegacySha256(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}
