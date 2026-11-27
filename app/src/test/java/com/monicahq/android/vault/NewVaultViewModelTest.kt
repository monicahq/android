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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NewVaultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monica = FakeMonica()
    private val graph = TestGraph()

    @After
    fun tearDown() = monica.close()

    private suspend fun viewModel(offerDefault: Boolean = false): NewVaultViewModel {
        graph.signIn(monica)
        return NewVaultViewModel(offerDefault, graph.vaultRepository)
    }

    private suspend fun NewVaultViewModel.awaitSaved(): NewVaultUiState =
        withContext(Dispatchers.Default) { withTimeout(5_000) { uiState.first { !it.saving } } }

    private fun refuse(body: String) {
        monica.overrides["/api/v1/vaults"] = { FakeMonica.json(body, code = 422) }
    }

    @Test
    fun `starts empty, blue, with the default option as the route says`() =
        runTest {
            assertEquals(NewVaultUiState(offerDefault = true), viewModel(offerDefault = true).uiState.value)
            assertFalse(NewVaultUiState().canCreate)
        }

    @Test
    fun `a blank name cannot be created`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onNameChange("   ")

            viewModel.create()

            assertFalse(viewModel.uiState.value.canCreate)
            assertTrue(monica.requestsTo("/api/v1/vaults").isEmpty())
        }

    @Test
    fun `the name stops at the instance's limit`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onNameChange("a".repeat(300))

            assertEquals(255, viewModel.uiState.value.name.length)
        }

    @Test
    fun `creating sends the form and ends with the vault created`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onNameChange(" Family ")
            viewModel.onDescriptionChange("Everybody")
            viewModel.onColourChange(VaultColour.Green)

            viewModel.create()
            assertTrue(viewModel.uiState.value.saving)
            assertFalse(viewModel.uiState.value.canCreate)

            assertTrue(viewModel.awaitSaved().created)
            assertEquals(FakeMonica.Vault("vault-1", "Family", "Everybody", "#4f8a49", isDefault = true), monica.vaults.single())
        }

    @Test
    fun `tapping twice while saving creates one vault`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onNameChange("Family")

            viewModel.create()
            viewModel.create()
            viewModel.awaitSaved()

            assertEquals(1, monica.requestsTo("/api/v1/vaults").size)
        }

    @Test
    fun `the default option is sent only when offered and ticked`() =
        runTest {
            monica.addVaults("Personal")
            val viewModel = viewModel(offerDefault = true)
            viewModel.onNameChange("Family")
            viewModel.onMakeDefaultChange(true)

            viewModel.create()
            viewModel.awaitSaved()

            assertTrue(monica.vaults.last().isDefault)
        }

    @Test
    fun `the default option is not sent when it was not offered`() =
        runTest {
            val viewModel = viewModel(offerDefault = false)
            viewModel.onNameChange("Family")
            viewModel.onMakeDefaultChange(true)

            viewModel.create()
            viewModel.awaitSaved()

            val body =
                Json
                    .parseToJsonElement(
                        monica
                            .requestsTo("/api/v1/vaults")
                            .single()
                            .body
                            ?.utf8()
                            .orEmpty(),
                    ).jsonObject
            assertFalse(body.containsKey("is_default"))
        }

    @Test
    fun `a refusal is shown on its field and keeps what was typed`() =
        runTest {
            val viewModel = viewModel()
            refuse(
                """{"message":"The free plan holds 1 vault.","errors":{"name":["The free plan holds 1 vault."],"description":["Too long."]}}""",
            )
            viewModel.onNameChange("Family")
            viewModel.onDescriptionChange("Everybody")

            viewModel.create()
            val state = viewModel.awaitSaved()

            assertEquals("The free plan holds 1 vault.", state.nameError)
            assertEquals("Too long.", state.descriptionError)
            assertNull(state.generalError)
            assertEquals("Family", state.name)
            assertEquals("Everybody", state.description)
            assertFalse(state.created)
        }

    @Test
    fun `editing a field clears its error`() =
        runTest {
            val viewModel = viewModel()
            refuse("""{"message":"No.","errors":{"name":["Bad name."],"description":["Bad description."],"colour":["Bad colour."]}}""")
            viewModel.onNameChange("Family")
            viewModel.create()
            viewModel.awaitSaved()

            viewModel.onNameChange("Famille")
            viewModel.onDescriptionChange("Tous")
            viewModel.onColourChange(VaultColour.Rose)

            val state = viewModel.uiState.value
            assertNull(state.nameError)
            assertNull(state.descriptionError)
            assertNull(state.generalError)
        }

    @Test
    fun `a refusal about another field is shown above the button`() =
        runTest {
            val viewModel = viewModel()
            refuse("""{"message":"No.","errors":{"colour":["Not a colour."]}}""")
            viewModel.onNameChange("Family")

            viewModel.create()

            assertEquals("Not a colour.", viewModel.awaitSaved().generalError)
        }

    @Test
    fun `a refusal naming no field shows its message`() =
        runTest {
            val viewModel = viewModel()
            refuse("""{"message":"Not now."}""")
            viewModel.onNameChange("Family")

            viewModel.create()

            assertEquals("Not now.", viewModel.awaitSaved().generalError)
        }

    @Test
    fun `a connection problem is said, the form is kept and can be sent again`() =
        runTest {
            val viewModel = viewModel()
            monica.overrides["/api/v1/vaults"] = { FakeMonica.json("{}", code = 500) }
            viewModel.onNameChange("Family")

            viewModel.create()
            val failed = viewModel.awaitSaved()
            assertEquals(AuthError.ServerError, failed.connectionError)
            assertEquals("Family", failed.name)
            assertTrue(failed.canCreate)

            viewModel.dismissConnectionError()
            assertNull(viewModel.uiState.value.connectionError)
        }

    @Test
    fun `a missing endpoint is a server error`() =
        runTest {
            val viewModel = viewModel()
            monica.overrides["/api/v1/vaults"] = { FakeMonica.json("{}", code = 404) }
            viewModel.onNameChange("Family")

            viewModel.create()

            assertEquals(AuthError.ServerError, viewModel.awaitSaved().connectionError)
        }

    @Test
    fun `a session ended while saving just stops saving`() =
        runTest {
            val viewModel = viewModel()
            monica.validAccessToken = null
            monica.validRefreshToken = null
            viewModel.onNameChange("Family")

            viewModel.create()
            val state = viewModel.awaitSaved()

            assertFalse(state.created)
            assertNull(state.connectionError)
        }

    @Test
    fun `leaving an untouched form closes it at once`() =
        runTest {
            val viewModel = viewModel()

            viewModel.leave()

            assertTrue(viewModel.uiState.value.closed)
            assertFalse(viewModel.uiState.value.confirmDiscard)
        }

    @Test
    fun `leaving with something typed asks first`() =
        runTest {
            val viewModel = viewModel()
            viewModel.onDescriptionChange("Everybody")

            viewModel.leave()
            assertTrue(viewModel.uiState.value.confirmDiscard)

            viewModel.dismissDiscard()
            assertFalse(viewModel.uiState.value.confirmDiscard)
            assertFalse(viewModel.uiState.value.closed)

            viewModel.leave()
            viewModel.confirmDiscard()
            assertTrue(viewModel.uiState.value.closed)
        }
}
