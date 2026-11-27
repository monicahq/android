package com.monicahq.android.auth

import com.monicahq.android.FakeMonica
import com.monicahq.android.TestGraph
import com.monicahq.android.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SessionManagerTest {
    private val monica = FakeMonica()
    private val graph = TestGraph()
    private val sessions get() = graph.sessionManager

    @After
    fun tearDown() = monica.close()

    private fun client() = checkNotNull(sessions.apiClient())

    @Test
    fun `loading without a stored session is signed out`() =
        runTest {
            assertEquals(SessionState.Loading, sessions.state.value)

            sessions.load()

            assertEquals(SessionState.SignedOut(), sessions.state.value)
            assertNull(sessions.apiClient())
        }

    @Test
    fun `a stored session is restored at start without its tokens`() =
        runTest {
            graph.signIn(monica)
            val restarted = SessionManager(graph.store, graph.tokenClient, graph.http, graph.offlineCopy, clock = { graph.now })

            restarted.load()

            val state = restarted.state.value
            assertEquals(SessionState.SignedIn(SignedInUser("7", "Jane Doe", "jane@example.com"), monica.url), state)
            assertFalse(state.toString().contains("access-"))
            assertEquals(monica.url, restarted.apiClient()?.instanceUrl)
        }

    @Test
    fun `expired access is renewed before the request`() =
        runTest {
            graph.signIn(monica)
            graph.now += TimeUnit.HOURS.toMillis(1)
            monica.requests.clear()

            assertTrue(client().me() is ApiResult.Success)

            assertEquals(listOf("/oauth/token", "/api/v1/me"), monica.requests.map { it.url.encodedPath })
            assertEquals("refresh-1", monica.form(monica.requests.first())["refresh_token"])
            assertEquals("access-2", graph.store.session()?.accessToken)
            assertEquals("refresh-2", graph.store.session()?.refreshToken)
        }

    @Test
    fun `twenty requests refused at once renew access once`() =
        runTest {
            graph.signIn(monica)
            // The instance moves on to new access, as when the old one expired early.
            monica.validAccessToken = "rotated-elsewhere"
            monica.tokenDelayMillis = 200
            monica.requests.clear()

            val results = (1..20).map { async { client().me() } }.awaitAll()

            assertTrue(results.all { it is ApiResult.Success })
            assertEquals(1, monica.requestsTo("/oauth/token").size)
            assertTrue(sessions.state.value is SessionState.SignedIn)
        }

    @Test
    fun `offline during renewal keeps the session`() =
        runTest {
            graph.signIn(monica)
            graph.now += TimeUnit.HOURS.toMillis(1)
            monica.close()

            assertEquals(ApiResult.Failed(AuthError.Unreachable), client().me())
            assertTrue(sessions.state.value is SessionState.SignedIn)
            assertEquals("refresh-1", graph.store.session()?.refreshToken)
        }

    @Test
    fun `a failing instance during renewal keeps the session`() =
        runTest {
            graph.signIn(monica)
            graph.now += TimeUnit.HOURS.toMillis(1)
            monica.overrides["/oauth/token"] = { FakeMonica.json("<html>maintenance</html>", code = 503) }

            assertEquals(ApiResult.Failed(AuthError.ServerError), client().me())
            assertTrue(sessions.state.value is SessionState.SignedIn)
        }

    @Test
    fun `a refused renewal ends the session as revoked`() =
        runTest {
            graph.signIn(monica)
            graph.now += TimeUnit.HOURS.toMillis(1)
            monica.validRefreshToken = null

            assertEquals(ApiResult.SessionEnded, client().me())
            assertEquals(SessionState.SignedOut(SignOutReason.Revoked), sessions.state.value)
            assertNull(graph.store.session())
            assertNull(sessions.apiClient())
        }

    @Test
    fun `a refused renewal after 30 days ends the session as expired`() =
        runTest {
            graph.signIn(monica)
            graph.now += TimeUnit.DAYS.toMillis(31)
            monica.validRefreshToken = null

            assertEquals(ApiResult.SessionEnded, client().me())
            assertEquals(SessionState.SignedOut(SignOutReason.Expired), sessions.state.value)
        }

    @Test
    fun `a renewal that finishes after logging out is discarded`() =
        runTest {
            graph.signIn(monica)
            graph.now += TimeUnit.HOURS.toMillis(1)
            monica.tokenDelayMillis = 300
            val client = client()

            val request = async(Dispatchers.Default) { client.me() }
            Thread.sleep(100)
            sessions.logOut()

            assertEquals(ApiResult.Discarded, request.await())
            assertNull(graph.store.session())
            assertEquals(SessionState.SignedOut(SignOutReason.LoggedOut), sessions.state.value)
        }

    @Test
    fun `a crash before the renewed session is saved leaves the old one whole`() =
        runTest {
            graph.signIn(monica)
            val before = graph.store.session()
            graph.now += TimeUnit.HOURS.toMillis(1)
            monica.overrides["/oauth/token"] = { FakeMonica.json("""{"access_token":"half"}""") }

            assertEquals(ApiResult.Failed(AuthError.ServerError), client().me())
            assertEquals(before, graph.store.session())
        }

    @Test
    fun `logging out clears the session at once and revokes it on the instance`() =
        runTest {
            graph.signIn(monica)

            sessions.logOut()

            assertEquals(SessionState.SignedOut(SignOutReason.LoggedOut), sessions.state.value)
            assertNull(graph.store.session())
            val form = monica.form(monica.requestsTo("/oauth/revoke").single())
            assertEquals("refresh-1", form["token"])
            assertEquals("refresh_token", form["token_type_hint"])
            assertEquals(FakeMonica.CLIENT_ID, form["client_id"])
            assertNull(graph.store.pendingRevocation())
        }

    @Test
    fun `logging out offline revokes at the next start`() =
        runTest {
            graph.signIn(monica)
            monica.overrides["/oauth/revoke"] = { FakeMonica.json("{}", code = 503) }

            sessions.logOut()

            assertEquals("refresh-1", graph.store.pendingRevocation()?.refreshToken)
            monica.overrides.remove("/oauth/revoke")
            SessionManager(graph.store, graph.tokenClient, graph.http, graph.offlineCopy, clock = { graph.now }).load()
            assertNull(graph.store.pendingRevocation())
            assertEquals(2, monica.requestsTo("/oauth/revoke").size)
        }

    @Test
    fun `a pending revocation is dropped after 30 days`() =
        runTest {
            graph.store.savePendingRevocation(PendingRevocation("${monica.url}/oauth/revoke", "client", "old", graph.now))
            graph.now += TimeUnit.DAYS.toMillis(30)

            sessions.load()

            assertNull(graph.store.pendingRevocation())
            assertTrue(monica.requestsTo("/oauth/revoke").isEmpty())
        }

    @Test
    fun `a pending revocation stays while the instance is unreachable`() =
        runTest {
            graph.store.savePendingRevocation(PendingRevocation("${monica.url}/oauth/revoke", "client", "old", graph.now))
            monica.close()

            sessions.load()

            assertEquals("old", graph.store.pendingRevocation()?.refreshToken)
        }

    @Test
    fun `without a revocation endpoint logging out only clears the phone`() =
        runTest {
            sessions.load()
            val tokens = TokenResponse("access-x", "refresh-x", 3600)
            monica.validAccessToken = "access-x"
            sessions.signIn(monica.url, "${monica.url}/oauth/token", revocationEndpoint = null, clientId = "client", tokens = tokens)

            sessions.logOut()

            assertTrue(monica.requestsTo("/oauth/revoke").isEmpty())
            assertNull(graph.store.pendingRevocation())
            assertNull(graph.store.session())
        }

    @Test
    fun `logging out also forgets a pending sign-in`() =
        runTest {
            graph.signIn(monica)
            graph.startSignIn(monica)

            sessions.logOut()

            assertNull(graph.store.pendingSignIn())
        }
}
