package com.monicahq.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End to end: the app as people open it. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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
}
