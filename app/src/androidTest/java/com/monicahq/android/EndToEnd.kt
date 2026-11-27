package com.monicahq.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.printToLog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasDataString
import com.monicahq.android.auth.REDIRECT_URI
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.startsWith

/**
 * Drives the real app against [TestMonica]s. The browser is never opened: Espresso-Intents catches
 * the sign-in page the app asks for, and the test plays the browser by sending the answer back.
 */
class EndToEnd {
    val app: MonicaApplication = ApplicationProvider.getApplicationContext()

    /** Starts from a phone where nobody is signed in and [instanceUrl] is saved. */
    fun setUp(instanceUrl: String) {
        runBlocking {
            app.sessionManager.logOut()
            app.instanceRepository.setUrl(instanceUrl)
        }
        Intents.init()
        intending(allOf(hasAction(Intent.ACTION_VIEW), hasDataString(startsWith("http"))))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
    }

    fun tearDown() {
        Intents.release()
        runBlocking {
            app.sessionManager.logOut()
            app.instanceRepository.setUrl(DEFAULT_INSTANCE_URL)
        }
    }

    /** The sign-in page the app last asked the browser to open. */
    fun authorizationUrl(composeRule: ComposeTestRule): String {
        try {
            composeRule.waitUntil(TIMEOUT) { Intents.getIntents().any { it.dataString?.contains("/oauth/authorize") == true } }
        } catch (e: Throwable) {
            android.util.Log.d("E2E", "intents: " + Intents.getIntents().map { it.toString() })
            composeRule
                .onAllNodes(
                    androidx.compose.ui.test
                        .isRoot(),
                ).printToLog("E2E", maxDepth = Int.MAX_VALUE)
            throw e
        }
        return checkNotNull(Intents.getIntents().last { it.dataString?.contains("/oauth/authorize") == true }.dataString)
    }

    /** Sends the app the browser's answer to the sign-in that opened [authorizationUrl]. */
    fun sendCallback(
        scenario: ActivityScenario<MainActivity>,
        authorizationUrl: String,
        query: String = "code=${TestMonica.CODE}",
    ) {
        val state = authorizationUrl.toHttpUrl().queryParameter("state")
        // Started from the activity, as the browser's answer lands in the app's own task.
        scenario.onActivity { activity ->
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("$REDIRECT_URI?$query&state=$state")).setPackage(activity.packageName),
            )
        }
    }

    companion object {
        const val TIMEOUT = 10_000L
    }
}

/** Waits until [text] is on screen. */
fun ComposeTestRule.waitForText(
    text: String,
    substring: Boolean = false,
) = try {
    waitUntil(EndToEnd.TIMEOUT) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
} catch (e: Throwable) {
    onAllNodes(
        androidx.compose.ui.test
            .isRoot(),
    ).printToLog("E2E", maxDepth = Int.MAX_VALUE)
    throw e
}
