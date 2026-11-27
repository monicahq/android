package com.monicahq.android.vault

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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

/** User Stories 1, 2 and 3: arriving on the vaults, opening one, creating one, end to end. */
@RunWith(AndroidJUnit4::class)
class VaultsEndToEndTest {
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

    private fun signIn(scenario: ActivityScenario<MainActivity>) {
        composeRule.onNodeWithText("Log in").performScrollTo().performClick()
        e2e.sendCallback(scenario, e2e.authorizationUrl(composeRule))
        composeRule.waitForText("All your vaults")
    }

    private fun ComposeTestRule.createVault(name: String) {
        onNode(hasSetTextAction() and hasText("Name")).performTextInput(name)
        onNodeWithContentDescription("Green").performClick()
        onNodeWithText("Create vault").performScrollTo().performClick()
    }

    @Test
    fun arrivingShowsTheVaultsAndOneOpens() {
        monica.addVaults("Personal", "Family", "Work")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signIn(scenario)
            composeRule.waitForText("Personal")
            composeRule.onNodeWithText("Default").assertExists()

            composeRule.onNodeWithText("Family").performClick()
            composeRule.waitUntil(EndToEnd.TIMEOUT) { monica.requestsTo("/api/v1/vaults/vault-2").isNotEmpty() }
            composeRule.onNodeWithContentDescription("Back").performClick()

            composeRule.waitForText("All your vaults")
            composeRule.onNodeWithText("Work").assertExists()
        }
    }

    @Test
    fun aNewAccountCreatesItsFirstVaultFromTheEmptyState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signIn(scenario)
            composeRule.waitForText("No vault yet")

            composeRule.onNodeWithText("New vault").performClick()
            composeRule.waitForText("Create vault")
            composeRule.onNodeWithText("Make this the default vault").assertDoesNotExist()
            composeRule.createVault("Family")

            composeRule.waitForText("Family")
            composeRule.waitForText("Default")
            assertEquals("#4f8a49", monica.vaults.single().colour)
        }
    }

    @Test
    fun aRefusalIsShownUnderTheNameAndTheNameIsKept() {
        monica.overrides["/api/v1/vaults"] = { request ->
            if (request.method == "POST") {
                TestMonica.json(
                    """{"message":"The free plan holds 1 vault.","errors":{"name":["The free plan holds 1 vault."]}}""",
                    code = 422,
                )
            } else {
                TestMonica.json("""{"data":[],"meta":{"page":1,"per_page":100,"total":0}}""")
            }
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signIn(scenario)
            composeRule.waitForText("No vault yet")
            composeRule.onNodeWithText("New vault").performClick()
            composeRule.waitForText("Create vault")
            composeRule.createVault("Family")

            composeRule.waitForText("The free plan holds 1 vault.")
            composeRule.onNodeWithText("Family").assertExists()
        }
    }
}
