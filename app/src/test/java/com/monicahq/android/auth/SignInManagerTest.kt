package com.monicahq.android.auth

import com.monicahq.android.FakeMonica
import com.monicahq.android.TestGraph
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SignInManagerTest {
    private val monica = FakeMonica()
    private val graph = TestGraph()

    @After
    fun tearDown() = monica.close()

    @Test
    fun `starting saves the attempt before returning the address`() =
        runTest {
            graph.sessionManager.load()

            val url = graph.startSignIn(monica)

            val pending = checkNotNull(graph.store.pendingSignIn())
            assertTrue(url.startsWith("${monica.url}/oauth/authorize?"))
            assertTrue(url.contains("state=${pending.state}"))
            assertEquals(monica.url, pending.issuer)
            assertEquals("${monica.url}/oauth/token", pending.tokenEndpoint)
        }

    @Test
    fun `starting on an incompatible instance fails without saving anything`() =
        runTest {
            val result = graph.signInManager.start("not an address", SignInIntent.LogIn)

            assertEquals(StartResult.Failed(AuthError.InvalidAddress), result)
            assertNull(graph.store.pendingSignIn())
        }

    @Test
    fun `signing up on an instance closed to new accounts does not open the browser`() =
        runTest {
            monica.registrationEnabled = false

            assertEquals(StartResult.Failed(AuthError.RegistrationClosed), graph.signInManager.start(monica.url, SignInIntent.SignUp))
            assertNull(graph.store.pendingSignIn())
        }

    @Test
    fun `the right answer exchanges the code, loads the person and saves the session`() =
        runTest {
            graph.sessionManager.load()
            val url = graph.startSignIn(monica)
            val pending = checkNotNull(graph.store.pendingSignIn())

            val result = graph.signInManager.complete(graph.callback(url))

            assertEquals(CompleteResult.SignedIn, result)
            val form = monica.form(monica.requestsTo("/oauth/token").single())
            assertEquals("authorization_code", form["grant_type"])
            assertEquals(FakeMonica.CLIENT_ID, form["client_id"])
            assertEquals(REDIRECT_URI, form["redirect_uri"])
            assertEquals(FakeMonica.CODE, form["code"])
            assertEquals(pending.codeVerifier, form["code_verifier"])
            assertEquals("Test phone", form["device_name"])
            val session = checkNotNull(graph.store.session())
            assertEquals(monica.url, session.issuer)
            assertEquals(graph.now + TimeUnit.MINUTES.toMillis(59), session.accessTokenExpiresAt)
            assertEquals(graph.now, session.refreshTokenObtainedAt)
            assertEquals(SignedInUser("7", "Jane Doe", "jane@example.com"), session.user)
            assertEquals(SessionState.SignedIn(session.user, monica.url), graph.sessionManager.state.value)
            assertNull(graph.store.pendingSignIn())
        }

    @Test
    fun `a wrong state fails and sends nothing`() =
        runTest {
            graph.startSignIn(monica)
            monica.requests.clear()

            val result = graph.signInManager.complete("$REDIRECT_URI?code=${FakeMonica.CODE}&state=forged")

            assertEquals(CompleteResult.Failed(AuthError.SignInFailed), result)
            assertTrue(monica.requests.isEmpty())
            assertNull(graph.store.pendingSignIn())
        }

    @Test
    fun `an answer without a sign-in in progress is ignored`() =
        runTest {
            assertEquals(CompleteResult.Ignored, graph.signInManager.complete("$REDIRECT_URI?code=c&state=s"))
            assertTrue(monica.requests.isEmpty())
        }

    @Test
    fun `an attempt older than an hour has expired`() =
        runTest {
            val url = graph.startSignIn(monica)
            monica.requests.clear()
            graph.now += TimeUnit.MINUTES.toMillis(61)

            assertEquals(CompleteResult.Failed(AuthError.SignInFailed), graph.signInManager.complete(graph.callback(url)))
            assertTrue(monica.requests.isEmpty())
            assertNull(graph.store.pendingSignIn())
        }

    @Test
    fun `an attempt still completes after the time it takes to confirm an email`() =
        runTest {
            graph.sessionManager.load()
            val url = graph.startSignIn(monica, SignInIntent.SignUp)
            graph.now += TimeUnit.MINUTES.toMillis(50)

            assertEquals(CompleteResult.SignedIn, graph.signInManager.complete(graph.callback(url)))
        }

    @Test
    fun `the same answer twice signs in once`() =
        runTest {
            graph.sessionManager.load()
            val callback = graph.callback(graph.startSignIn(monica))

            assertEquals(CompleteResult.SignedIn, graph.signInManager.complete(callback))
            assertEquals(CompleteResult.Ignored, graph.signInManager.complete(callback))
            assertEquals(1, monica.requestsTo("/oauth/token").size)
        }

    @Test
    fun `refusing access on the consent page says so`() =
        runTest {
            val url = graph.startSignIn(monica)
            val state = checkNotNull(graph.store.pendingSignIn()).state

            val result = graph.signInManager.complete("$REDIRECT_URI?error=access_denied&state=$state")

            assertEquals(CompleteResult.Failed(AuthError.Denied), result)
            assertTrue(url.isNotEmpty())
        }

    @Test
    fun `another error from the instance is a failed sign-in`() =
        runTest {
            graph.startSignIn(monica)
            val state = checkNotNull(graph.store.pendingSignIn()).state

            assertEquals(
                CompleteResult.Failed(AuthError.SignInFailed),
                graph.signInManager.complete("$REDIRECT_URI?error=server_error&state=$state"),
            )
        }

    @Test
    fun `a refused code is a failed sign-in`() =
        runTest {
            val url = graph.startSignIn(monica)

            assertEquals(CompleteResult.Failed(AuthError.SignInFailed), graph.signInManager.complete(graph.callback(url, code = "used")))
            assertNull(graph.store.session())
        }

    @Test
    fun `an answer naming another instance is refused before the exchange`() =
        runTest {
            val url = graph.startSignIn(monica)
            monica.requests.clear()

            val result = graph.signInManager.complete(graph.callback(url) + "&iss=https%3A%2F%2Fevil.example")

            assertEquals(CompleteResult.Failed(AuthError.SignInFailed), result)
            assertTrue(monica.requests.isEmpty())
        }

    @Test
    fun `an unreachable token endpoint says so`() =
        runTest {
            val url = graph.startSignIn(monica)
            monica.close()

            assertEquals(CompleteResult.Failed(AuthError.Unreachable), graph.signInManager.complete(graph.callback(url)))
        }

    @Test
    fun `a profile that cannot be loaded keeps nothing and revokes the tokens`() =
        runTest {
            graph.sessionManager.load()
            val url = graph.startSignIn(monica)
            monica.overrides["/api/v1/me"] = { FakeMonica.json("{}", code = 500) }

            assertEquals(CompleteResult.Failed(AuthError.ServerError), graph.signInManager.complete(graph.callback(url)))
            assertNull(graph.store.session())
            assertEquals(SessionState.SignedOut(), graph.sessionManager.state.value)
            assertEquals("refresh-1", monica.form(monica.requestsTo("/oauth/revoke").single())["token"])
        }

    @Test
    fun `coming back without an answer cancels the attempt`() =
        runTest {
            graph.startSignIn(monica)

            assertTrue(graph.signInManager.cancel())
            assertNull(graph.store.pendingSignIn())
            assertFalse(graph.signInManager.cancel())
        }

    @Test
    fun `a sign-in started before Android stopped the app completes after`() =
        runTest {
            val url = graph.startSignIn(monica)
            val restarted =
                SignInManager(graph.store, graph.checker, graph.tokenClient, graph.sessionManager, "Test phone", clock = { graph.now })
            graph.sessionManager.load()

            assertEquals(CompleteResult.SignedIn, restarted.complete(graph.callback(url)))
        }
}
