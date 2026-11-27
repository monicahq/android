package com.monicahq.android.auth

import com.monicahq.android.network.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** A successful answer of the token endpoint. */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
) {
    override fun toString(): String = "TokenResponse(expiresIn=$expiresIn)"
}

sealed interface TokenResult {
    data class Success(
        val tokens: TokenResponse,
    ) : TokenResult

    /** The instance refused the code or the refresh token: used, expired, revoked or wrong. */
    data object Refused : TokenResult

    /** The instance could not be reached or failed; nothing is known about the grant. */
    data class Failed(
        val error: AuthError,
    ) : TokenResult
}

/** Talks to the instance's token and revocation endpoints (RFC 6749, RFC 7009), as a public client. */
class TokenEndpointClient(
    private val client: OkHttpClient,
) {
    suspend fun exchangeCode(
        tokenEndpoint: String,
        clientId: String,
        redirectUri: String,
        code: String,
        codeVerifier: String,
        deviceName: String,
    ): TokenResult =
        token(
            tokenEndpoint,
            FormBody
                .Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("redirect_uri", redirectUri)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .add("device_name", deviceName.take(255))
                .build(),
        )

    suspend fun refresh(
        tokenEndpoint: String,
        clientId: String,
        refreshToken: String,
    ): TokenResult =
        token(
            tokenEndpoint,
            FormBody
                .Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
                .build(),
        )

    /** True when the instance answered, whatever it said; false when it should be tried again later. */
    suspend fun revoke(
        revocationEndpoint: String,
        clientId: String,
        refreshToken: String,
    ): Boolean =
        try {
            val body =
                FormBody
                    .Builder()
                    .add("token", refreshToken)
                    .add("token_type_hint", "refresh_token")
                    .add("client_id", clientId)
                    .build()
            client
                .newCall(
                    Request
                        .Builder()
                        .url(revocationEndpoint)
                        .post(body)
                        .build(),
                ).fetch()
                .code !in 500..599
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    private suspend fun token(
        tokenEndpoint: String,
        body: FormBody,
    ): TokenResult =
        try {
            val request =
                Request
                    .Builder()
                    .url(tokenEndpoint)
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
            val response = client.newCall(request).fetch()
            when {
                response.code in 200..299 -> {
                    runCatching { TokenResult.Success(json.decodeFromString(TokenResponse.serializer(), response.body)) }
                        .getOrElse { TokenResult.Failed(AuthError.ServerError) }
                }

                response.code in 400..401 && errorCode(response.body) in REFUSALS -> {
                    TokenResult.Refused
                }

                else -> {
                    TokenResult.Failed(AuthError.ServerError)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TokenResult.Failed(AuthError.from(e))
        }

    private fun errorCode(text: String): String? =
        runCatching {
            json
                .parseToJsonElement(text)
                .jsonObject["error"]
                ?.jsonPrimitive
                ?.content
        }.getOrNull()

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** The client was removed or the grant is no longer valid: the session cannot go on. */
        val REFUSALS = setOf("invalid_grant", "invalid_client")
    }
}
