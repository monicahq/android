package com.monicahq.android.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorizationRequestTest {
    private val pending =
        PendingSignIn(
            issuer = "https://example.com/monica",
            clientId = "client-id",
            redirectUri = REDIRECT_URI,
            tokenEndpoint = "https://example.com/monica/oauth/token",
            revocationEndpoint = null,
            state = "the-state",
            codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",
            createdAt = 0,
            intent = SignInIntent.LogIn,
        )

    @Test
    fun `the address carries every parameter of the request`() {
        val url = authorizationUrl("https://example.com/monica/oauth/authorize", "api", pending).toHttpUrl()

        assertEquals("/monica/oauth/authorize", url.encodedPath)
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("client-id", url.queryParameter("client_id"))
        assertEquals("com.monicahq.android:/oauth/callback", url.queryParameter("redirect_uri"))
        assertEquals("api", url.queryParameter("scope"))
        assertEquals("the-state", url.queryParameter("state"))
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("consent", url.queryParameter("prompt"))
    }

    @Test
    fun `signing up asks for the registration page`() {
        val url = authorizationUrl("https://example.com/oauth/authorize", "api", pending.copy(intent = SignInIntent.SignUp)).toHttpUrl()

        assertEquals("consent create", url.queryParameter("prompt"))
    }

    @Test
    fun `a code and its state are read`() {
        assertEquals(
            Callback.Code(code = "a/b", state = "s1", issuer = null),
            parseCallback("com.monicahq.android:/oauth/callback?code=a%2Fb&state=s1"),
        )
    }

    @Test
    fun `the issuer is read when the instance sends it`() {
        assertEquals(
            Callback.Code(code = "c", state = "s", issuer = "https://example.com"),
            parseCallback("com.monicahq.android:/oauth/callback?code=c&state=s&iss=https%3A%2F%2Fexample.com"),
        )
    }

    @Test
    fun `a refusal is read`() {
        assertEquals(Callback.Denied("s"), parseCallback("com.monicahq.android:/oauth/callback?error=access_denied&state=s"))
    }

    @Test
    fun `other errors are read`() {
        assertEquals(Callback.Error("s"), parseCallback("com.monicahq.android:/oauth/callback?error=invalid_scope&state=s"))
        assertEquals(Callback.Error(null), parseCallback("com.monicahq.android:/oauth/callback?error=server_error"))
    }

    @Test
    fun `addresses that are not the callback are rejected`() {
        assertEquals(Callback.Rejected, parseCallback("https://example.com/oauth/callback?code=c&state=s"))
        assertEquals(Callback.Rejected, parseCallback("com.monicahq.android:/other?code=c&state=s"))
        assertEquals(Callback.Rejected, parseCallback("com.monicahq.android://evil/oauth/callback?code=c&state=s"))
        assertEquals(Callback.Rejected, parseCallback("com.monicahq.android:/oauth/callback?code=c"))
        assertEquals(Callback.Rejected, parseCallback("com.monicahq.android:/oauth/callback"))
        assertEquals(Callback.Rejected, parseCallback("::not a uri"))
    }
}
