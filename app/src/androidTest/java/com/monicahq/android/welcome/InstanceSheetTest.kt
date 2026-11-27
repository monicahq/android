package com.monicahq.android.welcome

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.WithFontScale
import com.monicahq.android.WithLocale
import com.monicahq.android.auth.AuthError
import com.monicahq.android.isLiveRegion
import com.monicahq.android.ui.theme.MonicaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class InstanceSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val drafts = mutableListOf<String>()
    private var resets = 0
    private var dismissals = 0
    private var saves = 0
    private var confirmations = 0
    private var cancellations = 0

    private val editing = InstanceSheetState(draft = "https://monica.example.com")
    private val invalid = InstanceSheetState(draft = "not an address", error = AuthError.InvalidAddress)
    private val invalidMessage = "Enter an address like https://monica.example.com"

    private fun setSheet(
        state: InstanceSheetState = editing,
        darkTheme: Boolean = false,
        locale: Locale? = null,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            WithLocale(locale) {
                WithFontScale(fontScale) {
                    MonicaTheme(darkTheme = darkTheme) {
                        InstanceSheet(
                            state = state,
                            onDraftChange = { drafts += it },
                            onReset = { resets++ },
                            onDismiss = { dismissals++ },
                            onSave = { saves++ },
                            onConfirmSwitch = { confirmations++ },
                            onCancelSwitch = { cancellations++ },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun showsEveryElement() {
        setSheet()

        composeRule.onNodeWithText("Instance URL").assertIsDisplayed()
        composeRule.onNodeWithText("The address", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Address").assertIsDisplayed()
        composeRule.onNodeWithText("https://monica.example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Reset to default").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun titleIsAHeading() {
        setSheet()

        composeRule
            .onNode(hasText("Instance URL") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertExists()
    }

    @Test
    fun reportsTyping() {
        setSheet()

        composeRule.onNodeWithText("https://monica.example.com").performTextReplacement("monica.example.org")

        assertEquals(listOf("monica.example.org"), drafts)
    }

    @Test
    fun buttonsReportTheirTaps() {
        setSheet()

        composeRule.onNodeWithText("Reset to default").performClick()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(listOf(1, 1, 1), listOf(resets, saves, dismissals))
    }

    @Test
    fun keyboardDoneSaves() {
        setSheet()

        composeRule.onNodeWithText("https://monica.example.com").performImeAction()

        assertEquals(1, saves)
    }

    @Test
    fun checkingIsAnnouncedAndBlocksSaving() {
        setSheet(state = editing.copy(isChecking = true))

        composeRule.onNode(hasText("Checking monica.example.com…") and isLiveRegion()).assertIsDisplayed()
        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Reset to default").assertIsNotEnabled()
    }

    @Test
    fun errorIsShownOnTheFieldAndFocusMovesThere() {
        setSheet(state = invalid)

        composeRule.onNodeWithText(invalidMessage).assertIsDisplayed()
        composeRule
            .onNode(hasText("not an address") and SemanticsMatcher.expectValue(SemanticsProperties.Error, invalidMessage))
            .assertExists()
        // The sheet's window takes focus a moment after it appears.
        composeRule.waitUntil(5_000) {
            composeRule
                .onAllNodes(hasSetTextAction() and SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun everyCheckErrorHasItsOwnMessage() {
        setSheet(state = editing.copy(error = AuthError.HttpsRequired))

        composeRule.onNodeWithText("This instance must use HTTPS. Enter an address that starts with https://").assertIsDisplayed()
    }

    @Test
    fun switchingAsksFirst() {
        setSheet(state = editing.copy(switchFrom = "app.monicahq.com"))

        composeRule.onNodeWithText("Switch instance?").assertIsDisplayed()
        composeRule.onNodeWithText("Switching instance signs you out of app.monicahq.com.").assertIsDisplayed()
        composeRule.onNodeWithText("Switch and sign out").performClick()

        assertEquals(1, confirmations)
    }

    @Test
    fun switchCanBeCancelled() {
        setSheet(state = editing.copy(switchFrom = "app.monicahq.com"))

        // The sheet behind the dialog has its own "Cancel"; the dialog's comes last.
        composeRule.onAllNodes(hasText("Cancel"))[1].performClick()

        assertEquals(1, cancellations)
    }

    @Test
    fun buttonsAreAtLeast48dpHigh() {
        setSheet()

        listOf("Reset to default", "Cancel", "Save").forEach {
            composeRule.onNodeWithText(it).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun showsInFrench() {
        setSheet(state = editing.copy(switchFrom = "app.monicahq.com"), locale = Locale.FRENCH)

        composeRule.onNodeWithText("Changer d'instance ?").assertIsDisplayed()
        composeRule.onNodeWithText("Changer d'instance vous déconnecte de app.monicahq.com.").assertIsDisplayed()
        composeRule.onNodeWithText("Changer et se déconnecter").assertIsDisplayed()
    }

    @Test
    fun checkingShowsInFrench() {
        setSheet(state = editing.copy(isChecking = true), locale = Locale.FRENCH)

        composeRule.onNodeWithText("URL de l'instance").assertIsDisplayed()
        composeRule.onNodeWithText("Vérification de monica.example.com…").assertIsDisplayed()
    }

    @Test
    fun isReachableWithTheLargestFont() {
        setSheet(state = invalid, fontScale = 2f)

        // The error moves focus to the field, so the keyboard slides in: scroll once it settled.
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithText("Save").performScrollTo().assertIsDisplayed() }.isSuccess
        }
        composeRule.onNodeWithText("Reset to default").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun passesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet()

        composeRule.onNodeWithText("Instance URL").tryPerformAccessibilityChecks()
    }

    @Test
    fun passesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet(darkTheme = true)

        composeRule.onNodeWithText("Instance URL").tryPerformAccessibilityChecks()
    }

    @Test
    fun errorPassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet(state = invalid)

        composeRule.onNodeWithText("Instance URL").tryPerformAccessibilityChecks()
    }

    @Test
    fun errorPassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet(state = invalid, darkTheme = true)

        composeRule.onNodeWithText("Instance URL").tryPerformAccessibilityChecks()
    }

    @Test
    fun checkingPassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet(state = editing.copy(isChecking = true))

        composeRule.onNodeWithText("Instance URL").tryPerformAccessibilityChecks()
    }

    @Test
    fun checkingPassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet(state = editing.copy(isChecking = true), darkTheme = true)

        composeRule.onNodeWithText("Instance URL").tryPerformAccessibilityChecks()
    }

    @Test
    fun switchDialogTitleIsAHeading() {
        setSheet(state = editing.copy(switchFrom = "app.monicahq.com"))

        composeRule
            .onNode(hasText("Switch instance?") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertExists()
    }

    @Test
    fun switchDialogPassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet(state = editing.copy(switchFrom = "app.monicahq.com"))

        composeRule.onNodeWithText("Switch instance?").tryPerformAccessibilityChecks()
    }

    @Test
    fun switchDialogPassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setSheet(state = editing.copy(switchFrom = "app.monicahq.com"), darkTheme = true)

        composeRule.onNodeWithText("Switch instance?").tryPerformAccessibilityChecks()
    }
}
