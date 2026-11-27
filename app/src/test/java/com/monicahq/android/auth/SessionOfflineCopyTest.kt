package com.monicahq.android.auth

import com.monicahq.android.FakeMonica
import com.monicahq.android.TestGraph
import com.monicahq.android.network.ApiResult
import com.monicahq.android.offline.Owner
import com.monicahq.android.offline.RoomOfflineCopy
import com.monicahq.android.vault.Vault
import com.monicahq.android.vault.VaultColour
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The offline copy lives exactly as long as the session (spec 004, User Story 3; constitution VIII). */
class SessionOfflineCopyTest {
    private val monica = FakeMonica()
    private val other = FakeMonica(tokenPrefix = "b-")
    private val graph = TestGraph()
    private val sessions get() = graph.sessionManager
    private val copy get() = graph.offlineCopy

    /** The app's state at each erasure: it must never already be signed out. */
    private val statesAtErase = mutableListOf<SessionState>()

    private val jane get() = Owner(monica.url, "7")
    private val personal = Vault("v1", "Personal", null, VaultColour.Blue, isDefault = true, contactCount = 3)

    @After
    fun tearDown() {
        monica.close()
        other.close()
    }

    private suspend fun signedIn() {
        graph.signIn(monica)
        copy.onErase = { statesAtErase += sessions.state.value }
        statesAtErase.clear()
    }

    private fun restarted() = SessionManager(graph.store, graph.tokenClient, graph.http, copy, clock = { graph.now })

    private fun assertErasedBeforeSignedOut() {
        assertTrue("the copy was not erased", statesAtErase.isNotEmpty())
        assertTrue("erased after SignedOut was published: $statesAtErase", statesAtErase.none { it is SessionState.SignedOut })
        assertTrue(sessions.state.value is SessionState.SignedOut)
        assertFalse(copy.hasData)
    }

    @Test
    fun `signing in opens the copy for that account on that instance`() =
        runTest {
            signedIn()

            assertEquals(jane, copy.owner)
        }

    @Test
    fun `signing out erases the copy before the welcome screen`() =
        runTest {
            signedIn()

            sessions.logOut()

            assertErasedBeforeSignedOut()
        }

    @Test
    fun `signing out with the instance unreachable still erases the copy`() =
        runTest {
            signedIn()
            monica.close()

            sessions.logOut()

            assertErasedBeforeSignedOut()
        }

    @Test
    fun `a refused renewal erases the copy before the welcome screen`() =
        runTest {
            signedIn()
            monica.validAccessToken = "rotated-elsewhere"
            monica.validRefreshToken = null

            assertEquals(ApiResult.SessionEnded, checkNotNull(sessions.apiClient()).me())

            assertErasedBeforeSignedOut()
            assertEquals(SessionState.SignedOut(SignOutReason.Revoked), sessions.state.value)
        }

    @Test
    fun `an expired session erases the copy before the welcome screen`() =
        runTest {
            signedIn()
            graph.now += TimeUnit.DAYS.toMillis(31)
            monica.validRefreshToken = null

            assertEquals(ApiResult.SessionEnded, checkNotNull(sessions.apiClient()).me())

            assertErasedBeforeSignedOut()
            assertEquals(SessionState.SignedOut(SignOutReason.Expired), sessions.state.value)
        }

    @Test
    fun `a session the instance rejects erases the copy before the welcome screen`() =
        runTest {
            signedIn()
            monica.overrides["/api/v1/me"] = { FakeMonica.json("""{"message":"Unauthenticated."}""", code = 401) }

            assertEquals(ApiResult.SessionEnded, checkNotNull(sessions.apiClient()).me())

            assertErasedBeforeSignedOut()
        }

    @Test
    fun `a copy left by a run that died during sign-out is erased at start`() =
        runTest {
            copy.leaveOnDisk(jane, lastContactAt = graph.now, vaults = listOf(personal))

            sessions.load()

            assertEquals(SessionState.SignedOut(), sessions.state.value)
            assertFalse(copy.hasData)
        }

    @Test
    fun `a fresh copy of the same account is kept at start`() =
        runTest {
            signedIn()
            copy.leaveOnDisk(jane, lastContactAt = graph.now - TimeUnit.DAYS.toMillis(30), vaults = listOf(personal))
            val erasuresBefore = copy.erasures

            restarted().load()

            assertEquals(erasuresBefore, copy.erasures)
            assertEquals(listOf(personal), copy.vaults.all()?.value)
        }

    @Test
    fun `a copy older than 30 days without contact is erased at start`() =
        runTest {
            signedIn()
            copy.leaveOnDisk(jane, lastContactAt = graph.now - TimeUnit.DAYS.toMillis(30) - 1, vaults = listOf(personal))

            restarted().load()

            assertNull(copy.vaults.all())
            assertEquals(jane, copy.owner)
        }

    @Test
    fun `a clock set before the last contact erases the copy at start`() =
        runTest {
            signedIn()
            copy.leaveOnDisk(jane, lastContactAt = graph.now + TimeUnit.MINUTES.toMillis(1), vaults = listOf(personal))

            restarted().load()

            assertNull(copy.vaults.all())
        }

    @Test
    fun `a copy of another account is erased at start`() =
        runTest {
            signedIn()
            copy.leaveOnDisk(Owner(monica.url, "someone-else"), lastContactAt = graph.now, vaults = listOf(personal))

            restarted().load()

            assertNull(copy.vaults.all())
            assertEquals(jane, copy.owner)
        }

    @Test
    fun `a copy that cannot be read is erased at start`() =
        runTest {
            signedIn()
            copy.leaveOnDisk(jane, lastContactAt = graph.now, vaults = listOf(personal))
            copy.unreadable = true

            restarted().load()

            assertNull(copy.vaults.all())
        }

    @Test
    fun `signing in to another instance never sees the first one's copy`() =
        runTest {
            copy.leaveOnDisk(jane, lastContactAt = graph.now, vaults = listOf(personal))

            graph.signIn(other)

            assertEquals(Owner(other.url, "7"), copy.owner)
            assertNull(copy.vaults.all())
        }

    @Test
    fun `an answer from the instance confirms the copy`() =
        runTest {
            signedIn()
            graph.now += TimeUnit.DAYS.toMillis(2)

            checkNotNull(sessions.apiClient()).me()

            assertEquals(graph.now, copy.lastContactAt)
        }

    @Test
    fun `an unreachable instance does not confirm the copy`() =
        runTest {
            signedIn()
            val before = copy.lastContactAt
            graph.now += TimeUnit.DAYS.toMillis(2)
            monica.close()

            checkNotNull(sessions.apiClient()).me()

            assertEquals(before, copy.lastContactAt)
        }

    @Test
    fun `the 30-day limit is the one the real copy uses`() {
        assertEquals(TimeUnit.DAYS.toMillis(30), RoomOfflineCopy.MAX_AGE)
    }

    @Test
    fun `signing out still erases the copy when its caller is cancelled meanwhile`() =
        runTest {
            signedIn()
            copy.eraseDelayMillis = 1_000

            val signingOut = launch { sessions.logOut() }
            advanceTimeBy(500)
            signingOut.cancel()
            advanceUntilIdle()

            assertErasedBeforeSignedOut()
        }

    @Test
    fun `a copy that cannot be opened never stops signing in`() =
        runTest {
            copy.failOnOpen = true

            graph.signIn(monica)

            assertTrue(sessions.state.value is SessionState.SignedIn)
            assertFalse(copy.hasData)
        }

    @Test
    fun `a copy that cannot be opened never stops the app from starting`() =
        runTest {
            signedIn()
            copy.failOnOpen = true

            val restarted = restarted()
            restarted.load()

            assertTrue(restarted.state.value is SessionState.SignedIn)
            assertFalse(copy.hasData)
        }
}
