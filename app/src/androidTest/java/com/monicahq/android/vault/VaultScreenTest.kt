package com.monicahq.android.vault

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
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
class VaultScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var backs = 0
    private var tries = 0

    private val family = Vault("v2", "Family", "Everybody I am related to.", VaultColour.Green, isDefault = false, contactCount = 12)

    private fun setScreen(
        uiState: VaultUiState = VaultUiState.Loaded(family),
        darkTheme: Boolean = false,
        locale: Locale? = null,
    ) {
        composeRule.setContent {
            WithLocale(locale) {
                MonicaTheme(darkTheme = darkTheme) {
                    VaultScreen(uiState = uiState, onBack = { backs++ }, onTryAgain = { tries++ })
                }
            }
        }
    }

    @Test
    fun showsTheVaultWithItsNameAsAHeading() {
        setScreen()

        composeRule.onNode(hasText("Family") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        composeRule.onNodeWithText("Everybody I am related to.").assertIsDisplayed()
    }

    @Test
    fun aVaultWithoutDescriptionShowsNoEmptyText() {
        setScreen(VaultUiState.Loaded(family.copy(description = null)))

        composeRule.onNodeWithText("Everybody", substring = true).assertDoesNotExist()
    }

    @Test
    fun theBackButtonGoesBack() {
        setScreen()

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun aDeletedVaultSaysSoAndLeadsBack() {
        setScreen(VaultUiState.NotFound)

        composeRule.onNodeWithText("This vault no longer exists.").assertIsDisplayed()
        composeRule.onNodeWithText("Back to your vaults").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun aFailureCanBeTriedAgain() {
        setScreen(VaultUiState.Failed(AuthError.Timeout))

        composeRule.onNodeWithText("The instance took too long", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(1, tries)
    }

    @Test
    fun showsInFrench() {
        setScreen(VaultUiState.NotFound, locale = Locale.FRENCH)

        composeRule.onNodeWithText("Ce coffre n’existe plus.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Retour").assertExists()
    }

    @Test
    fun theScreenIsNamedAndLoadingIsSaidBeforeTheVaultIsKnown() {
        setScreen(VaultUiState.Loading)

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Vault")).assertExists()
        composeRule.onNodeWithContentDescription("Loading the vault").assertExists()
    }

    @Test
    fun theFailedStatePassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultUiState.Failed(AuthError.ServerError))
        composeRule.onRoot().tryPerformAccessibilityChecks()
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
    fun theNotFoundStatePassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultUiState.NotFound, darkTheme = true)
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun aVaultFromTheCopySaysItIsNotUpToDate() {
        setScreen(VaultUiState.Loaded(family, staleSince = System.currentTimeMillis() - 3 * 3_600_000L))

        composeRule.onNodeWithText("Not up to date", substring = true).assertIsDisplayed()
    }

    @Test
    fun aVaultFromTheCopyPassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultUiState.Loaded(family, staleSince = System.currentTimeMillis() - 60_000L))
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun aVaultFromTheCopyPassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultUiState.Loaded(family, staleSince = System.currentTimeMillis() - 60_000L), darkTheme = true)
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
