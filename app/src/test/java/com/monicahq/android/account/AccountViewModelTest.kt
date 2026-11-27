package com.monicahq.android.account

import com.monicahq.android.FakeMonica
import com.monicahq.android.MainDispatcherRule
import com.monicahq.android.TestGraph
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

class AccountViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val monica = FakeMonica()
    private val graph = TestGraph()

    @After
    fun tearDown() = monica.close()

    private suspend fun TestScope.viewModel(): AccountViewModel {
        graph.signIn(monica)
        return AccountViewModel(graph.sessionManager).also { viewModel ->
            backgroundScope.launch(mainDispatcherRule.dispatcher) { viewModel.uiState.collect {} }
        }
    }

    @Test
    fun `shows the person and the instance`() =
        runTest {
            val viewModel = viewModel()

            assertEquals("Jane Doe", viewModel.uiState.value.name)
            assertEquals("JD", viewModel.uiState.value.initials)
            assertEquals(monica.url, viewModel.uiState.value.instanceUrl)
        }

    @Test
    fun `the first state already shows the person`() =
        runTest {
            graph.signIn(monica)

            val viewModel = AccountViewModel(graph.sessionManager)

            assertEquals("Jane Doe", viewModel.uiState.value.name)
        }

    @Test
    fun `a name without letters has no initials`() =
        runTest {
            monica.overrides["/api/v1/me"] = { FakeMonica.json("""{"data":{"id":"7","attributes":{"first_name":"123","email":"123"}}}""") }
            val viewModel = viewModel()

            assertNull(viewModel.uiState.value.initials)
        }

    @Test
    fun `the sheet opens and closes`() =
        runTest {
            val viewModel = viewModel()

            viewModel.openSheet()
            assertTrue(viewModel.uiState.value.sheetOpen)

            viewModel.closeSheet()
            assertFalse(viewModel.uiState.value.sheetOpen)
        }

    @Test
    fun `the instance settings take the sheet's place`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openSheet()

            viewModel.openInstanceSettings()

            assertFalse(viewModel.uiState.value.sheetOpen)
        }

    @Test
    fun `signing out asks first`() =
        runTest {
            val viewModel = viewModel()

            viewModel.openSheet()
            viewModel.askSignOut()
            assertTrue(viewModel.uiState.value.confirmSignOut)
            assertFalse(viewModel.uiState.value.sheetOpen)

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
