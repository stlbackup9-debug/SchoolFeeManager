package com.school.feemanager.domain

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2 password hashing with a random 16-byte salt per user. */
object PasswordHasher {
    private const val ITERATIONS = 20000
    private const val KEY_BITS = 256

    fun newSalt(): String {
        val b = ByteArray(16)
        SecureRandom().nextBytes(b)
        return b.toHex()
    }

    fun hash(password: String, saltHex: String): String {
        val spec = PBEKeySpec(password.toCharArray(), saltHex.hexToBytes(), ITERATIONS, KEY_BITS)
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return f.generateSecret(spec).encoded.toHex()
    }

    fun verify(password: String, saltHex: String, expectedHex: String): Boolean =
        MessageDigest.isEqual(hash(password, saltHex).toByteArray(), expectedHex.toByteArray())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
