package com.monicahq.android.auth

/**
 * Reversible and tamper-evident enough for tests: a marker naming the current key, then the bytes
 * reversed. [destroyKey] moves to a new key, so earlier values no longer decrypt.
 */
class FakeTokenCipher : TokenCipher {
    private var keyGeneration = 0

    /** How many times the key was destroyed. */
    val destroyedKeys: Int get() = keyGeneration

    private fun marker() = "fake$keyGeneration:".toByteArray()

    override fun encrypt(plain: ByteArray): ByteArray = marker() + plain.reversedArray()

    override fun decrypt(data: ByteArray): ByteArray {
        val marker = marker()
        require(data.size >= marker.size && data.copyOfRange(0, marker.size).contentEquals(marker)) { "not encrypted by this key" }
        return data.copyOfRange(marker.size, data.size).reversedArray()
    }

    override fun destroyKey() {
        keyGeneration++
    }
}
