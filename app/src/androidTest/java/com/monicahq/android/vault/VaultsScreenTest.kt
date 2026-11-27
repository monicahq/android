package com.monicahq.android.vault

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.WithFontScale
import com.monicahq.android.WithLocale
import com.monicahq.android.auth.AuthError
import com.monicahq.android.ui.theme.MonicaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class VaultsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clicked = mutableListOf<String>()
    private var newVaultClicks = 0
    private var refreshes = 0
    private var tries = 0
    private var accountClicks = 0

    private val vaults =
        listOf(
            Vault("v1", "Personal", null, VaultColour.Blue, isDefault = true, contactCount = 25),
            Vault("v2", "Family", null, VaultColour.Green, isDefault = false, contactCount = 1),
            Vault("v3", "Work", null, VaultColour.Amber, isDefault = false, contactCount = 0),
        )

    private val everyColour =
        VaultColour.entries.mapIndexed { i, colour -> Vault("c$i", colour.name, null, colour, isDefault = i == 0, contactCount = i) }

    private fun setScreen(
        uiState: VaultsUiState = VaultsUiState(VaultsContent.Loaded(vaults)),
        initials: String? = "JD",
        darkTheme: Boolean = false,
        locale: Locale? = null,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            WithLocale(locale) {
                WithFontScale(fontScale) {
                    MonicaTheme(darkTheme = darkTheme) {
                        VaultsScreen(
                            uiState = uiState,
                            accountInitials = initials,
                            onAccountClick = { accountClicks++ },
                            onVaultClick = { clicked += it },
                            onNewVault = { newVaultClicks++ },
                            onRefresh = { refreshes++ },
                            onTryAgain = { tries++ },
                            onRefreshErrorShown = {},
                        )
                    }
                }
            }
        }
    }

    private fun heading(text: String) = hasText(text) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun loadingSaysSo() {
        setScreen(VaultsUiState())

        composeRule.onNodeWithContentDescription("Loading your vaults").assertExists()
        composeRule.onNode(heading("All your vaults")).assertIsDisplayed()
    }

    @Test
    fun showsEveryVaultWithItsContactsAndOneDefault() {
        setScreen()

        composeRule.onNode(heading("All your vaults")).assertIsDisplayed()
        composeRule.onNodeWithText("Personal", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("25 contacts", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("1 contact", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("No contacts", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("Default", substring = true).assertCountEquals(1)
    }

    @Test
    fun aCardIsOneControlReadWithItsNameContactsAndDefault() {
        setScreen()

        val card = composeRule.onNode(hasText("Personal") and hasText("25 contacts") and hasText("Default"))
        card.assertHasClickAction()
        card.performClick()

        assertEquals(listOf("v1"), clicked)
    }

    @Test
    fun theNewVaultButtonOpensTheForm() {
        setScreen()

        composeRule.onNodeWithText("New vault").performClick()

        assertEquals(1, newVaultClicks)
    }

    @Test
    fun theListOffersRefreshWithoutTheGesture() {
        setScreen()

        val list = composeRule.onNode(hasScrollAction())
        val action =
            list
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == "Refresh" }
        composeRule.runOnIdle { action.action() }

        assertEquals(1, refreshes)
    }

    @Test
    fun everyCardOffersRefreshToo() {
        setScreen()

        val card = composeRule.onNode(hasText("Family") and hasClickAction())
        val action = card.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Refresh" }
        composeRule.runOnIdle { action.action() }

        assertEquals(1, refreshes)
    }

    @Test
    fun theDefaultBadgeSaysDefaultVault() {
        setScreen()

        composeRule.onNode(hasContentDescription("Default vault"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun aFailedLoadSaysWhyAndCanBeTriedAgain() {
        setScreen(VaultsUiState(VaultsContent.Failed(AuthError.Unreachable)))

        composeRule.onNodeWithText("Can't reach", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(1, tries)
    }

    @Test
    fun aFailedRefreshIsToldAndTheListStays() {
        setScreen(VaultsUiState(VaultsContent.Loaded(vaults), refreshError = AuthError.Unreachable))

        composeRule.onNodeWithText("Your vaults could not be updated.").assertIsDisplayed()
        composeRule.onNodeWithText("Personal", substring = true).assertExists()
    }

    @Test
    fun theAccountButtonIsNamedAndShowsTheInitials() {
        setScreen()

        composeRule.onNodeWithContentDescription("Your account").assertHasClickAction().performClick()
        composeRule.onNodeWithText("JD", useUnmergedTree = true).assertIsDisplayed()

        assertEquals(1, accountClicks)
    }

    @Test
    fun aNameWithoutLettersShowsAPersonIconOnTheSameButton() {
        setScreen(initials = null)

        composeRule.onNodeWithContentDescription("Your account").assertHasClickAction()
        composeRule.onNodeWithText("JD", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun theEmptyStateExplainsAndOffersTheFirstVault() {
        setScreen(VaultsUiState(VaultsContent.Loaded(emptyList())))

        composeRule.onNode(heading("All your vaults")).assertIsDisplayed()
        composeRule.onNode(heading("No vault yet")).assertIsDisplayed()
        composeRule.onNodeWithText("A vault holds the people", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("New vault").assertCountEquals(1)
        composeRule.onNodeWithText("New vault").performClick()

        assertEquals(1, newVaultClicks)
    }

    @Test
    fun theEmptyStateStaysReadableAtTheLargestFont() {
        setScreen(VaultsUiState(VaultsContent.Loaded(emptyList())), fontScale = 2f)

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("New vault"))
        composeRule.onNodeWithText("New vault").assertIsDisplayed()
    }

    @Test
    fun theScreenHasATitleForScreenReaders() {
        setScreen()

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "All your vaults")).assertExists()
    }

    @Test
    fun showsInFrench() {
        setScreen(locale = Locale.FRENCH)

        composeRule.onNode(heading("Tous vos coffres")).assertIsDisplayed()
        composeRule.onNodeWithText("25 contacts", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Par défaut", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Nouveau coffre").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Votre compte")).assertExists()
    }

    @Test
    fun passesAccessibilityChecksWithEveryColourInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultsUiState(VaultsContent.Loaded(everyColour)))

        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun passesAccessibilityChecksWithEveryColourInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultsUiState(VaultsContent.Loaded(everyColour)), darkTheme = true)

        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun theEmptyStatePassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultsUiState(VaultsContent.Loaded(emptyList())), darkTheme = true)
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun theEmptyStatePassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultsUiState(VaultsContent.Loaded(emptyList())))
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun theFailedStatePassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultsUiState(VaultsContent.Failed(AuthError.ServerError)), darkTheme = true)
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun loadingPassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultsUiState(), darkTheme = true)
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun aListFromTheCopySaysItIsNotUpToDate() {
        setScreen(VaultsUiState(VaultsContent.Loaded(vaults), staleSince = System.currentTimeMillis() - 3 * 3_600_000L))

        composeRule.onNodeWithText("Not up to date", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Personal", substring = true).assertIsDisplayed()
    }

    @Test
    fun anUpToDateListSaysNothingMore() {
        setScreen()

        composeRule.onNodeWithText("Not up to date", substring = true).assertDoesNotExist()
    }

    @Test
    fun theNewAnswerReplacesTheCopyWithoutAnyProgressIndicator() {
        var uiState by mutableStateOf(VaultsUiState(VaultsContent.Loaded(vaults)))
        composeRule.setContent {
            MonicaTheme {
                VaultsScreen(uiState, "JD", {}, {}, {}, {}, {}, {})
            }
        }

        uiState = VaultsUiState(VaultsContent.Loaded(vaults.map { if (it.id == "v2") it.copy(name = "Famille") else it }))

        composeRule.onNodeWithText("Famille", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Personal", substring = true).assertIsDisplayed()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertDoesNotExist()
    }

    @Test
    fun theNotUpToDateLinePassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultsUiState(VaultsContent.Loaded(everyColour), staleSince = System.currentTimeMillis() - 60_000L), darkTheme = true)
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun theNotUpToDateLinePassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(VaultsUiState(VaultsContent.Loaded(everyColour), staleSince = System.currentTimeMillis() - 60_000L))
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun theNotUpToDateLineStaysReadableAtTheLargestFont() {
        setScreen(VaultsUiState(VaultsContent.Loaded(vaults), staleSince = System.currentTimeMillis() - 60_000L), fontScale = 2f)

        composeRule.onNodeWithText("Not up to date", substring = true).assertIsDisplayed()
    }
}
