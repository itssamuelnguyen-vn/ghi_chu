package com.samuelcruz.ghichu.sync

import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption of the Drive files (optional, Settings → "Mã hóa đầu cuối").
 *
 * The passphrase never leaves the phone: a 256-bit key is derived from it (PBKDF2-HMAC-SHA256 with a random salt)
 * and every note, label, photo and recording is encrypted with AES-256-GCM before it is uploaded. Google only sees
 * random bytes and the file names (`n_<random id>.json`). Drive keeps one small public file, `crypto.json`, with
 * the salt and a check value so another phone can derive the same key from the same passphrase.
 *
 * An encrypted file is `GCE1` + 12-byte nonce + ciphertext (with GCM's 16-byte tag). Files without that header
 * are plain (from before encryption was switched on) and are read as they are.
 */
object Crypto {
    const val CONFIG_FILE = "crypto.json"
    private val MAGIC = "GCE1".toByteArray()
    private const val NONCE = 12
    private const val TAG_BITS = 128
    const val ITERATIONS = 150_000

    /** What Drive keeps in `crypto.json`. [check] = SHA-256 of the key, first 16 bytes (tells a wrong passphrase). */
    @Serializable
    data class Config(val v: Int = 1, val salt: String, val iterations: Int = ITERATIONS, val check: String)

    private val random = SecureRandom()
    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = Base64.getDecoder().decode(s)

    fun newSalt(): ByteArray = ByteArray(16).also { random.nextBytes(it) }

    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun check(key: ByteArray): String = b64(MessageDigest.getInstance("SHA-256").digest(key).copyOf(16))

    fun newConfig(key: ByteArray, salt: ByteArray, iterations: Int = ITERATIONS) = Config(salt = b64(salt), iterations = iterations, check = check(key))

    /** The key for [passphrase] if it matches [config], else null (wrong passphrase). */
    fun unlock(passphrase: String, config: Config): ByteArray? {
        val key = deriveKey(passphrase, unb64(config.salt), config.iterations)
        return if (MessageDigest.isEqual(check(key).toByteArray(), config.check.toByteArray())) key else null
    }

    fun matches(key: ByteArray, config: Config) = check(key) == config.check

    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= MAGIC.size + NONCE && MAGIC.indices.all { data[it] == MAGIC[it] }

    fun encrypt(key: ByteArray, plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return MAGIC + nonce + c.doFinal(plain)
    }

    /** Plain data passes through; encrypted data needs [key] (throws [WrongKey] if it is missing or wrong). */
    fun decrypt(key: ByteArray?, data: ByteArray): ByteArray {
        if (!isEncrypted(data)) return data
        if (key == null) throw WrongKey()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, data, MAGIC.size, NONCE))
        return try {
            c.doFinal(data, MAGIC.size + NONCE, data.size - MAGIC.size - NONCE)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongKey()
        }
    }

    fun keyToString(key: ByteArray) = b64(key)
    fun keyFromString(s: String): ByteArray? = runCatching { unb64(s) }.getOrNull()?.takeIf { it.size == 32 }

    /** The file was encrypted with another key (or no key is set on this phone). */
    class WrongKey : Exception("Encrypted with another passphrase")
}
