package com.monicahq.android.vault

import com.monicahq.android.FakeMonica
import com.monicahq.android.MainDispatcherRule
import com.monicahq.android.TestGraph
import com.monicahq.android.auth.AuthError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VaultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monica = FakeMonica()
    private val graph = TestGraph()

    @After
    fun tearDown() = monica.close()

    private suspend fun viewModel(id: String): VaultViewModel {
        graph.signIn(monica)
        return VaultViewModel(id, graph.vaultRepository)
    }

    private suspend fun VaultViewModel.awaitState(): VaultUiState =
        withContext(Dispatchers.Default) { withTimeout(5_000) { uiState.first { it != VaultUiState.Loading } } }

    @Test
    fun `shows the vault`() =
        runTest {
            monica.addVaults("Personal", "Family")

            val state = viewModel("vault-2").awaitState()

            assertEquals("Family", (state as VaultUiState.Loaded).vault.name)
        }

    @Test
    fun `a vault deleted on the web is not found`() =
        runTest {
            assertEquals(VaultUiState.NotFound, viewModel("gone").awaitState())
        }

    @Test
    fun `a failure can be tried again`() =
        runTest {
            monica.addVaults("Personal")
            monica.overrides["/api/v1/vaults/vault-1"] = { FakeMonica.json("{}", code = 500) }
            val viewModel = viewModel("vault-1")
            assertEquals(VaultUiState.Failed(AuthError.ServerError), viewModel.awaitState())

            monica.overrides.clear()
            viewModel.tryAgain()

            assertEquals("Personal", (viewModel.awaitState() as VaultUiState.Loaded).vault.name)
        }

    @Test
    fun `an unexpected refusal is a server error`() =
        runTest {
            monica.overrides["/api/v1/vaults/vault-1"] = { FakeMonica.json("""{"message":"No."}""", code = 422) }

            assertEquals(VaultUiState.Failed(AuthError.ServerError), viewModel("vault-1").awaitState())
        }

    @Test
    fun `offline, a vault received in the list shows from the copy, marked not up to date`() =
        runTest {
            monica.addVaults("Personal", "Family")
            graph.signIn(monica)
            graph.now = 1_800_000_000_000L
            graph.vaultRepository.vaults()
            monica.overrides["/api/v1/vaults/vault-2"] = { FakeMonica.json("{}", code = 500) }

            val viewModel = VaultViewModel("vault-2", graph.vaultRepository)
            val state =
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        viewModel.uiState.first {
                            (it as? VaultUiState.Loaded)?.staleSince !=
                                null
                        }
                    }
                }

            state as VaultUiState.Loaded
            assertEquals("Family", state.vault.name)
            assertEquals(graph.now, state.staleSince)
        }

    @Test
    fun `a vault deleted on the web leaves even when it was shown from the copy`() =
        runTest {
            monica.addVaults("Personal")
            graph.signIn(monica)
            graph.vaultRepository.vaults()
            monica.vaults.clear()

            val viewModel = VaultViewModel("vault-1", graph.vaultRepository)

            // The copy may show first; the instance's answer then says the vault is gone.
            withContext(Dispatchers.Default) { withTimeout(5_000) { viewModel.uiState.first { it == VaultUiState.NotFound } } }
            assertEquals(null, graph.vaultRepository.cachedVault("vault-1"))
        }
}
