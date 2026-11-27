package com.monicahq.android.home

import com.monicahq.android.FakeMonica
import com.monicahq.android.MainDispatcherRule
import com.monicahq.android.TestGraph
import com.monicahq.android.auth.AuthError
import com.monicahq.android.auth.SessionState
import com.monicahq.android.auth.SignOutReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monica = FakeMonica()
    private val graph = TestGraph()

    @After
    fun tearDown() = monica.close()

    private suspend fun TestScope.viewModel(): HomeViewModel {
        graph.signIn(monica)
        return HomeViewModel(graph.sessionManager).also { viewModel ->
            backgroundScope.launch(mainDispatcherRule.dispatcher) { viewModel.uiState.collect {} }
        }
    }

    private suspend fun HomeViewModel.awaitState(predicate: (HomeUiState) -> Boolean): HomeUiState =
        withContext(Dispatchers.Default) { withTimeout(5_000) { uiState.first(predicate) } }

    @Test
    fun `shows the person and the instance`() =
        runTest {
            val viewModel = viewModel()

            assertEquals("Jane Doe", viewModel.uiState.value.name)
            assertEquals(monica.url, viewModel.uiState.value.instanceUrl)
        }

    @Test
    fun `the first state already shows the person`() =
        runTest {
            graph.signIn(monica)

            val viewModel = HomeViewModel(graph.sessionManager)

            assertEquals("Jane Doe", viewModel.uiState.value.name)
        }

    @Test
    fun `talks to the instance once when it opens`() =
        runTest {
            viewModel()

            withContext(Dispatchers.Default) { withTimeout(5_000) { while (monica.requestsTo("/api/v1/me").size < 2) Thread.sleep(10) } }
        }

    @Test
    fun `a problem reaching the instance is shown, then dismissed`() =
        runTest {
            val viewModel = viewModel()
            monica.overrides["/api/v1/me"] = { FakeMonica.json("{}", code = 500) }

            viewModel.refresh()
            assertEquals(AuthError.ServerError, viewModel.awaitState { it.error != null }.error)

            viewModel.dismissError()
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `signing out asks first`() =
        runTest {
            val viewModel = viewModel()

            viewModel.askSignOut()
            assertTrue(viewModel.uiState.value.confirmSignOut)

            viewModel.dismissSignOut()
            assertFalse(viewModel.uiState.value.confirmSignOut)
            assertTrue(graph.sessionManager.state.value is SessionState.SignedIn)
        }

    @Test
    fun `confirming signs out`() =
        runTest {
            val viewModel = viewModel()
            viewModel.askSignOut()

            viewModel.confirmSignOut()

            withContext(Dispatchers.Default) {
                withTimeout(5_000) { graph.sessionManager.state.first { it is SessionState.SignedOut } }
            }
            assertEquals(SessionState.SignedOut(SignOutReason.LoggedOut), graph.sessionManager.state.value)
            assertFalse(viewModel.uiState.value.confirmSignOut)
        }
}
