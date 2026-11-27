package com.monicahq.android.network

import com.monicahq.android.FakeMonica
import com.monicahq.android.TestGraph
import com.monicahq.android.auth.AuthError
import com.monicahq.android.auth.SessionState
import com.monicahq.android.auth.SignOutReason
import com.monicahq.android.auth.SignedInUser
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientTest {
    private val monica = FakeMonica()
    private val elsewhere = FakeMonica()
    private val graph = TestGraph()

    @After
    fun tearDown() {
        monica.close()
        elsewhere.close()
    }

    private suspend fun signedInClient(): ApiClient {
        graph.signIn(monica)
        monica.requests.clear()
        return checkNotNull(graph.sessionManager.apiClient())
    }

    @Test
    fun `the person is read with the session's access token`() =
        runTest {
            val client = signedInClient()

            assertEquals(ApiResult.Success(SignedInUser("7", "Jane Doe", "jane@example.com")), client.me())
            assertEquals("Bearer access-1", monica.requests.single().headers["Authorization"])
        }

    @Test
    fun `a 401 renews access once and retries`() =
        runTest {
            val client = signedInClient()
            monica.validAccessToken = "rotated-elsewhere"

            assertTrue(client.me() is ApiResult.Success)
            assertEquals(listOf("/api/v1/me", "/oauth/token", "/api/v1/me"), monica.requests.map { it.url.encodedPath })
            assertEquals("Bearer access-2", monica.requests.last().headers["Authorization"])
        }

    @Test
    fun `a second 401 ends the session without looping`() =
        runTest {
            val client = signedInClient()
            monica.overrides["/api/v1/me"] = { FakeMonica.json("""{"message":"Unauthenticated."}""", code = 401) }

            assertEquals(ApiResult.SessionEnded, client.me())
            assertEquals(3, monica.requests.size)
            assertEquals(SessionState.SignedOut(SignOutReason.Revoked), graph.sessionManager.state.value)
        }

    @Test
    fun `a 403 is not allowed and does not renew`() =
        runTest {
            val client = signedInClient()
            monica.overrides["/api/v1/me"] = { FakeMonica.json("{}", code = 403) }

            assertEquals(ApiResult.Failed(AuthError.NotAllowed), client.me())
            assertTrue(monica.requestsTo("/oauth/token").isEmpty())
        }

    @Test
    fun `a 500 is a server error and keeps the session`() =
        runTest {
            val client = signedInClient()
            monica.overrides["/api/v1/me"] = { FakeMonica.json("{}", code = 500) }

            assertEquals(ApiResult.Failed(AuthError.ServerError), client.me())
            assertTrue(graph.sessionManager.state.value is SessionState.SignedIn)
        }

    @Test
    fun `an unexpected body is a server error`() =
        runTest {
            val client = signedInClient()
            monica.overrides["/api/v1/me"] = { FakeMonica.json("<html></html>") }

            assertEquals(ApiResult.Failed(AuthError.ServerError), client.me())
        }

    @Test
    fun `offline keeps the session`() =
        runTest {
            val client = signedInClient()
            monica.close()

            assertEquals(ApiResult.Failed(AuthError.Unreachable), client.me())
            assertTrue(graph.sessionManager.state.value is SessionState.SignedIn)
        }

    @Test
    fun `a redirect to another server is not followed, so no token reaches it`() =
        runTest {
            val client = signedInClient()
            monica.overrides["/api/v1/me"] = {
                MockResponse
                    .Builder()
                    .code(302)
                    .addHeader("Location", "${elsewhere.url}/api/v1/me")
                    .build()
            }

            assertEquals(ApiResult.Failed(AuthError.Moved), client.me())
            assertTrue(elsewhere.requests.isEmpty())
        }

    @Test
    fun `a name made only of an email falls back to the email`() =
        runTest {
            val client = signedInClient()
            monica.overrides["/api/v1/me"] = { FakeMonica.json("""{"data":{"id":"7","attributes":{"email":"jane@example.com"}}}""") }

            assertEquals(ApiResult.Success(SignedInUser("7", "jane@example.com", "jane@example.com")), client.me())
        }
}
