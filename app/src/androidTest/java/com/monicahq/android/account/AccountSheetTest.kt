package com.monicahq.android.account

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
import com.monicahq.android.ui.theme.MonicaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AccountSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var instanceClicks = 0
    private var signOutClicks = 0
    private var dismissals = 0
    private var confirmations = 0

    private val signedIn = AccountUiState(name = "Jane Doe", initials = "JD", instanceUrl = "https://monica.example.com", sheetOpen = true)

    private fun setSheet(
        darkTheme: Boolean = false,
        locale: Locale? = null,
    ) {
        composeRule.setContent {
            WithLocale(locale) {
                MonicaTheme(darkTheme = darkTheme) {
                    AccountSheet(
                        uiState = signedIn,
                        onInstanceSettings = { instanceClicks++ },
                        onSignOut = { signOutClicks++ },
                        onDismiss = { dismissals++ },
                    )
                }
            }
        }
    }

    private fun setDialog(darkTheme: Boolean = false) {
        composeRule.setContent {
            MonicaTheme(darkTheme = darkTheme) {
                SignOutDialog(onConfirm = { confirmations++ }, onDismiss = { dismissals++ })
            }
        }
    }

    @Test
    fun showsThePersonAndTheInstance() {
        setSheet()

        composeRule.onNode(hasText("Jane Doe") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        composeRule.onNodeWithText("Connected to monica.example.com").assertIsDisplayed()
    }

    @Test
    fun buttonsReportTheirTapsAndAreAtLeast48dpHigh() {
        setSheet()

        composeRule.onNodeWithText("Instance settings").assertHeightIsAtLeast(48.dp).performClick()
        composeRule.onNodeWithText("Sign out").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(1, 1), listOf(instanceClicks, signOutClicks))
    }

    @Test
    fun signingOutAsksFirst() {
        setDialog()

        composeRule.onNode(hasText("Sign out?") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        composeRule.onNodeWithText("You will need to log in again to use Monica on this phone.").assertIsDisplayed()
        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(listOf(1, 1), listOf(confirmations, dismissals))
    }

    @Test
    fun showsInFrench() {
        setSheet(locale = Locale.FRENCH)

        composeRule.onNodeWithText("Connecté à monica.example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Se déconnecter").assertIsDisplayed()
    }

    @Test
    fun theSheetIsNamedForScreenReaders() {
        setSheet()

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Your account")).assertExists()
    }

    @Test
    fun passesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet()
        composeRule.onNodeWithText("Jane Doe").tryPerformAccessibilityChecks()
    }

    @Test
    fun passesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet(darkTheme = true)
        composeRule.onNodeWithText("Jane Doe").tryPerformAccessibilityChecks()
    }

    @Test
    fun signOutDialogPassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setDialog(darkTheme = true)
        composeRule.onNodeWithText("Sign out?").tryPerformAccessibilityChecks()
    }

    @Test
    fun signOutDialogPassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setDialog()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
