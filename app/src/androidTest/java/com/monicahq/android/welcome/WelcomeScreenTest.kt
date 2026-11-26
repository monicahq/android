package com.monicahq.android.welcome

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    private val host = "app.monicahq.com"
    private var signUpClicks = 0
    private var logInClicks = 0
    private var instanceClicks = 0

    private fun setScreen(
        darkTheme: Boolean = false,
        locale: Locale? = null,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            WithLocale(locale) {
                WithFontScale(fontScale) {
                    MonicaTheme(darkTheme = darkTheme) {
                        WelcomeScreen(
                            instanceHost = host,
                            onSignUp = { signUpClicks++ },
                            onLogIn = { logInClicks++ },
                            onInstanceSettings = { instanceClicks++ },
                        )
                    }
                }
            }
        }
    }

    private fun instanceLine(prefix: String = "Instance: ") = composeRule.onNode(hasText(prefix + host) and hasClickAction())

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

/** Shows [content] with the resources of [locale], without changing the device's language. */
@Composable
private fun WithLocale(
    locale: Locale?,
    content: @Composable () -> Unit,
) {
    if (locale == null) return content()
    val context = LocalContext.current
    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
    CompositionLocalProvider(
        LocalContext provides context.createConfigurationContext(configuration),
        LocalConfiguration provides configuration,
        content = content,
    )
}

/** Shows [content] as with the system font size set to [fontScale]. */
@Composable
private fun WithFontScale(
    fontScale: Float?,
    content: @Composable () -> Unit,
) {
    if (fontScale == null) return content()
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale),
        content = content,
    )
}
