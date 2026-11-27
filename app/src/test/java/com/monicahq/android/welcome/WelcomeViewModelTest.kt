package com.monicahq.android.welcome

import com.monicahq.android.FakeMonica
import com.monicahq.android.MainDispatcherRule
import com.monicahq.android.TestGraph
import com.monicahq.android.auth.AuthError
import com.monicahq.android.auth.REDIRECT_URI
import com.monicahq.android.auth.SessionState
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

class WelcomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monica = FakeMonica()
    private val graph = TestGraph()

    @After
    fun tearDown() = monica.close()

    /** A ViewModel whose state is collected, as the screen does. */
    private suspend fun TestScope.viewModel(): WelcomeViewModel {
        graph.instanceRepository.setUrl(monica.url)
        graph.sessionManager.load()
        return WelcomeViewModel(graph.instanceRepository, graph.signInManager, graph.sessionManager).also { viewModel ->
            backgroundScope.launch(mainDispatcherRule.dispatcher) { viewModel.uiState.collect {} }
        }
    }

    /** Waits for work running on OkHttp's threads to come back. */
    private suspend fun WelcomeViewModel.awaitState(predicate: (WelcomeUiState) -> Boolean): WelcomeUiState =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(5_000) { uiState.first(predicate) }
        }

    @Test
    fun `starts idle on the saved instance`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(WelcomeUiState(instanceUrl = monica.url), viewModel.uiState.value)
        }

    @Test
    fun `the default instance is shown when none was saved`() =
        runTest {
            graph.sessionManager.load()
            val viewModel = WelcomeViewModel(graph.instanceRepository, graph.signInManager, graph.sessionManager)
            backgroundScope.launch(mainDispatcherRule.dispatcher) { viewModel.uiState.collect {} }

            assertEquals(DEFAULT_INSTANCE_URL, viewModel.uiState.value.instanceUrl)
        }

    @Test
    fun `logging in shows progress, then asks to open the sign-in page`() =
        runTest {
            val viewModel = viewModel()

            viewModel.logIn()
            assertEquals(SignInStatus.Starting, viewModel.uiState.value.status)

            val state = viewModel.awaitState { it.browserUrl != null }
            assertEquals(SignInStatus.Starting, state.status)
            assertTrue(checkNotNull(state.browserUrl).startsWith("${monica.url}/oauth/authorize"))
        }

    @Test
    fun `a second tap while starting does nothing`() =
        runTest {
            val viewModel = viewModel()

            viewModel.logIn()
            viewModel.signUp()
            viewModel.awaitState { it.browserUrl != null }

            assertEquals(1, monica.requestsTo("/api/v1/status").size)
        }

    @Test
    fun `a failed start shows why and lets the person try again`() =
        runTest {
            val viewModel = viewModel()
            monica.overrides["/api/v1/status"] = { FakeMonica.json("<html></html>") }

            viewModel.logIn()
            val state = viewModel.awaitState { it.message != null }

            assertEquals(SignInStatus.Idle, state.status)
            assertEquals(AuthError.NotMonica, state.message)
        }

    @Test
    fun `signing up on a closed instance explains it`() =
        runTest {
            monica.registrationEnabled = false
            val viewModel = viewModel()

            viewModel.signUp()

            assertEquals(AuthError.RegistrationClosed, viewModel.awaitState { it.message != null }.message)
            assertNull(viewModel.uiState.value.browserUrl)
        }

    @Test
    fun `signing up asks for the registration page`() =
        runTest {
            val viewModel = viewModel()

            viewModel.signUp()

            val url = checkNotNull(viewModel.awaitState { it.browserUrl != null }.browserUrl).toHttpUrl()
            assertEquals("consent create", url.queryParameter("prompt"))
        }

    @Test
    fun `the answer from the browser signs the person in`() =
        runTest {
            val viewModel = viewModel()
            viewModel.logIn()
            val url = checkNotNull(viewModel.awaitState { it.browserUrl != null }.browserUrl)
            viewModel.onBrowserOpened()
            assertNull(viewModel.uiState.value.browserUrl)

            viewModel.onCallback(graph.callback(url))
            assertEquals(SignInStatus.Completing, viewModel.uiState.value.status)

            val state = viewModel.awaitState { it.status == SignInStatus.Idle }
            assertNull(state.message)
            assertTrue(graph.sessionManager.state.value is SessionState.SignedIn)
        }

    @Test
    fun `a failed answer shows why`() =
        runTest {
            val viewModel = viewModel()
            viewModel.logIn()
            viewModel.awaitState { it.browserUrl != null }
            viewModel.onBrowserOpened()
            val state = checkNotNull(graph.store.pendingSignIn()).state

            viewModel.onCallback("$REDIRECT_URI?error=access_denied&state=$state")

            assertEquals(AuthError.Denied, viewModel.awaitState { it.message != null }.message)
        }

    @Test
    fun `coming back without an answer says the sign-in was cancelled`() =
        runTest {
            val viewModel = viewModel()
            viewModel.logIn()
            viewModel.awaitState { it.browserUrl != null }
            viewModel.onBrowserOpened()

            viewModel.onResume()

            val state = viewModel.awaitState { it.message != null }
            assertEquals(AuthError.Cancelled, state.message)
            assertEquals(SignInStatus.Idle, state.status)
        }

    @Test
    fun `an answer arriving after coming back still signs the person in`() =
        runTest {
            val viewModel = viewModel()
            viewModel.signUp()
            val url = checkNotNull(viewModel.awaitState { it.browserUrl != null }.browserUrl)
            viewModel.onBrowserOpened()
            // Back in the app to wait for the confirmation email.
            viewModel.onResume()
            viewModel.awaitState { it.message != null }

            viewModel.onCallback(graph.callback(url))

            assertNull(viewModel.awaitState { it.status == SignInStatus.Idle && it.message == null }.message)
            assertTrue(graph.sessionManager.state.value is SessionState.SignedIn)
        }

    @Test
    fun `coming back from signing up explains how to finish`() =
        runTest {
            val viewModel = viewModel()
            viewModel.signUp()
            viewModel.awaitState { it.browserUrl != null }
            viewModel.onBrowserOpened()

            viewModel.onResume()

            assertEquals(AuthError.SignUpUnfinished, viewModel.awaitState { it.message != null }.message)
        }

    @Test
    fun `a message from before signing in is gone after signing out`() =
        runTest {
            val viewModel = viewModel()
            viewModel.logIn()
            viewModel.awaitState { it.browserUrl != null }
            viewModel.onBrowserOpened()
            viewModel.onResume()
            viewModel.awaitState { it.message != null }

            graph.signIn(monica)
            graph.sessionManager.logOut()

            assertNull(viewModel.awaitState { graph.sessionManager.state.value is SessionState.SignedOut }.message)
        }

    @Test
    fun `resuming without having opened the browser changes nothing`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onResume()

            assertEquals(WelcomeUiState(instanceUrl = monica.url), viewModel.uiState.value)
        }

    @Test
    fun `a session ended by the instance is explained`() =
        runTest {
            graph.signIn(monica)
            graph.now += TimeUnit.HOURS.toMillis(1)
            monica.validRefreshToken = null
            checkNotNull(graph.sessionManager.apiClient()).me()

            val viewModel = WelcomeViewModel(graph.instanceRepository, graph.signInManager, graph.sessionManager)
            backgroundScope.launch(mainDispatcherRule.dispatcher) { viewModel.uiState.collect {} }

            assertEquals(AuthError.SessionRevoked, viewModel.uiState.value.message)
        }

    @Test
    fun `an expired session is explained`() =
        runTest {
            graph.signIn(monica)
            graph.now += TimeUnit.DAYS.toMillis(31)
            monica.validRefreshToken = null
            checkNotNull(graph.sessionManager.apiClient()).me()

            val viewModel = WelcomeViewModel(graph.instanceRepository, graph.signInManager, graph.sessionManager)
            backgroundScope.launch(mainDispatcherRule.dispatcher) { viewModel.uiState.collect {} }

            assertEquals(AuthError.SessionExpired, viewModel.uiState.value.message)
        }
}
