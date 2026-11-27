package com.monicahq.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End to end: the app as people open it. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // The saved address lives on the device: every test starts and ends on the default one.
    @Before
    @After
    fun useDefaultInstance() =
        runBlocking {
            (composeRule.activity.application as MonicaApplication).instanceRepository.setUrl(DEFAULT_INSTANCE_URL)
        }

    @Test
    fun opensOnTheWelcomeScreenWithTheDefaultInstance() {
        composeRule.onNodeWithText("Welcome to Monica").assertIsDisplayed()
        composeRule.onNodeWithText("Instance: app.monicahq.com").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingLogInDoesNothingYet() {
        composeRule.onNodeWithText("Log in").performScrollTo().performClick()

        composeRule.onNodeWithText("Welcome to Monica").assertIsDisplayed()
    }

    @Test
    fun choosingAnotherInstanceIsKept() {
        composeRule.onNodeWithContentDescription("Instance settings").performClick()
        composeRule.onNodeWithText("https://app.monicahq.com").performTextReplacement("monica.example.com")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Instance URL").assertDoesNotExist()
        composeRule.onNodeWithText("Instance: monica.example.com").performScrollTo().assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText("Instance: monica.example.com").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancellingKeepsTheInstance() {
        composeRule.onNode(hasText("Instance: app.monicahq.com") and hasClickAction()).performScrollTo().performClick()
        composeRule.onNodeWithText("https://app.monicahq.com").performTextReplacement("monica.example.com")
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Instance URL").assertDoesNotExist()
        composeRule.onNodeWithText("Instance: app.monicahq.com").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anInvalidAddressIsNotSaved() {
        composeRule.onNodeWithContentDescription("Instance settings").performClick()
        composeRule.onNodeWithText("https://app.monicahq.com").performTextReplacement("not an address")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Enter an address like https://monica.example.com").assertIsDisplayed()
        composeRule.onNodeWithText("Reset to default").performClick()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.onNodeWithText("Instance: app.monicahq.com").performScrollTo().assertIsDisplayed()
    }
}
