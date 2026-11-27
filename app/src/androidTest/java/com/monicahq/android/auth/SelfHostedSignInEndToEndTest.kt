package com.monicahq.android.auth

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monicahq.android.EndToEnd
import com.monicahq.android.MainActivity
import com.monicahq.android.TestMonica
import com.monicahq.android.waitForText
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** User Story 2: a self-hosted Monica served under a path. */
@RunWith(AndroidJUnit4::class)
class SelfHostedSignInEndToEndTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val monica = TestMonica(basePath = "/monica")
    private val e2e = EndToEnd()
    private val host get() = monica.url.substringAfter("://")

    @Before
    fun setUp() = e2e.setUp(monica.url)

    @After
    fun tearDown() {
        e2e.tearDown()
        monica.close()
    }

    @Test
    fun loggingInToAnInstanceUnderAPathWorks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Instance: $host").performScrollTo()
            composeRule.onNodeWithText("Log in").performScrollTo().performClick()

            e2e.sendCallback(scenario, e2e.authorizationUrl(composeRule))

            composeRule.waitForText("All your vaults")
            composeRule.onNodeWithContentDescription("Your account").performClick()
            composeRule.waitForText("Connected to $host")
        }
    }
}
