package com.monicahq.android.instance

import com.monicahq.android.auth.AuthError
import com.monicahq.android.network.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/** The outcome of checking an address. */
sealed interface CheckResult {
    data class Compatible(
        val url: String,
        val configuration: InstanceConfiguration,
    ) : CheckResult

    data class Failed(
        val error: AuthError,
    ) : CheckResult
}

/**
 * Tells whether an address is a Monica the app can sign in to, and learns how: Monica's status,
 * then its OAuth description. It never saves anything; the caller saves the address once it is
 * [CheckResult.Compatible].
 */
class InstanceChecker(
    private val client: OkHttpClient,
    private val appVersion: String,
    private val allowHttp: Boolean,
) {
    suspend fun check(input: String): CheckResult {
        val url =
            normalizeInstanceUrl(input, allowHttp)
                ?: return CheckResult.Failed(if (requiresHttps(input, allowHttp)) AuthError.HttpsRequired else AuthError.InvalidAddress)

        val status =
            when (val answer = get("$url/api/v1/status", StatusDocument.serializer(), missing = AuthError.NotMonica)) {
                is Answer.Failed -> return CheckResult.Failed(answer.error)
                is Answer.Ok -> answer.value
            }
        val mobile = status.mobile
        if (mobile == null || mobile.apiVersion < 1) return CheckResult.Failed(AuthError.MonicaTooOld)
        val minimum = mobile.minimumAndroidVersion
        if (minimum != null && compareVersions(appVersion, minimum) < 0) return CheckResult.Failed(AuthError.AppTooOld)
        if (mobile.clientId.isBlank()) return CheckResult.Failed(AuthError.NoAppSignIn)

        val server =
            when (
                val answer =
                    get(
                        "$url/.well-known/oauth-authorization-server",
                        AuthorizationServerDocument.serializer(),
                        missing = AuthError.NoAppSignIn,
                    )
            ) {
                is Answer.Failed -> return CheckResult.Failed(answer.error)
                is Answer.Ok -> answer.value
            }
        val endpoints = listOfNotNull(server.authorizationEndpoint, server.tokenEndpoint, server.revocationEndpoint)
        val compatible =
            normalizeInstanceUrl(server.issuer, allowHttp = true) == url &&
                endpoints.all { isUnderBase(it, url) } &&
                "S256" in server.codeChallengeMethods
        if (!compatible) return CheckResult.Failed(AuthError.NoAppSignIn)

        return CheckResult.Compatible(
            url,
            InstanceConfiguration(
                issuer = url,
                authorizationEndpoint = server.authorizationEndpoint,
                tokenEndpoint = server.tokenEndpoint,
                revocationEndpoint = server.revocationEndpoint,
                clientId = mobile.clientId,
                scope = mobile.scope,
                registrationEnabled = mobile.registrationEnabled,
            ),
        )
    }

    private sealed interface Answer<out T> {
        data class Ok<T>(
            val value: T,
        ) : Answer<T>

        data class Failed(
            val error: AuthError,
        ) : Answer<Nothing>
    }

    /** [missing] is the error when the document is absent or is not the expected JSON. */
    private suspend fun <T> get(
        url: String,
        serializer: KSerializer<T>,
        missing: AuthError,
    ): Answer<T> =
        try {
            client
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .build(),
                ).fetch()
                .let { response ->
                    when (response.code) {
                        in 200..299 -> {
                            runCatching { json.decodeFromString(serializer, response.body) }
                                .fold({ Answer.Ok(it) }, { Answer.Failed(missing) })
                        }

                        in 300..399 -> {
                            Answer.Failed(AuthError.Moved)
                        }

                        in 500..599 -> {
                            Answer.Failed(AuthError.ServerError)
                        }

                        else -> {
                            Answer.Failed(missing)
                        }
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Answer.Failed(AuthError.from(e))
        }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Compares dotted version numbers part by part ("1.10" is after "1.9"); missing parts count as 0. */
fun compareVersions(
    a: String,
    b: String,
): Int {
    val left = a.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val right = b.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(left.size, right.size)) {
        val difference = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
        if (difference != 0) return difference
    }
    return 0
}
