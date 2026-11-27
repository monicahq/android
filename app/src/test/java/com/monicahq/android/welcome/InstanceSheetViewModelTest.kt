package com.monicahq.android.welcome

import com.monicahq.android.FakeMonica
import com.monicahq.android.MainDispatcherRule
import com.monicahq.android.TestGraph
import com.monicahq.android.auth.AuthError
import com.monicahq.android.auth.SessionState
import com.monicahq.android.auth.SignOutReason
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import com.monicahq.android.instance.instanceHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InstanceSheetViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val a = FakeMonica()
    private val b = FakeMonica()
    private val graph = TestGraph()

    @After
    fun tearDown() {
        a.close()
        b.close()
    }

    private suspend fun viewModel(saved: String = a.url): InstanceSheetViewModel {
        graph.instanceRepository.setUrl(saved)
        graph.sessionManager.load()
        return InstanceSheetViewModel(graph.instanceRepository, graph.checker, graph.sessionManager, graph.signInManager)
    }

    /** Waits for the check, which runs on OkHttp's threads. */
    private suspend fun InstanceSheetViewModel.awaitSheet(predicate: (InstanceSheetState?) -> Boolean): InstanceSheetState? =
        withContext(Dispatchers.Default) { withTimeout(5_000) { uiState.first(predicate) } }

    private suspend fun InstanceSheetViewModel.openWith(draft: String) {
        open()
        onDraftChange(draft)
    }

    @Test
    fun `opening fills the field with the saved address`() =
        runTest {
            val viewModel = viewModel()

            viewModel.open()

            assertEquals(InstanceSheetState(draft = a.url), viewModel.uiState.value)
        }

    @Test
    fun `typing changes the draft only`() =
        runTest {
            val viewModel = viewModel()

            viewModel.openWith(b.url)

            assertEquals(b.url, viewModel.uiState.value?.draft)
            assertEquals(a.url, graph.instanceRepository.url.first())
        }

    @Test
    fun `a compatible address is checked, then saved`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openWith(b.url)

            viewModel.save()
            assertEquals(true, viewModel.uiState.value?.isChecking)

            assertNull(viewModel.awaitSheet { it == null })
            assertEquals(b.url, graph.instanceRepository.url.first())
            assertEquals(1, b.requestsTo("/api/v1/status").size)
        }

    @Test
    fun `an incompatible address keeps the sheet open with the reason and the previous address`() =
        runTest {
            val viewModel = viewModel()
            b.overrides["/api/v1/status"] = { FakeMonica.json("<html></html>") }
            viewModel.openWith(b.url)

            viewModel.save()

            val sheet = viewModel.awaitSheet { it?.error != null }
            assertEquals(InstanceSheetState(draft = b.url, error = AuthError.NotMonica, failedAttempts = 1), sheet)
            assertEquals(a.url, graph.instanceRepository.url.first())
        }

    @Test
    fun `an invalid address is refused at once`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openWith("not an address")

            viewModel.save()

            assertEquals(AuthError.InvalidAddress, viewModel.awaitSheet { it?.error != null }?.error)
        }

    @Test
    fun `the same error twice counts as two attempts, so it is announced again`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openWith("not an address")
            viewModel.save()
            viewModel.awaitSheet { it?.failedAttempts == 1 }

            viewModel.save()

            val sheet = viewModel.awaitSheet { it?.failedAttempts == 2 }
            assertEquals(AuthError.InvalidAddress, sheet?.error)
        }

    @Test
    fun `closing the sheet during the check saves nothing`() =
        runTest {
            val viewModel = viewModel()
            b.overrides["/api/v1/status"] = { FakeMonica.delayed(FakeMonica.json(b.status()), 300) }
            viewModel.openWith(b.url)

            viewModel.save()
            viewModel.dismiss()
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { while (b.requestsTo("/.well-known/oauth-authorization-server").isEmpty()) Thread.sleep(10) }
            }
            Thread.sleep(200)

            assertNull(viewModel.uiState.value)
            assertEquals(a.url, graph.instanceRepository.url.first())
        }

    @Test
    fun `typing after an error clears it`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openWith("not an address")
            viewModel.save()
            viewModel.awaitSheet { it?.error != null }

            viewModel.onDraftChange(b.url)

            assertNull(viewModel.uiState.value?.error)
        }

    @Test
    fun `reset puts the official instance in the field without saving`() =
        runTest {
            val viewModel = viewModel()
            viewModel.open()

            viewModel.resetDraft()

            assertEquals(DEFAULT_INSTANCE_URL, viewModel.uiState.value?.draft)
            assertEquals(a.url, graph.instanceRepository.url.first())
        }

    @Test
    fun `dismissing closes the sheet without saving`() =
        runTest {
            val viewModel = viewModel()
            viewModel.openWith(b.url)

            viewModel.dismiss()

            assertNull(viewModel.uiState.value)
            assertEquals(a.url, graph.instanceRepository.url.first())
        }

    @Test
    fun `saving the current address just closes the sheet`() =
        runTest {
            graph.signIn(a)
            val viewModel = viewModel()
            viewModel.open()

            viewModel.save()

            assertNull(viewModel.awaitSheet { it == null })
            assertTrue(graph.sessionManager.state.value is SessionState.SignedIn)
        }

    @Test
    fun `leaving the instance the person is signed in to asks first`() =
        runTest {
            graph.signIn(a)
            val viewModel = viewModel()
            viewModel.openWith(b.url)

            viewModel.save()

            assertEquals(instanceHost(a.url), viewModel.awaitSheet { it?.switchFrom != null }?.switchFrom)
            assertEquals(a.url, graph.instanceRepository.url.first())
            assertTrue(graph.sessionManager.state.value is SessionState.SignedIn)
        }

    @Test
    fun `confirming the switch signs out of A, then saves B`() =
        runTest {
            graph.signIn(a)
            val viewModel = viewModel()
            viewModel.openWith(b.url)
            viewModel.save()
            viewModel.awaitSheet { it?.switchFrom != null }

            viewModel.confirmSwitch()

            assertNull(viewModel.awaitSheet { it == null })
            assertEquals(b.url, graph.instanceRepository.url.first())
            assertEquals(SessionState.SignedOut(SignOutReason.LoggedOut), graph.sessionManager.state.value)
            assertEquals(1, a.requestsTo("/oauth/revoke").size)
            assertTrue(b.requests.none { it.headers["Authorization"] != null })
        }

    @Test
    fun `cancelling the switch keeps everything`() =
        runTest {
            graph.signIn(a)
            val viewModel = viewModel()
            viewModel.openWith(b.url)
            viewModel.save()
            viewModel.awaitSheet { it?.switchFrom != null }

            viewModel.cancelSwitch()
            viewModel.confirmSwitch()

            assertEquals(InstanceSheetState(draft = b.url), viewModel.uiState.value)
            assertEquals(a.url, graph.instanceRepository.url.first())
            assertTrue(graph.sessionManager.state.value is SessionState.SignedIn)
        }

    @Test
    fun `switching while signed out cancels a sign-in in progress`() =
        runTest {
            val viewModel = viewModel()
            graph.startSignIn(a)
            viewModel.openWith(b.url)

            viewModel.save()
            viewModel.awaitSheet { it == null }

            assertNull(graph.store.pendingSignIn())
        }

    @Test
    fun `events while the sheet is closed change nothing`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onDraftChange(b.url)
            viewModel.resetDraft()
            viewModel.save()

            assertNull(viewModel.uiState.value)
            assertEquals(a.url, graph.instanceRepository.url.first())
        }
}
