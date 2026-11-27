package com.monicahq.android.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** The random values of one sign-in attempt (RFC 7636), drawn from a secure source. */
object Pkce {
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** 64 random bytes, so 86 characters: within the 43 to 128 that RFC 7636 allows. */
    fun verifier(random: SecureRandom = SecureRandom()): String = randomString(random, 64)

    /** The S256 challenge sent with the authorization request. */
    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** 32 random bytes, tying the browser's answer to the attempt that started it. */
    fun state(random: SecureRandom = SecureRandom()): String = randomString(random, 32)

    private fun randomString(
        random: SecureRandom,
        bytes: Int,
    ): String = encoder.encodeToString(ByteArray(bytes).also(random::nextBytes))
}
