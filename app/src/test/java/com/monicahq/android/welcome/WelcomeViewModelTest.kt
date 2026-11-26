package com.monicahq.android.welcome

import com.monicahq.android.MainDispatcherRule
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import com.monicahq.android.instance.FakePreferencesDataStore
import com.monicahq.android.instance.InstanceRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = InstanceRepository(FakePreferencesDataStore())

    /** A ViewModel whose state is collected, as the screen does. */
    private fun TestScope.viewModel(): WelcomeViewModel =
        WelcomeViewModel(repository).also { viewModel ->
            backgroundScope.launch(mainDispatcherRule.dispatcher) { viewModel.uiState.collect {} }
        }

    @Test
    fun `starts on the default instance with the sheet closed`() =
        runTest {
            val viewModel = viewModel()

            assertEquals(WelcomeUiState(instanceUrl = DEFAULT_INSTANCE_URL, sheet = null), viewModel.uiState.value)
        }

    @Test
    fun `shows the saved instance`() =
        runTest {
            repository.setUrl("https://monica.example.com")

            val viewModel = viewModel()

            assertEquals("https://monica.example.com", viewModel.uiState.value.instanceUrl)
        }

    @Test
    fun `opening the sheet fills the field with the current address`() =
        runTest {
            repository.setUrl("https://monica.example.com")
            val viewModel = viewModel()

            viewModel.openInstanceSheet()

            assertEquals(InstanceSheetState(draft = "https://monica.example.com"), viewModel.uiState.value.sheet)
        }

    @Test
    fun `typing changes the draft only`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openInstanceSheet()

            viewModel.onDraftChange("monica.example")

            assertEquals(
                "monica.example",
                viewModel.uiState.value.sheet
                    ?.draft,
            )
            assertEquals(DEFAULT_INSTANCE_URL, repository.url.first())
        }

    @Test
    fun `reset puts the default address in the field without saving`() =
        runTest {
            repository.setUrl("https://monica.example.com")
            val viewModel = viewModel()
            viewModel.openInstanceSheet()

            viewModel.resetDraft()

            assertEquals(
                DEFAULT_INSTANCE_URL,
                viewModel.uiState.value.sheet
                    ?.draft,
            )
            assertEquals("https://monica.example.com", repository.url.first())
        }

    @Test
    fun `dismissing closes the sheet without saving`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openInstanceSheet()
            viewModel.onDraftChange("https://monica.example.com")

            viewModel.dismissInstanceSheet()

            assertNull(viewModel.uiState.value.sheet)
            assertEquals(DEFAULT_INSTANCE_URL, viewModel.uiState.value.instanceUrl)
        }

    @Test
    fun `saving stores the normalised address and closes the sheet`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openInstanceSheet()
            viewModel.onDraftChange(" monica.example.com/ ")

            viewModel.saveInstance()

            assertNull(viewModel.uiState.value.sheet)
            assertEquals("https://monica.example.com", viewModel.uiState.value.instanceUrl)
            assertEquals("https://monica.example.com", repository.url.first())
        }

    @Test
    fun `saving an invalid address keeps the sheet open with an error`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openInstanceSheet()
            viewModel.onDraftChange("not an address")

            viewModel.saveInstance()

            assertEquals(InstanceSheetState(draft = "not an address", isError = true), viewModel.uiState.value.sheet)
            assertEquals(DEFAULT_INSTANCE_URL, repository.url.first())
        }

    @Test
    fun `typing after an error clears it`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openInstanceSheet()
            viewModel.onDraftChange("not an address")
            viewModel.saveInstance()

            viewModel.onDraftChange("monica.example.com")

            assertEquals(
                false,
                viewModel.uiState.value.sheet
                    ?.isError,
            )
        }

    @Test
    fun `events while the sheet is closed change nothing`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onDraftChange("https://monica.example.com")
            viewModel.resetDraft()
            viewModel.saveInstance()

            assertEquals(WelcomeUiState(), viewModel.uiState.value)
            assertEquals(DEFAULT_INSTANCE_URL, repository.url.first())
        }
}
