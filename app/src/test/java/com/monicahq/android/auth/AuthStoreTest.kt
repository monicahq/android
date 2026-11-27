package com.monicahq.android.auth

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.monicahq.android.instance.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AuthStoreTest {
    private val dataStore = FakePreferencesDataStore()
    private val store = AuthStore(dataStore, FakeTokenCipher())

    private val session =
        Session(
            issuer = "https://monica.example.com",
            tokenEndpoint = "https://monica.example.com/oauth/token",
            revocationEndpoint = "https://monica.example.com/oauth/revoke",
            clientId = "client",
            accessToken = "secret-access",
            accessTokenExpiresAt = 1_000,
            refreshToken = "secret-refresh",
            refreshTokenObtainedAt = 500,
            user = SignedInUser("7", "Jane Doe", "jane@example.com"),
        )

    private val pending =
        PendingSignIn(
            issuer = "https://monica.example.com",
            clientId = "client",
            redirectUri = REDIRECT_URI,
            tokenEndpoint = "https://monica.example.com/oauth/token",
            revocationEndpoint = null,
            state = "secret-state",
            codeVerifier = "secret-verifier",
            createdAt = 1,
            intent = SignInIntent.LogIn,
        )

    private val revocation = PendingRevocation("https://monica.example.com/oauth/revoke", "client", "secret-refresh", 1)

    @Test
    fun `nothing is stored at first`() =
        runTest {
            assertNull(store.session())
            assertNull(store.pendingSignIn())
            assertNull(store.pendingRevocation())
        }

    @Test
    fun `the session is saved, read back and cleared`() =
        runTest {
            store.saveSession(session)
            assertEquals(session, store.session())

            store.clearSession()
            assertNull(store.session())
        }

    @Test
    fun `the pending sign-in is saved, read back and cleared`() =
        runTest {
            store.savePendingSignIn(pending)
            assertEquals(pending, store.pendingSignIn())

            store.clearPendingSignIn()
            assertNull(store.pendingSignIn())
        }

    @Test
    fun `the pending revocation is saved, read back and cleared`() =
        runTest {
            store.savePendingRevocation(revocation)
            assertEquals(revocation, store.pendingRevocation())

            store.clearPendingRevocation()
            assertNull(store.pendingRevocation())
        }

    @Test
    fun `values are stored encrypted`() =
        runTest {
            store.saveSession(session)

            val stored =
                dataStore.data
                    .first()
                    .asMap()
                    .values
                    .joinToString()
            assertFalse(stored.contains("secret-access"))
        }

    @Test
    fun `a value that cannot be decrypted is deleted and read as absent`() =
        runTest {
            store.saveSession(session)
            dataStore.edit { it[stringPreferencesKey("session")] = "bm90IGVuY3J5cHRlZA==" }

            assertNull(store.session())
            assertNull(dataStore.data.first()[stringPreferencesKey("session")])
        }

    @Test
    fun `toString hides tokens, states and verifiers`() {
        val text = listOf(session, pending, revocation, TokenResponse("secret-access", "secret-refresh", 3600)).joinToString()

        assertFalse(text.contains("secret"))
    }
}
