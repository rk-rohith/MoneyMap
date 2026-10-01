package com.moneymap.core

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected backups: PBKDF2-SHA256 derives an AES-256 key, AES-GCM encrypts and authenticates.
 * The file is plain text so it survives being shared through chat apps:
 *
 * ```
 * MONEYMAP-ENCRYPTED-BACKUP v1
 * pbkdf2-sha256:<iterations>
 * <salt, base64>
 * <iv, base64>
 * <ciphertext, base64>
 * ```
 */
object BackupCrypto {
    const val HEADER = "MONEYMAP-ENCRYPTED-BACKUP v1"
    const val ITERATIONS = 120_000
    private const val KDF = "pbkdf2-sha256"

    class WrongPasswordException : Exception("Wrong backup password")

    fun isEncrypted(text: String): Boolean = text.trimStart().startsWith(HEADER)

    fun encrypt(plain: String, password: CharArray, random: SecureRandom = SecureRandom()): String {
        require(password.isNotEmpty()) { "Password is empty" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        cipher.updateAAD(HEADER.toByteArray())
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val b64 = Base64.getEncoder()
        return listOf(HEADER, "$KDF:$ITERATIONS", b64.encodeToString(salt), b64.encodeToString(iv), b64.encodeToString(data))
            .joinToString("\n", postfix = "\n")
    }

    /** Throws [WrongPasswordException] when the password doesn't match (or the file was changed). */
    fun decrypt(text: String, password: CharArray): String {
        val lines = text.trim().lines().map { it.trim() }
        require(lines.size >= 5 && lines[0] == HEADER) { "Not an encrypted Money map backup" }
        val (kdf, iterText) = lines[1].split(':').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }
        require(kdf == KDF) { "Unsupported backup format" }
        val iterations = iterText.toIntOrNull()?.takeIf { it in 10_000..10_000_000 } ?: error("Unsupported backup format")
        val b64 = Base64.getDecoder()
        val salt = b64.decode(lines[2])
        val iv = b64.decode(lines[3])
        val data = b64.decode(lines[4])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(128, iv))
        cipher.updateAAD(HEADER.toByteArray())
        return try {
            cipher.doFinal(data).toString(Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
