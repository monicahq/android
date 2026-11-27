package com.monicahq.android.auth

import com.monicahq.android.network.ApiClient
import com.monicahq.android.network.ApiResult
import com.monicahq.android.offline.OfflineCopy
import com.monicahq.android.offline.Owner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Whether someone is signed in. Never holds a token. */
sealed interface SessionState {
    /** The stored session is being read, right after the app starts. */
    data object Loading : SessionState

    data class SignedOut(
        val reason: SignOutReason? = null,
    ) : SessionState

    data class SignedIn(
        val user: SignedInUser,
        val instanceUrl: String,
    ) : SessionState
}

enum class SignOutReason { Expired, Revoked, LoggedOut }

/** What [SessionManager] hands an [ApiClient] that asks for access. */
sealed interface Access {
    data class Token(
        val value: String,
    ) : Access

    /** The session was ended: the person must sign in again. */
    data object Ended : Access

    /** The session this request belonged to is gone (logout, switch): drop the result. */
    data object Discarded : Access

    /** The instance could not be reached to renew access; the session goes on. */
    data class Failed(
        val error: AuthError,
    ) : Access
}

/**
 * The one owner of the session: it stores it, renews it, ends it and tells the rest of the app
 * whether someone is signed in. Nothing else reads tokens.
 *
 * Each sign-in, sign-out and instance switch starts a new generation. Every [ApiClient] belongs to
 * one generation, and anything it brings back after the generation changed is discarded, so
 * nothing about one instance or session leaks into the next.
 */
class SessionManager(
    private val store: AuthStore,
    private val tokenClient: TokenEndpointClient,
    private val httpClient: OkHttpClient,
    /** The offline copy lives exactly as long as the session: opened here, erased here. */
    private val offlineCopy: OfflineCopy,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Guards [session], [generation] and the stored session. Held briefly, never during a network call. */
    private val commitLock = Mutex()

    /** Lets only one renewal reach the instance at a time. */
    private val renewLock = Mutex()

    private var session: Session? = null
    private var generation = 0

    @Volatile
    private var currentClient: ApiClient? = null

    /** The client for the signed-in session, or null when nobody is signed in. */
    fun apiClient(): ApiClient? = currentClient

    /** Reads the stored session, then tells the instance about a logout it missed while offline. */
    suspend fun load() {
        commitLock.withLock {
            val stored = store.session()
            session = stored
            generation++
            if (stored == null) {
                // A copy left by a run that died during sign-out is erased before anything shows.
                offlineCopy.erase()
                _state.value = SessionState.SignedOut()
            } else {
                // Another owner's, unreadable or too old (30 days): erased before anything shows.
                openCopy(Owner(stored.issuer, stored.user.id))
                currentClient = ApiClient(stored.issuer, generation, this, httpClient)
                _state.value = SessionState.SignedIn(stored.user, stored.issuer)
            }
        }
        retryPendingRevocation()
    }

    /**
     * Starts the session the instance just granted: loads the person's profile, then stores the
     * session and shows the signed-in app. On failure nothing is kept and the tokens are revoked.
     */
    suspend fun signIn(
        issuer: String,
        tokenEndpoint: String,
        revocationEndpoint: String?,
        clientId: String,
        tokens: TokenResponse,
    ): ApiResult<SignedInUser> {
        val now = clock()
        val draft =
            Session(
                issuer = issuer,
                tokenEndpoint = tokenEndpoint,
                revocationEndpoint = revocationEndpoint,
                clientId = clientId,
                accessToken = tokens.accessToken,
                accessTokenExpiresAt = expiry(now, tokens.expiresIn),
                refreshToken = tokens.refreshToken,
                refreshTokenObtainedAt = now,
                user = SignedInUser(id = "", name = "", email = ""),
            )
        val draftGeneration =
            commitLock.withLock {
                currentClient?.cancelAll()
                currentClient = null
                session = draft
                ++generation
            }
        val client = ApiClient(issuer, draftGeneration, this, httpClient)
        val result = client.me()
        val failed =
            commitLock.withLock {
                val current = session
                if (draftGeneration != generation || current == null) return ApiResult.Discarded
                if (result is ApiResult.Success) {
                    val signedIn = current.copy(user = result.value)
                    openCopy(Owner(issuer, result.value.id))
                    store.saveSession(signedIn)
                    session = signedIn
                    currentClient = client
                    _state.value = SessionState.SignedIn(signedIn.user, issuer)
                    return result
                }
                session = null
                generation++
                current
            }
        revoke(failed)
        return result
    }

    /** Access for a request of [requestGeneration], renewed first when it has expired. */
    internal suspend fun access(requestGeneration: Int): Access {
        val current = commitLock.withLock { session?.takeIf { requestGeneration == generation } } ?: return Access.Discarded
        if (clock() < current.accessTokenExpiresAt) return Access.Token(current.accessToken)
        return refresh(requestGeneration, current.accessToken)
    }

    /**
     * Renews access after the instance refused [staleAccessToken]. When several requests ask at
     * once, only the first one reaches the instance: the others find the access already renewed.
     * Monica ends a session whose refresh token is presented twice, so this matters.
     */
    internal suspend fun refresh(
        requestGeneration: Int,
        staleAccessToken: String,
    ): Access =
        renewLock.withLock {
            val current = commitLock.withLock { session?.takeIf { requestGeneration == generation } } ?: return Access.Discarded
            if (current.accessToken != staleAccessToken) return Access.Token(current.accessToken)

            val result = tokenClient.refresh(current.tokenEndpoint, current.clientId, current.refreshToken)
            // Not cancellable: the instance has already rotated the refresh token, so the new one
            // must be saved, and an ended session must be erased, even if the screen went away.
            withContext(NonCancellable) {
                commitLock.withLock {
                    if (requestGeneration != generation) return@withContext Access.Discarded
                    when (result) {
                        is TokenResult.Success -> {
                            val now = clock()
                            val renewed =
                                current.copy(
                                    accessToken = result.tokens.accessToken,
                                    accessTokenExpiresAt = expiry(now, result.tokens.expiresIn),
                                    refreshToken = result.tokens.refreshToken,
                                    refreshTokenObtainedAt = now,
                                )
                            store.saveSession(renewed)
                            session = renewed
                            Access.Token(renewed.accessToken)
                        }

                        TokenResult.Refused -> {
                            val expired = clock() - current.refreshTokenObtainedAt >= REFRESH_TOKEN_LIFETIME
                            endLocked(if (expired) SignOutReason.Expired else SignOutReason.Revoked)
                            Access.Ended
                        }

                        is TokenResult.Failed -> {
                            Access.Failed(result.error)
                        }
                    }
                }
            }
        }

    /** The instance refused even renewed access: the session is over. */
    internal suspend fun rejected(requestGeneration: Int) {
        withContext(NonCancellable) {
            commitLock.withLock { if (requestGeneration == generation) endLocked(SignOutReason.Revoked) }
        }
    }

    /** True while [requestGeneration] is the current one. */
    internal suspend fun isCurrent(requestGeneration: Int): Boolean = commitLock.withLock { requestGeneration == generation }

    /**
     * Signs out on this phone right away, cancels everything still running for the session and any
     * pending sign-in, then tells the instance to end the session. Offline, the instance is told at
     * a later start. Also used before switching instance.
     */
    suspend fun logOut() {
        // Not cancellable: a screen closing mid-way must not leave a session half ended.
        val ended =
            withContext(NonCancellable) {
                commitLock.withLock {
                    val current = session
                    store.clearPendingSignIn()
                    endLocked(SignOutReason.LoggedOut)
                    current
                }
            }
        revoke(ended)
    }

    /**
     * Every session end goes through here. The offline copy is erased before [SignedOut] is
     * published, so the welcome screen never shows while the session's data is still on the phone.
     */
    private suspend fun endLocked(reason: SignOutReason) =
        // Not cancellable: whoever triggered the end (a screen's request) may be cancelled meanwhile,
        // and an end must never stop between clearing the session and erasing the copy.
        withContext(NonCancellable) {
            generation++
            session = null
            currentClient?.cancelAll()
            currentClient = null
            store.clearSession()
            // A failure never stops the sign-out: the key is destroyed first, and the next start
            // sweeps what is left.
            try {
                offlineCopy.erase()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            _state.value = SessionState.SignedOut(reason)
        }

    /**
     * Opens the offline copy for [owner]. A copy that cannot be opened (full disk, broken Keystore)
     * never stops the session: it is erased, and the app works online without one.
     */
    private suspend fun openCopy(owner: Owner) {
        try {
            offlineCopy.openOrErase(owner, clock())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            try {
                offlineCopy.erase()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    /** The instance answered a request of the session: the offline copy is still confirmed. */
    internal suspend fun contacted() {
        offlineCopy.contacted(clock())
    }

    private suspend fun revoke(ended: Session?) {
        val endpoint = ended?.revocationEndpoint ?: return
        if (!tokenClient.revoke(endpoint, ended.clientId, ended.refreshToken)) {
            store.savePendingRevocation(PendingRevocation(endpoint, ended.clientId, ended.refreshToken, clock()))
        }
    }

    private suspend fun retryPendingRevocation() {
        val pending = store.pendingRevocation() ?: return
        // After 30 days the refresh token has expired anyway.
        if (clock() - pending.createdAt >= REFRESH_TOKEN_LIFETIME ||
            tokenClient.revoke(pending.revocationEndpoint, pending.clientId, pending.refreshToken)
        ) {
            store.clearPendingRevocation()
        }
    }

    /** Renews a minute early, so a request never leaves with access about to expire. */
    private fun expiry(
        now: Long,
        expiresInSeconds: Long,
    ): Long = now + TimeUnit.SECONDS.toMillis(expiresInSeconds) - TimeUnit.MINUTES.toMillis(1)

    private companion object {
        val REFRESH_TOKEN_LIFETIME = TimeUnit.DAYS.toMillis(30)
    }
}
