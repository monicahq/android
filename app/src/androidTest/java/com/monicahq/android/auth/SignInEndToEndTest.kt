package com.monicahq.android.auth

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.EndToEnd
import com.monicahq.android.MainActivity
import com.monicahq.android.TestMonica
import com.monicahq.android.waitForText
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** User Stories 1 and 5: logging in and signing up through the browser, end to end. */
@RunWith(AndroidJUnit4::class)
class SignInEndToEndTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val monica = TestMonica()
    private val e2e = EndToEnd()

    @Before
    fun setUp() = e2e.setUp(monica.url)

    @After
    fun tearDown() {
        e2e.tearDown()
        monica.close()
    }

    @Test
    fun loggingInShowsTheSignedInHome() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Log in").performScrollTo().performClick()
            val url = e2e.authorizationUrl(composeRule)
            assertEquals("consent", url.toHttpUrl().queryParameter("prompt"))

            e2e.sendCallback(scenario, url)

            composeRule.waitForText("Hello, Jane Doe")
            composeRule.onNodeWithText("Connected to ${monica.url.substringAfter("://")}").assertExists()
        }
    }

    @Test
    fun signInCompletesAfterTheActivityWasRecreated() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Log in").performScrollTo().performClick()
            val url = e2e.authorizationUrl(composeRule)

            scenario.recreate()
            e2e.sendCallback(scenario, url)

            composeRule.waitForText("Hello, Jane Doe")
        }
    }

    @Test
    fun theSessionSurvivesARelaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Log in").performScrollTo().performClick()
            e2e.sendCallback(scenario, e2e.authorizationUrl(composeRule))
            composeRule.waitForText("Hello, Jane Doe")
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitForText("Hello, Jane Doe")
        }
    }

    @Test
    fun refusingAccessIsExplained() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Log in").performScrollTo().performClick()

            e2e.sendCallback(scenario, e2e.authorizationUrl(composeRule), query = "error=access_denied")

            composeRule.waitForText("You did not allow access. Log in again to allow it.")
        }
    }

    @Test
    fun signingUpOpensTheRegistrationPageThenSignsIn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Sign up").performScrollTo().performClick()
            val url = e2e.authorizationUrl(composeRule)
            assertEquals("consent create", url.toHttpUrl().queryParameter("prompt"))

            e2e.sendCallback(scenario, url)

            composeRule.waitForText("Hello, Jane Doe")
        }
    }

    @Test
    fun comingBackWhileConfirmingTheEmailStillSignsInFromTheLink() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Sign up").performScrollTo().performClick()
            val url = e2e.authorizationUrl(composeRule)

            // The person comes back to the app to wait for the confirmation email.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitForText(
                "Not finished yet? Open the link in the email Monica sent you to confirm your address: it brings you back here, signed in.",
            )

            e2e.sendCallback(scenario, url)

            composeRule.waitForText("Hello, Jane Doe")
        }
    }

    @Test
    fun signingUpOnAClosedInstanceExplainsIt() {
        monica.registrationEnabled = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Sign up").performScrollTo().performClick()

            composeRule.waitForText("This instance doesn't accept new accounts. Ask its administrator.")
        }
    }
}
