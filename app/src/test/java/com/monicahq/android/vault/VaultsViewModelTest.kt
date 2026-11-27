package com.monicahq.android.vault

import com.monicahq.android.FakeMonica
import com.monicahq.android.MainDispatcherRule
import com.monicahq.android.TestGraph
import com.monicahq.android.auth.AuthError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

class VaultsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monica = FakeMonica()
    private val graph = TestGraph()

    @After
    fun tearDown() = monica.close()

    private suspend fun viewModel(): VaultsViewModel {
        graph.signIn(monica)
        return VaultsViewModel(graph.vaultRepository)
    }

    private suspend fun VaultsViewModel.awaitState(predicate: (VaultsUiState) -> Boolean): VaultsUiState =
        withContext(Dispatchers.Default) { withTimeout(5_000) { uiState.first(predicate) } }

    private fun VaultsUiState.names(): List<String> = (content as VaultsContent.Loaded).vaults.map { it.name }

    private fun failNextLoads() {
        monica.overrides["/api/v1/vaults"] = { FakeMonica.json("{}", code = 500) }
    }

    private fun answerLoadsAgain() {
        monica.overrides.remove("/api/v1/vaults")
    }

    @Test
    fun `starts loading, then shows the vaults`() =
        runTest {
            monica.addVaults("Personal", "Family")
            val viewModel = viewModel()
            assertEquals(VaultsContent.Loading, viewModel.uiState.value.content)

            viewModel.onShown()

            assertEquals(listOf("Personal", "Family"), viewModel.awaitState { it.content is VaultsContent.Loaded }.names())
        }

    @Test
    fun `an account with no vault shows the empty list`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onShown()

            assertEquals(VaultsContent.Loaded(emptyList()), viewModel.awaitState { it.content !is VaultsContent.Loading }.content)
        }

    @Test
    fun `a first load that fails shows the error, and trying again loads`() =
        runTest {
            val viewModel = viewModel()
            failNextLoads()

            viewModel.onShown()
            assertEquals(VaultsContent.Failed(AuthError.ServerError), viewModel.awaitState { it.content is VaultsContent.Failed }.content)

            answerLoadsAgain()
            monica.addVaults("Personal")
            viewModel.tryAgain()
            assertEquals(VaultsContent.Loading, viewModel.uiState.value.content)
            assertEquals(listOf("Personal"), viewModel.awaitState { it.content is VaultsContent.Loaded }.names())
        }

    @Test
    fun `showing the screen again reloads quietly`() =
        runTest {
            monica.addVaults("Personal")
            val viewModel = viewModel()
            viewModel.onShown()
            viewModel.awaitState { it.content is VaultsContent.Loaded }

            monica.addVaults("Family")
            viewModel.onShown()
            // The list stays while it reloads.
            assertTrue(viewModel.uiState.value.content is VaultsContent.Loaded)
            assertEquals(
                listOf("Personal", "Family"),
                viewModel.awaitState { it.content is VaultsContent.Loaded && it.names().size == 2 }.names(),
            )
        }

    @Test
    fun `a quiet reload that fails keeps the list, marked not up to date`() =
        runTest {
            monica.addVaults("Personal")
            graph.now = 1_800_000_000_000L
            val viewModel = viewModel()
            viewModel.onShown()
            val loaded = viewModel.awaitState { it.content is VaultsContent.Loaded }
            failNextLoads()

            viewModel.onShown()
            val stale = viewModel.awaitState { it.staleSince != null }

            assertEquals(loaded.content, stale.content)
            assertEquals(graph.now, stale.staleSince)
            assertNull(stale.refreshError)
        }

    @Test
    fun `a pull to refresh shows progress and the new list`() =
        runTest {
            monica.addVaults("Personal")
            val viewModel = viewModel()
            viewModel.onShown()
            viewModel.awaitState { it.content is VaultsContent.Loaded }
            monica.vaults[0] = monica.vaults[0].copy(name = "Me")

            viewModel.refresh()
            assertTrue(viewModel.uiState.value.refreshing)

            val refreshed = viewModel.awaitState { !it.refreshing }
            assertEquals(listOf("Me"), refreshed.names())
            assertNull(refreshed.refreshError)
        }

    @Test
    fun `a failed pull to refresh keeps the list and says so once`() =
        runTest {
            monica.addVaults("Personal")
            val viewModel = viewModel()
            viewModel.onShown()
            viewModel.awaitState { it.content is VaultsContent.Loaded }
            failNextLoads()

            viewModel.refresh()
            val failed = viewModel.awaitState { !it.refreshing }

            assertEquals(listOf("Personal"), failed.names())
            assertEquals(AuthError.ServerError, failed.refreshError)
            viewModel.refreshErrorShown()
            assertNull(viewModel.uiState.value.refreshError)
        }

    @Test
    fun `an answer the instance should not give is a server error`() =
        runTest {
            val viewModel = viewModel()
            monica.overrides["/api/v1/vaults"] = { FakeMonica.json("""{"message":"No."}""", code = 422) }

            viewModel.onShown()

            assertEquals(VaultsContent.Failed(AuthError.ServerError), viewModel.awaitState { it.content is VaultsContent.Failed }.content)
        }

    @Test
    fun `a session ended by the instance changes nothing on screen`() =
        runTest {
            val viewModel = viewModel()
            monica.validAccessToken = null
            monica.validRefreshToken = null

            viewModel.refresh()
            val state = viewModel.awaitState { !it.refreshing }

            assertEquals(VaultsContent.Loading, state.content)
            assertNull(state.refreshError)
        }

    @Test
    fun `showing the screen while it loads does not load twice`() =
        runTest {
            val viewModel = viewModel()
            monica.overrides["/api/v1/vaults"] = { FakeMonica.delayed(FakeMonica.json("""{"data":[],"meta":{"total":0}}"""), 300) }

            viewModel.onShown()
            viewModel.onShown()
            viewModel.awaitState { it.content is VaultsContent.Loaded }

            assertEquals(1, monica.requestsTo("/api/v1/vaults").size)
            assertFalse(viewModel.uiState.value.refreshing)
        }

    @Test
    fun `the same failure on a second pull is said again`() =
        runTest {
            monica.addVaults("Personal")
            val viewModel = viewModel()
            viewModel.onShown()
            viewModel.awaitState { it.content is VaultsContent.Loaded }
            failNextLoads()
            viewModel.refresh()
            viewModel.awaitState { !it.refreshing }

            viewModel.refresh()

            // Cleared at once, so the screen sees a new failure when it comes.
            assertNull(viewModel.uiState.value.refreshError)
            assertEquals(AuthError.ServerError, viewModel.awaitState { !it.refreshing }.refreshError)
        }

    @Test
    fun `the copy shows at once, without loading, then the answer replaces it`() =
        runTest {
            monica.addVaults("Personal")
            val viewModel = viewModel()
            viewModel.onShown()
            viewModel.awaitState { it.content is VaultsContent.Loaded }
            monica.vaults[0] = monica.vaults[0].copy(name = "Me")
            monica.overrides["/api/v1/vaults"] = { request ->
                FakeMonica.delayed(
                    FakeMonica.json("""{"data":[{"id":"vault-1","attributes":{"name":"Me"}}],"meta":{"total":1}}"""),
                    300,
                )
            }

            // A new screen, as after the app restarts.
            val reopened = VaultsViewModel(graph.vaultRepository)
            val states = mutableListOf<VaultsUiState>()
            val collecting = launch(mainDispatcherRule.dispatcher) { reopened.uiState.collect { states += it } }
            reopened.onShown()
            reopened.awaitState { it.content is VaultsContent.Loaded && it.names() == listOf("Me") }
            collecting.cancel()

            val contents = states.map { it.content }.filter { it != VaultsContent.Loading }
            assertEquals(listOf("Personal"), (contents.first() as VaultsContent.Loaded).vaults.map { it.name })
            assertTrue(states.drop(1).none { it.content == VaultsContent.Loading || it.refreshing })
        }

    @Test
    fun `offline with a copy shows it, marked not up to date`() =
        runTest {
            monica.addVaults("Personal", "Family")
            graph.now = 1_800_000_000_000L
            val viewModel = viewModel()
            viewModel.onShown()
            viewModel.awaitState { it.content is VaultsContent.Loaded }
            failNextLoads()

            val reopened = VaultsViewModel(graph.vaultRepository)
            reopened.onShown()
            val stale = reopened.awaitState { it.staleSince != null }

            assertEquals(listOf("Personal", "Family"), stale.names())
            assertEquals(graph.now, stale.staleSince)
        }

    @Test
    fun `a successful answer clears the not up to date mark`() =
        runTest {
            monica.addVaults("Personal")
            val viewModel = viewModel()
            viewModel.onShown()
            viewModel.awaitState { it.content is VaultsContent.Loaded }
            failNextLoads()
            viewModel.onShown()
            viewModel.awaitState { it.staleSince != null }
            answerLoadsAgain()

            viewModel.refresh()

            assertNull(viewModel.awaitState { !it.refreshing && it.staleSince == null }.staleSince)
        }

    @Test
    fun `a failed pull to refresh says so and marks the list not up to date`() =
        runTest {
            monica.addVaults("Personal")
            val viewModel = viewModel()
            viewModel.onShown()
            viewModel.awaitState { it.content is VaultsContent.Loaded }
            failNextLoads()

            viewModel.refresh()
            val failed = viewModel.awaitState { !it.refreshing && it.refreshError != null }

            assertEquals(graph.now, failed.staleSince)
        }
}
