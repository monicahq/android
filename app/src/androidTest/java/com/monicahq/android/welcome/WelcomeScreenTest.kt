package com.monicahq.android.welcome

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.WithFontScale
import com.monicahq.android.WithLocale
import com.monicahq.android.auth.AuthError
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import com.monicahq.android.instance.instanceHost
import com.monicahq.android.isLiveRegion
import com.monicahq.android.ui.theme.MonicaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class WelcomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val host = instanceHost(DEFAULT_INSTANCE_URL)
    private var signUpClicks = 0
    private var logInClicks = 0
    private var instanceClicks = 0

    private fun setScreen(
        uiState: WelcomeUiState = WelcomeUiState(),
        darkTheme: Boolean = false,
        locale: Locale? = null,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            WithLocale(locale) {
                WithFontScale(fontScale) {
                    MonicaTheme(darkTheme = darkTheme) {
                        WelcomeScreen(
                            uiState = uiState,
                            onSignUp = { signUpClicks++ },
                            onLogIn = { logInClicks++ },
                            onOpenInstanceSheet = { instanceClicks++ },
                        )
                    }
                }
            }
        }
    }

    private fun instanceLine(prefix: String = "Instance: ") = composeRule.onNode(hasText(prefix + host) and hasClickAction())

    @Test
    fun startingShowsProgressAndDisablesTheButtons() {
        setScreen(uiState = WelcomeUiState(status = SignInStatus.Starting))

        composeRule.onNodeWithText("Connecting to $host…").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Sign up").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("Log in").performScrollTo().assertIsNotEnabled()
        composeRule.onNode(hasText("Connecting to $host…") and isLiveRegion()).assertExists()
    }

    @Test
    fun completingShowsProgress() {
        setScreen(uiState = WelcomeUiState(status = SignInStatus.Completing))

        composeRule.onNodeWithText("Connecting to $host…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theInstanceLineStaysUsableWhileConnecting() {
        setScreen(uiState = WelcomeUiState(status = SignInStatus.Starting))

        instanceLine().performScrollTo().performClick()

        assertEquals(1, instanceClicks)
    }

    @Test
    fun anErrorIsShownAndAnnouncedAndLogInWorksAgain() {
        setScreen(uiState = WelcomeUiState(message = AuthError.Denied))

        composeRule
            .onNode(hasText("You did not allow access. Log in again to allow it.") and isLiveRegion())
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Log in")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        assertEquals(1, logInClicks)
    }

    @Test
    fun registrationClosedIsExplained() {
        setScreen(uiState = WelcomeUiState(message = AuthError.RegistrationClosed))

        composeRule
            .onNodeWithText(
                "This instance doesn't accept new accounts. Ask its administrator.",
            ).performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun endedSessionsAreExplained() {
        setScreen(uiState = WelcomeUiState(message = AuthError.SessionRevoked))

        composeRule.onNodeWithText("Your session was ended. Log in again.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun expiredSessionsAreExplained() {
        setScreen(uiState = WelcomeUiState(message = AuthError.SessionExpired))

        composeRule.onNodeWithText("Your session expired. Log in again.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun signInStatesShowInFrench() {
        setScreen(uiState = WelcomeUiState(status = SignInStatus.Starting), locale = Locale.FRENCH)

        composeRule.onNodeWithText("Connexion à $host…").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun errorsShowInFrench() {
        setScreen(uiState = WelcomeUiState(message = AuthError.RegistrationClosed), locale = Locale.FRENCH)

        composeRule
            .onNodeWithText("Cette instance n'accepte pas de nouveaux comptes. Demandez à son administrateur.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun progressPassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(uiState = WelcomeUiState(status = SignInStatus.Starting))

        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun progressPassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(uiState = WelcomeUiState(status = SignInStatus.Starting), darkTheme = true)

        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun theScreenHasATitleForScreenReaders() {
        setScreen()

        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Welcome to Monica")).assertExists()
    }

    @Test
    fun errorPassesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(uiState = WelcomeUiState(message = AuthError.SessionRevoked))

        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun errorPassesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setScreen(uiState = WelcomeUiState(message = AuthError.SessionRevoked), darkTheme = true)

        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun showsEveryElementOfTheDesign() {
        setScreen()

        composeRule.onNodeWithContentDescription("Instance settings").assertIsDisplayed()
        composeRule.onNodeWithText("Monica").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("A panda sitting in the grass, waving").assertIsDisplayed()
        composeRule.onNodeWithText("Welcome to Monica").assertIsDisplayed()
        composeRule
            .onNodeWithText("Monica helps you remember and organise your relationships and life context.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Sign up").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Log in").performScrollTo().assertIsDisplayed()
        instanceLine().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun signUpReportsTheTap() {
        setScreen()

        composeRule.onNodeWithText("Sign up").performScrollTo().performClick()

        assertEquals(1, signUpClicks)
        assertEquals(0, logInClicks + instanceClicks)
    }

    @Test
    fun logInReportsTheTap() {
        setScreen()

        composeRule.onNodeWithText("Log in").performScrollTo().performClick()

        assertEquals(1, logInClicks)
        assertEquals(0, signUpClicks + instanceClicks)
    }

    @Test
    fun settingsButtonAndInstanceLineOpenTheInstanceSettings() {
        setScreen()

        composeRule.onNodeWithContentDescription("Instance settings").performClick()
        instanceLine().performScrollTo().performClick()

        assertEquals(2, instanceClicks)
        assertEquals(0, signUpClicks + logInClicks)
    }

    @Test
    fun showsTheHostOfTheSavedInstance() {
        setScreen(uiState = WelcomeUiState(instanceUrl = "http://192.168.1.10:8080/monica"))

        composeRule
            .onNode(hasText("Instance: 192.168.1.10:8080/monica") and hasClickAction())
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun showsInDarkTheme() {
        setScreen(darkTheme = true)

        composeRule.onNodeWithText("Welcome to Monica").assertIsDisplayed()
        composeRule.onNodeWithText("Sign up").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsIconIsDrawnInInkInDarkTheme() {
        setScreen(darkTheme = true)

        // The automated checks do not see an icon drawn in the page colour, so look at its pixels.
        val pixels =
            composeRule
                .onNodeWithContentDescription("Instance settings")
                .captureToImage()
                .toPixelMap()
        val hasLightPixel =
            (0 until pixels.width).any { x ->
                (0 until pixels.height).any { y -> pixels[x, y].luminance() > 0.5f }
            }
        assertTrue("The settings icon is invisible on the dark page", hasLightPixel)
    }

    @Test
    fun showsInFrench() {
        setScreen(locale = Locale.FRENCH)

        composeRule.onNodeWithContentDescription("Paramètres de l'instance").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Un panda assis dans l'herbe qui fait coucou").assertIsDisplayed()
        composeRule.onNodeWithText("Bienvenue dans Monica").assertIsDisplayed()
        composeRule
            .onNodeWithText("Monica vous aide à vous souvenir et à organiser vos relations et votre contexte de vie.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Inscription").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Connexion").performScrollTo().assertIsDisplayed()
        instanceLine(prefix = "Instance : ").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun titleIsAHeading() {
        setScreen()

        composeRule
            .onNode(hasText("Welcome to Monica") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertExists()
    }

    @Test
    fun logoIsHiddenFromScreenReaders() {
        setScreen()

        // The only images with a description are the settings icon and the illustration.
        composeRule.onNode(hasContentDescription("Monica")).assertDoesNotExist()
    }

    @Test
    fun controlsAreAtLeast48dp() {
        setScreen()

        composeRule
            .onNodeWithContentDescription("Instance settings")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Sign up").performScrollTo().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText("Log in").performScrollTo().assertHeightIsAtLeast(48.dp)
        instanceLine().performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun everythingIsReachableWithTheLargestFont() {
        setScreen(fontScale = 2f)

        composeRule.onNodeWithText("Welcome to Monica").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText("Sign up")
            .performScrollTo()
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("Log in").performScrollTo().assertIsDisplayed()
        instanceLine().performScrollTo().assertIsDisplayed()
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
}
