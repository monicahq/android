package com.monicahq.android.auth

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URI
import java.net.URLDecoder

/** Where the browser sends the person back: an address only this app opens (RFC 8252 §7.1). */
const val REDIRECT_URI = "com.monicahq.android:/oauth/callback"

/** The address of the instance's authorization page for [pending], to open in the browser. */
fun authorizationUrl(
    authorizationEndpoint: String,
    scope: String,
    pending: PendingSignIn,
): String =
    authorizationEndpoint
        .toHttpUrl()
        .newBuilder()
        .addQueryParameter("response_type", "code")
        .addQueryParameter("client_id", pending.clientId)
        .addQueryParameter("redirect_uri", pending.redirectUri)
        .addQueryParameter("scope", scope)
        .addQueryParameter("state", pending.state)
        .addQueryParameter("code_challenge", Pkce.challenge(pending.codeVerifier))
        .addQueryParameter("code_challenge_method", "S256")
        // Consent at every sign-in; "create" opens the registration page first.
        .addQueryParameter("prompt", if (pending.intent == SignInIntent.SignUp) "consent create" else "consent")
        .build()
        .toString()

/** What the browser brought back to the app. */
sealed interface Callback {
    data class Code(
        val code: String,
        val state: String,
        val issuer: String?,
    ) : Callback

    data class Denied(
        val state: String?,
    ) : Callback

    data class Error(
        val state: String?,
    ) : Callback

    /** Not an answer to a sign-in: another address, or no code and no error. */
    data object Rejected : Callback
}

/** Reads the address the browser opened the app with. */
fun parseCallback(uri: String): Callback {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return Callback.Rejected
    if (parsed.scheme != "com.monicahq.android" || parsed.rawAuthority != null || parsed.rawPath != "/oauth/callback") {
        return Callback.Rejected
    }
    val parameters =
        parsed.rawQuery
            .orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val name = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                URLDecoder.decode(name, "UTF-8") to URLDecoder.decode(value, "UTF-8")
            }
    val state = parameters["state"]
    val error = parameters["error"]
    val code = parameters["code"]
    return when {
        error == "access_denied" -> Callback.Denied(state)
        error != null -> Callback.Error(state)
        code != null && state != null -> Callback.Code(code, state, parameters["iss"])
        else -> Callback.Rejected
    }
}
