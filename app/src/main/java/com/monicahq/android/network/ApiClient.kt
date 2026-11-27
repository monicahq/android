package com.monicahq.android.network

import com.monicahq.android.auth.Access
import com.monicahq.android.auth.AuthError
import com.monicahq.android.auth.SessionManager
import com.monicahq.android.auth.SignedInUser
import com.monicahq.android.instance.sameOrigin
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** The outcome of a request to the instance. */
sealed interface ApiResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ApiResult<T>

    /** Offline, unreachable, not allowed, moved or failing: the session goes on. */
    data class Failed(
        val error: AuthError,
    ) : ApiResult<Nothing>

    /** The session was ended by the instance; the app is back on the welcome screen. */
    data object SessionEnded : ApiResult<Nothing>

    /** The person logged out or switched instance while the request was running. */
    data object Discarded : ApiResult<Nothing>
}

/**
 * Talks to the Monica API of one instance, for one session. Credentials are only ever added to
 * requests for [instanceUrl]'s origin, redirects are never followed, and once the session changes
 * its calls are cancelled and their results discarded. Get it from [SessionManager.apiClient].
 */
class ApiClient internal constructor(
    val instanceUrl: String,
    private val generation: Int,
    private val sessions: SessionManager,
    httpClient: OkHttpClient,
) {
    /** A dispatcher of its own, so switching instance cancels exactly this session's calls. */
    private val client = httpClient.newBuilder().dispatcher(Dispatcher()).build()
    private val origin = instanceUrl.toHttpUrl()

    internal fun cancelAll() = client.dispatcher.cancelAll()

    /** `GET /api/v1/me`: the person signed in. */
    suspend fun me(): ApiResult<SignedInUser> =
        get("/api/v1/me") { body ->
            val user = json.decodeFromString(MeDocument.serializer(), body).data
            val name = listOfNotNull(user.attributes.firstName, user.attributes.lastName).joinToString(" ").trim()
            SignedInUser(id = user.id, name = name.ifEmpty { user.attributes.email }, email = user.attributes.email)
        }

    private suspend fun <T> get(
        path: String,
        parse: (String) -> T,
    ): ApiResult<T> {
        val url = (instanceUrl + path).toHttpUrl()
        val token =
            when (val access = sessions.access(generation)) {
                is Access.Token -> access.value
                Access.Ended -> return ApiResult.SessionEnded
                Access.Discarded -> return ApiResult.Discarded
                is Access.Failed -> return ApiResult.Failed(access.error)
            }
        val first = send(url, token)
        if (first !is Sent.Answer || first.code != 401) return finish(first, parse)

        // Only an explicit "not authenticated" from this instance renews access, and only once.
        val renewed =
            when (val access = sessions.refresh(generation, token)) {
                is Access.Token -> access.value
                Access.Ended -> return ApiResult.SessionEnded
                Access.Discarded -> return ApiResult.Discarded
                is Access.Failed -> return ApiResult.Failed(access.error)
            }
        val second = send(url, renewed)
        if (second is Sent.Answer && second.code == 401) {
            sessions.rejected(generation)
            return ApiResult.SessionEnded
        }
        return finish(second, parse)
    }

    private sealed interface Sent {
        data class Answer(
            val code: Int,
            val body: String,
        ) : Sent

        data class Failure(
            val error: AuthError,
        ) : Sent
    }

    private suspend fun send(
        url: okhttp3.HttpUrl,
        token: String,
    ): Sent {
        val request = Request.Builder().url(url).header("Accept", "application/json")
        if (url.sameOrigin(origin)) request.header("Authorization", "Bearer $token")
        return try {
            client.newCall(request.build()).fetch().let { Sent.Answer(it.code, it.body) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Sent.Failure(AuthError.from(e))
        }
    }

    private suspend fun <T> finish(
        sent: Sent,
        parse: (String) -> T,
    ): ApiResult<T> {
        if (!sessions.isCurrent(generation)) return ApiResult.Discarded
        return when (sent) {
            is Sent.Failure -> {
                ApiResult.Failed(sent.error)
            }

            is Sent.Answer -> {
                when (sent.code) {
                    in 200..299 -> {
                        runCatching { ApiResult.Success(parse(sent.body)) }.getOrElse { ApiResult.Failed(AuthError.ServerError) }
                    }

                    in 300..399 -> {
                        ApiResult.Failed(AuthError.Moved)
                    }

                    403 -> {
                        ApiResult.Failed(AuthError.NotAllowed)
                    }

                    else -> {
                        ApiResult.Failed(AuthError.ServerError)
                    }
                }
            }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class MeDocument(
    val data: UserDocument,
)

@Serializable
private data class UserDocument(
    val id: String,
    val attributes: UserAttributes,
)

@Serializable
private data class UserAttributes(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val email: String,
)
