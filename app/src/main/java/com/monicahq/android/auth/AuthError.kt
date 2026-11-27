package com.monicahq.android.auth

import androidx.annotation.StringRes
import com.monicahq.android.R
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Everything that can go wrong while checking an instance, signing in or talking to it, in the
 * words the person reads. Exception messages are never kept: they can hold addresses or tokens.
 */
enum class AuthError(
    @param:StringRes val message: Int,
) {
    InvalidAddress(R.string.auth_error_invalid_address),
    HttpsRequired(R.string.auth_error_https_required),
    Unreachable(R.string.auth_error_unreachable),
    Timeout(R.string.auth_error_timeout),
    Certificate(R.string.auth_error_certificate),
    Moved(R.string.auth_error_moved),
    NotMonica(R.string.auth_error_not_monica),
    MonicaTooOld(R.string.auth_error_monica_too_old),
    AppTooOld(R.string.auth_error_app_too_old),
    NoAppSignIn(R.string.auth_error_no_app_sign_in),
    RegistrationClosed(R.string.auth_error_registration_closed),
    Cancelled(R.string.auth_error_cancelled),
    SignUpUnfinished(R.string.auth_error_sign_up_unfinished),
    Denied(R.string.auth_error_denied),
    SignInFailed(R.string.auth_error_sign_in_failed),
    SessionExpired(R.string.auth_error_session_expired),
    SessionRevoked(R.string.auth_error_session_revoked),
    NotAllowed(R.string.auth_error_not_allowed),
    ServerError(R.string.auth_error_server),
    ;

    companion object {
        /**
         * The error a failed network call stands for. When every address of a host fails, OkHttp
         * throws the first failure and attaches the others as suppressed: a certificate problem
         * on any of them is what the person needs to know.
         */
        fun from(throwable: Throwable): AuthError {
            val related = generateSequence(throwable) { it.cause }.flatMap { sequenceOf(it) + it.suppressed.asSequence() }
            if (related.any { it is SSLException }) return Certificate
            return when (throwable) {
                // InterruptedIOException covers OkHttp's call timeout.
                is SocketTimeoutException, is InterruptedIOException -> Timeout

                // Unknown host, refused connection, no route, connection reset.
                is IOException -> Unreachable

                else -> ServerError
            }
        }
    }
}
