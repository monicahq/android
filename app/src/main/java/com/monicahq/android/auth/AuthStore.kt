package com.monicahq.android.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/** The person signed in, as `GET /api/v1/me` describes them. */
@Serializable
data class SignedInUser(
    val id: String,
    val name: String,
    val email: String,
)

/**
 * The signed-in state on this phone. [issuer] is the instance that issued it: its tokens are only
 * ever sent there. Replaced as a whole on renewal.
 */
@Serializable
data class Session(
    val issuer: String,
    val tokenEndpoint: String,
    val revocationEndpoint: String?,
    val clientId: String,
    val accessToken: String,
    val accessTokenExpiresAt: Long,
    val refreshToken: String,
    val refreshTokenObtainedAt: Long,
    val user: SignedInUser,
) {
    override fun toString(): String = "Session(issuer=$issuer, user=${user.id}, accessTokenExpiresAt=$accessTokenExpiresAt)"
}

/** Log in, or sign up first. */
enum class SignInIntent { LogIn, SignUp }

/** One sign-in attempt in progress, kept while the browser is open, even if Android stops the app. */
@Serializable
data class PendingSignIn(
    val issuer: String,
    val clientId: String,
    val redirectUri: String,
    val tokenEndpoint: String,
    val revocationEndpoint: String?,
    val state: String,
    val codeVerifier: String,
    val createdAt: Long,
    val intent: SignInIntent,
) {
    override fun toString(): String = "PendingSignIn(issuer=$issuer, intent=$intent, createdAt=$createdAt)"
}

/** A logout the instance has not heard about yet, because the phone was offline. */
@Serializable
data class PendingRevocation(
    val revocationEndpoint: String,
    val clientId: String,
    val refreshToken: String,
    val createdAt: Long,
) {
    override fun toString(): String = "PendingRevocation(revocationEndpoint=$revocationEndpoint, createdAt=$createdAt)"
}

/**
 * Keeps the session, the pending sign-in and the pending revocation, each encrypted with [cipher]
 * and written as one value, so a crash leaves either the old value or the new one. The file is
 * excluded from backups. A value that cannot be decrypted (key lost, file restored) is deleted and
 * read as absent.
 */
class AuthStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: TokenCipher,
) {
    suspend fun session(): Session? = read(SESSION, Session.serializer())

    suspend fun saveSession(session: Session) = write(SESSION, Session.serializer(), session)

    suspend fun clearSession() = clear(SESSION)

    suspend fun pendingSignIn(): PendingSignIn? = read(PENDING, PendingSignIn.serializer())

    suspend fun savePendingSignIn(pending: PendingSignIn) = write(PENDING, PendingSignIn.serializer(), pending)

    suspend fun clearPendingSignIn() = clear(PENDING)

    suspend fun pendingRevocation(): PendingRevocation? = read(REVOCATION, PendingRevocation.serializer())

    suspend fun savePendingRevocation(revocation: PendingRevocation) = write(REVOCATION, PendingRevocation.serializer(), revocation)

    suspend fun clearPendingRevocation() = clear(REVOCATION)

    private suspend fun <T> read(
        key: Preferences.Key<String>,
        serializer: KSerializer<T>,
    ): T? {
        val stored = dataStore.data.first()[key] ?: return null
        return runCatching {
            val plain = cipher.decrypt(Base64.getDecoder().decode(stored))
            json.decodeFromString(serializer, plain.toString(Charsets.UTF_8))
        }.getOrElse {
            clear(key)
            null
        }
    }

    private suspend fun <T> write(
        key: Preferences.Key<String>,
        serializer: KSerializer<T>,
        value: T,
    ) {
        val encrypted = cipher.encrypt(json.encodeToString(serializer, value).toByteArray(Charsets.UTF_8))
        dataStore.edit { it[key] = Base64.getEncoder().encodeToString(encrypted) }
    }

    private suspend fun clear(key: Preferences.Key<String>) {
        dataStore.edit { it.remove(key) }
    }

    private companion object {
        val SESSION = stringPreferencesKey("session")
        val PENDING = stringPreferencesKey("pending")
        val REVOCATION = stringPreferencesKey("revocation")
        val json = Json { ignoreUnknownKeys = true }
    }
}
