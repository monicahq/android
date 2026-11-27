package com.monicahq.android.vault

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.WithLocale
import com.monicahq.android.auth.AuthError
import com.monicahq.android.ui.theme.MonicaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class NewVaultScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<String>()

    private fun setScreen(
        uiState: NewVaultUiState = NewVaultUiState(),
        darkTheme: Boolean = false,
        locale: Locale? = null,
    ) {
        composeRule.setContent {
            WithLocale(locale) {
                MonicaTheme(darkTheme = darkTheme) {
                    NewVaultScreen(
                        uiState = uiState,
                        onNameChange = { events += "name:$it" },
                        onDescriptionChange = { events += "description:$it" },
                        onColourChange = { events += "colour:$it" },
                        onMakeDefaultChange = { events += "default:$it" },
                        onCreate = { events += "create" },
                        onLeave = { events += "leave" },
                        onConfirmDiscard = { events += "discard" },
                        onDismissDiscard = { events += "keep" },
                        onConnectionErrorShown = { events += "errorShown" },
                    )
                }
            }
        }
    }

    @Test
    fun theFieldsAreLabelledAndReportTyping() {
        setScreen()

        composeRule.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Family")
        composeRule.onNode(hasSetTextAction() and hasText("Description (optional)")).performTextInput("Everybody")

        // A stateless field the test never updates may also report going back to its value.
        assertTrue("name:Family" in events)
        assertTrue("description:Everybody" in events)
    }

    @Test
    fun aBlankNameCannotBeCreated() {
        setScreen()

        composeRule.onNodeWithText("Create vault").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aNamedVaultCanBeCreated() {
        setScreen(NewVaultUiState(name = "Family"))

        composeRule
            .onNodeWithText("Create vault")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf("create"), events)
    }

    @Test
    fun everyColourIsNamedAndTheChosenOneIsSelected() {
        setScreen(NewVaultUiState(colour = VaultColour.Green))

        VaultColour.entries.forEach { composeRule.onNodeWithContentDescription(it.name).assertExists() }
        composeRule.onNodeWithContentDescription("Green").assertIsSelected()
        composeRule.onNodeWithContentDescription("Blue").assertIsNotSelected().performClick()

        assertEquals(listOf("colour:Blue"), events)
    }

    @Test
    fun theDefaultOptionIsOnlyThereWhenOffered() {
        setScreen(NewVaultUiState(offerDefault = false))

        composeRule.onNodeWithText("Make this the default vault").assertDoesNotExist()
    }

    @Test
    fun theDefaultOptionToggles() {
        setScreen(NewVaultUiState(offerDefault = true))

        composeRule.onNodeWithText("Make this the default vault").performScrollTo().performClick()

        assertEquals(listOf("default:true"), events)
    }

    @Test
    fun savingSaysSoAndCannotBeRepeated() {
        setScreen(NewVaultUiState(name = "Family", saving = true))

        composeRule.onNodeWithText("Creating…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Creating…").assertIsNotEnabled()
    }

    @Test
    fun theInstanceRefusalIsShownUnderTheNameWhichTakesFocus() {
        setScreen(NewVaultUiState(name = "Family", nameError = "The free plan holds 1 vault."))

        composeRule.onNodeWithText("The free plan holds 1 vault.").assertIsDisplayed()
        composeRule
            .onNode(hasSetTextAction() and SemanticsMatcher.expectValue(SemanticsProperties.Error, "The free plan holds 1 vault."))
            .assertIsFocused()
    }

    @Test
    fun aGeneralRefusalIsShownAboveTheButton() {
        setScreen(NewVaultUiState(name = "Family", generalError = "Not now."))

        composeRule.onNodeWithText("Not now.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aConnectionProblemIsToldInASnackbar() {
        setScreen(NewVaultUiState(name = "Family", connectionError = AuthError.Unreachable))

        composeRule.onNodeWithText("Can't reach", substring = true).assertIsDisplayed()
    }

    @Test
    fun cancelAsksToLeave() {
        setScreen()

        composeRule.onNodeWithContentDescription("Cancel").performClick()

        assertEquals(listOf("leave"), events)
    }

    @Test
    fun theDiscardDialogAsksFirst() {
        setScreen(NewVaultUiState(name = "Family", confirmDiscard = true))

        composeRule
            .onNode(
                hasText("Discard this vault?") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
            ).assertIsDisplayed()
        composeRule.onNodeWithText("What you typed will be lost.").assertIsDisplayed()
        composeRule.onNodeWithText("Keep editing").performClick()
        composeRule.onNodeWithText("Discard").performClick()

        assertEquals(listOf("keep", "discard"), events)
    }

    @Test
    fun theTitleIsAHeadingAndThePaneIsNamed() {
        setScreen()

        composeRule.onNode(hasText("New vault") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "New vault")).assertExists()
    }

    @Test
    fun showsInFrench() {
        setScreen(NewVaultUiState(offerDefault = true), locale = Locale.FRENCH)

        composeRule.onNode(hasText("Nouveau coffre") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertExists()
        composeRule.onNode(hasSetTextAction() and hasText("Nom")).assertExists()
        composeRule.onNodeWithContentDescription("Sarcelle").assertExists()
        composeRule.onNodeWithText("Créer le coffre").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun passesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(NewVaultUiState(name = "Family", offerDefault = true, nameError = "Too long."))
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun passesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(NewVaultUiState(name = "Family", offerDefault = true, nameError = "Too long."), darkTheme = true)
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun theDiscardDialogPassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(NewVaultUiState(name = "Family", confirmDiscard = true))
        composeRule.onNodeWithText("Discard this vault?").tryPerformAccessibilityChecks()
    }

    @Test
    fun theDiscardDialogPassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(NewVaultUiState(name = "Family", confirmDiscard = true), darkTheme = true)
        composeRule.onNodeWithText("Discard this vault?").tryPerformAccessibilityChecks()
    }

    @Test
    fun colourAndDefaultCannotChangeWhileSaving() {
        setScreen(NewVaultUiState(name = "Family", offerDefault = true, saving = true))

        composeRule.onNodeWithContentDescription("Green").assertIsNotEnabled()
        composeRule.onNodeWithText("Make this the default vault").performScrollTo().assertIsNotEnabled()
    }
}
