package com.monicahq.android.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreTokenCipherTest {
    private val cipher = KeystoreTokenCipher()
    private val secret = "refresh-token".toByteArray()

    @Test
    fun aValueComesBackAsItWent() {
        assertArrayEquals(secret, cipher.decrypt(cipher.encrypt(secret)))
    }

    @Test
    fun theSameValueIsNeverEncryptedTheSameWay() {
        assertFalse(cipher.encrypt(secret).contentEquals(cipher.encrypt(secret)))
    }

    @Test
    fun aChangedValueIsRefused() {
        val encrypted = cipher.encrypt(secret)
        encrypted[encrypted.size - 1] = (encrypted.last() + 1).toByte()

        assertTrue(runCatching { cipher.decrypt(encrypted) }.isFailure)
    }
}
