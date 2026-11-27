package com.monicahq.android.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts what the app stores about a session and its offline copy. An interface so JVM tests can
 * run without a Keystore.
 */
interface TokenCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** Throws when [data] was not produced by [encrypt] with the same key, or was changed. */
    fun decrypt(data: ByteArray): ByteArray

    /**
     * Destroys the key: everything encrypted so far can never be decrypted again. The next
     * [encrypt] uses a new key. Used to erase the offline copy at once (crypto-erasure).
     */
    fun destroyKey()
}

/**
 * AES-256-GCM with a key that never leaves the Android Keystore. Each value gets a fresh IV, stored
 * in front of the ciphertext. A restored backup has no key, so its values cannot be read: the
 * person signs in again.
 *
 * Each use has its own key, named by [alias]: the session's (`monica_session`) and the offline
 * copy's (`monica_offline`) never decrypt each other's values.
 */
class KeystoreTokenCipher(
    private val alias: String = SESSION_ALIAS,
) : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    override fun destroyKey() {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val SESSION_ALIAS = "monica_session"
        const val OFFLINE_ALIAS = "monica_offline"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
