package com.monicahq.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import com.monicahq.android.instance.instanceHost
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End to end: the app as people open it, and choosing an instance (User Story 2). */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val monica = TestMonica()
    private val e2e = EndToEnd()
    private val host get() = monica.url.substringAfter("://")

    @Before
    fun setUp() = e2e.setUp(DEFAULT_INSTANCE_URL)

    @After
    fun tearDown() {
        e2e.tearDown()
        monica.close()
    }

    private fun openSheetAndType(address: String) {
        composeRule.onNodeWithContentDescription("Instance settings").performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement(address)
    }

    @Test
    fun opensOnTheWelcomeScreenWithTheDefaultInstance() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForText("Welcome to Monica")
            composeRule.onNodeWithText("Instance: ${instanceHost(DEFAULT_INSTANCE_URL)}").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun aCompatibleInstanceIsCheckedThenKept() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSheetAndType(monica.url)
            composeRule.onNodeWithText("Save").performScrollTo().performClick()

            composeRule.waitForText("Instance: $host")
            composeRule.onNodeWithText("Instance URL").assertDoesNotExist()

            scenario.recreate()
            composeRule.onNodeWithText("Instance: $host").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun somethingThatIsNotMonicaIsRefused() {
        monica.overrides["/api/v1/status"] = { TestMonica.json("<html></html>") }
        ActivityScenario.launch(MainActivity::class.java).use {
            openSheetAndType(monica.url)
            composeRule.onNodeWithText("Save").performScrollTo().performClick()

            composeRule.waitForText("This address isn't a Monica instance. Check the address.")
            composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
            composeRule.onNodeWithText("Instance: ${instanceHost(DEFAULT_INSTANCE_URL)}").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun cancellingKeepsTheInstance() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule
                .onNode(
                    hasText("Instance: ${instanceHost(DEFAULT_INSTANCE_URL)}") and hasClickAction(),
                ).performScrollTo()
                .performClick()
            composeRule.onNode(hasSetTextAction()).performTextReplacement("monica.example.com")
            composeRule.onNodeWithText("Cancel").performScrollTo().performClick()

            composeRule.onNodeWithText("Instance URL").assertDoesNotExist()
            composeRule.onNodeWithText("Instance: ${instanceHost(DEFAULT_INSTANCE_URL)}").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun anInvalidAddressIsNotSaved() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSheetAndType("not an address")
            composeRule.onNodeWithText("Save").performScrollTo().performClick()

            composeRule.waitForText("Enter an address like https://monica.example.com")
            composeRule.onNodeWithText("Cancel").performScrollTo().performClick()
            composeRule.onNodeWithText("Instance: ${instanceHost(DEFAULT_INSTANCE_URL)}").performScrollTo().assertIsDisplayed()
        }
    }
}
