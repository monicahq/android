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
}
