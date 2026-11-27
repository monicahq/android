package com.monicahq.android.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.WithLocale
import com.monicahq.android.auth.AuthError
import com.monicahq.android.ui.theme.MonicaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var instanceClicks = 0
    private var signOutClicks = 0
    private var confirmations = 0
    private var dismissals = 0
    private var errorsShown = 0

    private val signedIn = HomeUiState(name = "Jane Doe", instanceUrl = "https://monica.example.com")

    private fun setScreen(
        uiState: HomeUiState = signedIn,
        darkTheme: Boolean = false,
        locale: Locale? = null,
    ) {
        composeRule.setContent {
            WithLocale(locale) {
                MonicaTheme(darkTheme = darkTheme) {
                    HomeScreen(
                        uiState = uiState,
                        onChangeInstance = { instanceClicks++ },
                        onSignOut = { signOutClicks++ },
                        onConfirmSignOut = { confirmations++ },
                        onDismissSignOut = { dismissals++ },
                        onErrorShown = { errorsShown++ },
                    )
                }
            }
        }
    }

    @Test
    fun showsThePersonAndTheInstance() {
        setScreen()

        composeRule
            .onNode(hasText("Hello, Jane Doe") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
        composeRule.onNodeWithText("Connected to monica.example.com").assertIsDisplayed()
    }

    @Test
    fun buttonsReportTheirTaps() {
        setScreen()

        composeRule.onNodeWithText("Instance settings").performClick()
        composeRule.onNodeWithText("Sign out").performClick()

        assertEquals(listOf(1, 1), listOf(instanceClicks, signOutClicks))
    }

    @Test
    fun signingOutAsksFirst() {
        setScreen(uiState = signedIn.copy(confirmSignOut = true))

        composeRule.onNodeWithText("Sign out?").assertIsDisplayed()
        composeRule.onNodeWithText("You will need to log in again to use Monica on this phone.").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Sign out"))[1].performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(listOf(1, 1), listOf(confirmations, dismissals))
    }

    @Test
    fun anErrorIsShownInASnackbar() {
        setScreen(uiState = signedIn.copy(error = AuthError.Unreachable))

        composeRule.onNodeWithText("Can't reach this instance. Check the address and your connection, then try again.").assertIsDisplayed()
    }

    @Test
    fun buttonsAreAtLeast48dpHigh() {
        setScreen()

        composeRule.onNodeWithText("Instance settings").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Sign out").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun showsInFrench() {
        setScreen(uiState = signedIn.copy(confirmSignOut = true), locale = Locale.FRENCH)

        composeRule.onNodeWithText("Bonjour, Jane Doe").assertIsDisplayed()
        composeRule.onNodeWithText("Connecté à monica.example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Se déconnecter ?").assertIsDisplayed()
    }

    @Test
    fun passesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen()

        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun passesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(darkTheme = true)

        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun theScreenHasATitleForScreenReaders() {
        setScreen()

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Home")).assertExists()
    }

    @Test
    fun signOutDialogTitleIsAHeading() {
        setScreen(uiState = signedIn.copy(confirmSignOut = true))

        composeRule.onNode(hasText("Sign out?") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
    }

    @Test
    fun theErrorStaysUntilDismissed() {
        composeRule.enableAccessibilityChecks()
        setScreen(uiState = signedIn.copy(error = AuthError.Unreachable))

        composeRule.onNodeWithText("Can't reach", substring = true).assertIsDisplayed()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun signOutDialogPassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(uiState = signedIn.copy(confirmSignOut = true))

        composeRule.onNodeWithText("Sign out?").tryPerformAccessibilityChecks()
    }

    @Test
    fun signOutDialogPassesAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
        setScreen(uiState = signedIn.copy(confirmSignOut = true), darkTheme = true)

        composeRule.onNodeWithText("Sign out?").tryPerformAccessibilityChecks()
    }
}
