package com.monicahq.android.auth

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.EndToEnd
import com.monicahq.android.MainActivity
import com.monicahq.android.TestMonica
import com.monicahq.android.waitForText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** User Story 4: switching instance signs out of the first one, and nothing crosses over. */
@RunWith(AndroidJUnit4::class)
class InstanceSwitchEndToEndTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val a = TestMonica(tokenPrefix = "a-")
    private val b = TestMonica(tokenPrefix = "b-")
    private val e2e = EndToEnd()

    @Before
    fun setUp() = e2e.setUp(a.url)

    @After
    fun tearDown() {
        e2e.tearDown()
        a.close()
        b.close()
    }

    @Test
    fun switchingSignsOutOfAAndKeepsBClean() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Log in").performScrollTo().performClick()
            e2e.sendCallback(scenario, e2e.authorizationUrl(composeRule))
            composeRule.waitForText("Hello, Jane Doe")

            composeRule.onNodeWithText("Instance settings").performClick()
            composeRule.onNode(hasSetTextAction()).performTextReplacement(b.url)
            composeRule.onNodeWithText("Save").performScrollTo().performClick()
            composeRule.waitForText("Switch and sign out")
            composeRule.onNodeWithText("Switch and sign out").performClick()

            composeRule.waitForText("Instance: ${b.url.substringAfter("://")}")
            composeRule.waitUntil(EndToEnd.TIMEOUT) { a.requestsTo("/oauth/revoke").isNotEmpty() }
            assertEquals("a-refresh-1", a.form(a.requestsTo("/oauth/revoke").single())["token"])
            assertTrue(
                b.requests.none {
                    it.headers["Authorization"] != null ||
                        it.body
                            ?.utf8()
                            .orEmpty()
                            .contains("a-")
                },
            )
        }
    }

    @Test
    fun loggingOutAsksFirstThenReturnsToTheWelcomeScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Log in").performScrollTo().performClick()
            e2e.sendCallback(scenario, e2e.authorizationUrl(composeRule))
            composeRule.waitForText("Hello, Jane Doe")

            composeRule.onNodeWithText("Sign out").performClick()
            composeRule.onNodeWithText("Sign out?").assertExists()
            // The dialog's button comes after the home's.
            composeRule.onAllNodesWithText("Sign out")[1].performClick()

            composeRule.waitForText("Welcome to Monica")
            composeRule.waitUntil(EndToEnd.TIMEOUT) { a.requestsTo("/oauth/revoke").isNotEmpty() }
        }
    }
}
