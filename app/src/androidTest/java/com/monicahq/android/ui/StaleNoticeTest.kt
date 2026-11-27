package com.monicahq.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.WithFontScale
import com.monicahq.android.WithLocale
import com.monicahq.android.isLiveRegion
import com.monicahq.android.ui.theme.MonicaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class StaleNoticeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_800_000_000_000L
    private val threeHoursAgo = now - 3 * 3_600_000L

    private fun setNotice(
        darkTheme: Boolean = false,
        locale: Locale? = null,
        fontScale: Float? = null,
    ) {
        composeRule.setContent {
            WithLocale(locale) {
                WithFontScale(fontScale) {
                    MonicaTheme(darkTheme = darkTheme) {
                        // On the page colour, as on the screens, so the contrast check is real.
                        Surface(color = MaterialTheme.colorScheme.background) { StaleNotice(staleSince = threeHoursAgo, now = now) }
                    }
                }
            }
        }
    }

    @Test
    fun saysItIsNotUpToDateAndSinceWhen() {
        setNotice()

        composeRule.onNodeWithText("Not up to date. Last updated 3 hours ago.").assertIsDisplayed()
    }

    @Test
    fun isAnnouncedWhenItAppears() {
        setNotice()

        composeRule.onNode(isLiveRegion() and hasText("Not up to date", substring = true)).assertExists()
    }

    @Test
    fun showsInFrench() {
        setNotice(locale = Locale.FRENCH)

        composeRule.onNodeWithText("Pas à jour. Mis à jour il y a 3 heures.").assertIsDisplayed()
    }

    @Test
    fun staysReadableAtTheLargestFont() {
        setNotice(fontScale = 2f)

        composeRule.onNodeWithText("Not up to date", substring = true).assertIsDisplayed()
    }

    @Test
    fun nothingIsShownWhenUpToDateButTheRegionIsReadyToAnnounce() {
        composeRule.setContent { MonicaTheme { StaleNotice(staleSince = null, now = now) } }

        composeRule.onNodeWithText("Not up to date", substring = true).assertDoesNotExist()
        composeRule.onNode(isLiveRegion()).assertExists()
    }

    @Test
    fun passesAccessibilityChecksInLightTheme() {
        composeRule.enableAccessibilityChecks()
        setNotice()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun passesAccessibilityChecksInDarkTheme() {
        composeRule.enableAccessibilityChecks()
        setNotice(darkTheme = true)
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
