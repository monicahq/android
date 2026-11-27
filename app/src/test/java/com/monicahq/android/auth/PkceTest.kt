package com.monicahq.android.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    private val urlSafe = Regex("^[A-Za-z0-9_-]+$")

    @Test
    fun `the challenge follows RFC 7636 appendix B`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `the verifier is 86 url-safe characters`() {
        val verifier = Pkce.verifier()

        assertEquals(86, verifier.length)
        assertTrue(urlSafe.matches(verifier))
    }

    @Test
    fun `the state holds 32 random bytes`() {
        val state = Pkce.state()

        assertEquals(43, state.length)
        assertTrue(urlSafe.matches(state))
    }

    @Test
    fun `two attempts never share a value`() {
        assertNotEquals(Pkce.verifier(), Pkce.verifier())
        assertNotEquals(Pkce.state(), Pkce.state())
    }
}
