package com.monicahq.android.auth

/** Reversible and tamper-evident enough for tests: a marker, then the bytes reversed. */
class FakeTokenCipher : TokenCipher {
    override fun encrypt(plain: ByteArray): ByteArray = MARKER + plain.reversedArray()

    override fun decrypt(data: ByteArray): ByteArray {
        require(data.size >= MARKER.size && data.copyOfRange(0, MARKER.size).contentEquals(MARKER)) { "not encrypted by this cipher" }
        return data.copyOfRange(MARKER.size, data.size).reversedArray()
    }

    private companion object {
        val MARKER = "fake:".toByteArray()
    }
}
