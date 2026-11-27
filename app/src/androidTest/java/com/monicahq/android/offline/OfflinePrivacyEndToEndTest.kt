package com.monicahq.android.offline

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Spec 004, User Story 3: nothing of a session stays on the phone once it ends. */
@RunWith(AndroidJUnit4::class)
class OfflinePrivacyEndToEndTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val monica = TestMonica()
    private val e2e = EndToEnd()
    private val database get() = File(e2e.app.noBackupFilesDir, "offline/${RoomOfflineCopy.DATABASE_FILE}")

    @Before
    fun setUp() {
        e2e.setUp(monica.url)
        monica.addVaults("Personal", "Family")
    }

    @After
    fun tearDown() {
        e2e.tearDown()
        monica.close()
    }

    private fun signInAndLoad(scenario: ActivityScenario<MainActivity>) {
        composeRule.onNodeWithText("Log in").performScrollTo().performClick()
        e2e.sendCallback(scenario, e2e.authorizationUrl(composeRule))
        composeRule.waitForText("Family")
        assertTrue("the list was not kept", database.exists())
    }

    @Test
    fun signingOutOfflineLeavesNothingOnThePhone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signInAndLoad(scenario)
            monica.close()

            composeRule.onNodeWithContentDescription("Your account").performClick()
            composeRule.onNodeWithText("Sign out").performClick()
            composeRule.waitForText("Sign out?")
            composeRule.onNodeWithText("Sign out").performClick()

            composeRule.waitForText("Welcome to Monica")
            assertFalse(database.exists())
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForText("Welcome to Monica")
            assertFalse(database.exists())
        }
    }

    @Test
    fun aSessionEndedOnTheWebErasesTheCopy() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            signInAndLoad(scenario)
        }
        monica.validAccessToken = null
        monica.validRefreshToken = null

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForText("Your session was ended. Log in again.")
            assertFalse(database.exists())
        }
    }
}
