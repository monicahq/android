package com.monicahq.android.auth

import com.monicahq.android.FakeMonica
import com.monicahq.android.TestGraph
import com.monicahq.android.instance.CheckResult
import com.monicahq.android.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Nothing belonging to instance A ever reaches instance B (SC-005). */
class InstanceIsolationTest {
    private val a = FakeMonica(tokenPrefix = "a-")
    private val b = FakeMonica(tokenPrefix = "b-")
    private val graph = TestGraph()

    @After
    fun tearDown() {
        a.close()
        b.close()
    }

    private fun assertBReceivedNothingFromA() {
        assertTrue(b.requests.none { it.headers["Authorization"].orEmpty().contains("a-") })
        assertTrue(
            b.requests.none {
                it.body
                    ?.utf8()
                    .orEmpty()
                    .contains("a-")
            },
        )
    }

    @Test
    fun `switching during a request discards its result`() =
        runTest {
            graph.signIn(a)
            val client = checkNotNull(graph.sessionManager.apiClient())
            a.overrides["/api/v1/me"] = { FakeMonica.delayed(FakeMonica.json("{}"), 500) }

            val request = async(Dispatchers.Default) { client.me() }
            Thread.sleep(100)
            graph.sessionManager.logOut()
            graph.signIn(b)

            assertEquals(ApiResult.Discarded, request.await())
            assertBReceivedNothingFromA()
            assertEquals(b.url, graph.sessionManager.apiClient()?.instanceUrl)
        }

    @Test
    fun `switching during a renewal saves nothing and sends B no refresh token`() =
        runTest {
            graph.signIn(a)
            graph.now += TimeUnit.HOURS.toMillis(1)
            a.tokenDelayMillis = 400
            val client = checkNotNull(graph.sessionManager.apiClient())

            val request = async(Dispatchers.Default) { client.me() }
            Thread.sleep(100)
            graph.sessionManager.logOut()

            assertEquals(ApiResult.Discarded, request.await())
            assertNull(graph.store.session())
            graph.signIn(b)
            assertBReceivedNothingFromA()
            assertEquals(b.url, graph.store.session()?.issuer)
        }

    @Test
    fun `switching with a sign-in in progress makes its answer ignored`() =
        runTest {
            graph.sessionManager.load()
            val url = graph.startSignIn(a)

            graph.signInManager.cancel()

            assertEquals(CompleteResult.Ignored, graph.signInManager.complete(graph.callback(url)))
            assertTrue(a.requestsTo("/oauth/token").isEmpty())
            assertTrue(b.requests.isEmpty())
        }

    @Test
    fun `a failed check of B leaves the session on A untouched`() =
        runTest {
            graph.signIn(a)
            b.overrides["/api/v1/status"] = { FakeMonica.json("<html></html>") }

            assertTrue(graph.checker.check(b.url) is CheckResult.Failed)

            assertEquals(a.url, graph.store.session()?.issuer)
            assertTrue(graph.sessionManager.state.value is SessionState.SignedIn)
            assertBReceivedNothingFromA()
        }
}
