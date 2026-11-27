package com.monicahq.android.auth

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.EndToEnd
import com.monicahq.android.MainActivity
import com.monicahq.android.TestMonica
import com.monicahq.android.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** User Story 3: staying signed in, and being told when the session really ended. */
@RunWith(AndroidJUnit4::class)
class SessionLifecycleEndToEndTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val monica = TestMonica()
    private val e2e = EndToEnd()

    @Before
    fun setUp() {
        e2e.setUp(monica.url)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Log in").performScrollTo().performClick()
            e2e.sendCallback(scenario, e2e.authorizationUrl(composeRule))
            composeRule.waitForText("All your vaults")
        }
        monica.requests.clear()
    }

    @After
    fun tearDown() {
        e2e.tearDown()
        monica.close()
    }

    @Test
    fun refusedAccessIsRenewedWithoutThePersonNoticing() {
        monica.validAccessToken = "rotated-elsewhere"

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitUntil(EndToEnd.TIMEOUT) { monica.requestsTo("/api/v1/vaults").size >= 2 }
            composeRule.onNodeWithText("All your vaults").assertExists()
            assertEquals(1, monica.requestsTo("/oauth/token").size)
        }
    }

    @Test
    fun aSessionEndedOnTheWebSignsThePhoneOut() {
        monica.validAccessToken = null
        monica.validRefreshToken = null

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitForText("Your session was ended. Log in again.")
        }
    }

    @Test
    fun anUnreachableInstanceKeepsThePersonSignedIn() {
        monica.close()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitForText("Can't reach this instance", substring = true)
            composeRule.onNodeWithText("All your vaults").assertExists()
        }
    }
}
