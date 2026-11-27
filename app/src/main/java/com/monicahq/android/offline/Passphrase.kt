package com.monicahq.android.offline

import com.monicahq.android.auth.TokenCipher
import java.io.File
import java.security.SecureRandom

/**
 * The offline database's passphrase: 32 random bytes, kept on disk only encrypted by [cipher]
 * (a Keystore key of its own). Destroying that key makes the file, and so the database, unreadable.
 */
internal class Passphrase(
    private val file: File,
    private val cipher: TokenCipher,
) {
    /** The stored passphrase; throws when it is missing or its key is gone. */
    fun read(): ByteArray = cipher.decrypt(file.readBytes())

    /** A new passphrase, stored encrypted. */
    fun create(): ByteArray {
        val passphrase = ByteArray(BYTES).also { SecureRandom().nextBytes(it) }
        file.parentFile?.mkdirs()
        file.writeBytes(cipher.encrypt(passphrase))
        return passphrase
    }

    fun exists(): Boolean = file.exists()

    private companion object {
        const val BYTES = 32
    }
}
