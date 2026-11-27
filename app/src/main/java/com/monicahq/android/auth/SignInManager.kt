package com.monicahq.android.auth

import com.monicahq.android.instance.CheckResult
import com.monicahq.android.instance.InstanceChecker
import com.monicahq.android.network.ApiResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed interface StartResult {
    data class OpenBrowser(
        val url: String,
    ) : StartResult

    data class Failed(
        val error: AuthError,
    ) : StartResult
}

sealed interface CompleteResult {
    data object SignedIn : CompleteResult

    data class Failed(
        val error: AuthError,
    ) : CompleteResult

    /** Not an answer to the sign-in in progress: nothing was sent anywhere. */
    data object Ignored : CompleteResult
}

/**
 * Signs a person in through their instance's own sign-in page in the browser (OAuth 2.0
 * authorization code with PKCE). The attempt is stored before the browser opens, so it completes
 * even if Android stopped the app meanwhile, and it is used at most once.
 */
class SignInManager(
    private val store: AuthStore,
    private val checker: InstanceChecker,
    private val tokenClient: TokenEndpointClient,
    private val sessionManager: SessionManager,
    private val deviceName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    private val lock = Mutex()

    /** Checks [instanceUrl] again, then returns the address to open in the browser. */
    suspend fun start(
        instanceUrl: String,
        intent: SignInIntent,
    ): StartResult {
        val configuration =
            when (val check = checker.check(instanceUrl)) {
                is CheckResult.Failed -> return StartResult.Failed(check.error)
                is CheckResult.Compatible -> check.configuration
            }
        if (intent == SignInIntent.SignUp && !configuration.registrationEnabled) {
            return StartResult.Failed(AuthError.RegistrationClosed)
        }
        val pending =
            PendingSignIn(
                issuer = configuration.issuer,
                clientId = configuration.clientId,
                redirectUri = REDIRECT_URI,
                tokenEndpoint = configuration.tokenEndpoint,
                revocationEndpoint = configuration.revocationEndpoint,
                state = Pkce.state(random),
                codeVerifier = Pkce.verifier(random),
                createdAt = clock(),
                intent = intent,
            )
        lock.withLock { store.savePendingSignIn(pending) }
        return StartResult.OpenBrowser(authorizationUrl(configuration.authorizationEndpoint, configuration.scope, pending))
    }

    /** Finishes the sign-in with the address the browser opened the app with. */
    suspend fun complete(callbackUri: String): CompleteResult {
        val pending =
            lock.withLock {
                val pending = store.pendingSignIn() ?: return CompleteResult.Ignored
                // Used at most once, whatever the answer.
                store.clearPendingSignIn()
                pending
            }
        val callback = parseCallback(callbackUri)
        val state =
            when (callback) {
                is Callback.Code -> callback.state
                is Callback.Denied -> callback.state
                is Callback.Error -> callback.state
                Callback.Rejected -> null
            }
        if (state != pending.state || clock() - pending.createdAt > PENDING_LIFETIME) {
            return CompleteResult.Failed(AuthError.SignInFailed)
        }
        return when (callback) {
            is Callback.Denied -> CompleteResult.Failed(AuthError.Denied)
            is Callback.Code -> exchange(pending, callback)
            else -> CompleteResult.Failed(AuthError.SignInFailed)
        }
    }

    /** Drops the sign-in in progress, when switching instance. True when there was one. */
    suspend fun cancel(): Boolean =
        lock.withLock {
            store.pendingSignIn() ?: return false
            store.clearPendingSignIn()
            true
        }

    private suspend fun exchange(
        pending: PendingSignIn,
        callback: Callback.Code,
    ): CompleteResult {
        // RFC 9207: when the instance says who it is, it must be the one the sign-in started with.
        if (callback.issuer != null && callback.issuer.trimEnd('/') != pending.issuer) {
            return CompleteResult.Failed(AuthError.SignInFailed)
        }
        val tokens =
            when (
                val result =
                    tokenClient.exchangeCode(
                        tokenEndpoint = pending.tokenEndpoint,
                        clientId = pending.clientId,
                        redirectUri = pending.redirectUri,
                        code = callback.code,
                        codeVerifier = pending.codeVerifier,
                        deviceName = deviceName,
                    )
            ) {
                is TokenResult.Success -> result.tokens
                TokenResult.Refused -> return CompleteResult.Failed(AuthError.SignInFailed)
                is TokenResult.Failed -> return CompleteResult.Failed(result.error)
            }
        return when (
            val result =
                sessionManager.signIn(pending.issuer, pending.tokenEndpoint, pending.revocationEndpoint, pending.clientId, tokens)
        ) {
            is ApiResult.Success -> CompleteResult.SignedIn

            is ApiResult.Failed -> CompleteResult.Failed(result.error)

            ApiResult.SessionEnded -> CompleteResult.Failed(AuthError.SignInFailed)

            ApiResult.Discarded -> CompleteResult.Ignored

            // `/me` neither refuses input nor misses: either is an instance answering oddly.
            ApiResult.NotFound, is ApiResult.Invalid -> CompleteResult.Failed(AuthError.ServerError)
        }
    }

    private companion object {
        /** Long enough to create an account and confirm its email address. */
        val PENDING_LIFETIME = TimeUnit.HOURS.toMillis(1)
    }
}
