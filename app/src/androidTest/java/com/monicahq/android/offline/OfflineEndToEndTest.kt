package com.monicahq.android.offline

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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

/** Spec 004, User Stories 1, 2 and 4: reading the vaults without the instance. */
@RunWith(AndroidJUnit4::class)
class OfflineEndToEndTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val monica = TestMonica()
    private val e2e = EndToEnd()

    @Before
    fun setUp() {
        e2e.setUp(monica.url)
        monica.addVaults("Personal", "Family", "Work")
    }

    @After
    fun tearDown() {
        e2e.tearDown()
        monica.close()
    }

    private fun signInAndLoad() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Log in").performScrollTo().performClick()
            e2e.sendCallback(scenario, e2e.authorizationUrl(composeRule))
            composeRule.waitForText("Work")
        }
    }

    private fun failVaultCalls() {
        monica.overrides["/api/v1/vaults"] = { TestMonica.json("{}", code = 503) }
        monica.overrides["/api/v1/vaults/vault-2"] = { TestMonica.json("{}", code = 503) }
    }

    @Test
    fun theListIsReadableWhenTheInstanceIsNotAndUpdatesWhenItIsBack() {
        signInAndLoad()
        failVaultCalls()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForText("Not up to date", substring = true)
            composeRule.onNodeWithText("Personal").assertExists()
            composeRule.onNodeWithText("Family").assertExists()
            composeRule.onNodeWithText("Work").assertExists()
        }

        monica.overrides.clear()
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForText("Work")
            composeRule.waitUntil(EndToEnd.TIMEOUT) {
                composeRule.onAllNodes(hasText("Not up to date", substring = true)).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    @Test
    fun theListIsReadableWithTheInstanceGone() {
        signInAndLoad()
        monica.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForText("Not up to date", substring = true)
            composeRule.onNodeWithText("Family").assertExists()
        }
    }

    @Test
    fun theCopyShowsFirstThenTheInstancesAnswer() {
        signInAndLoad()
        monica.vaults[1] = monica.vaults[1].copy(name = "Famille")
        // The instance answers slowly: the copy must show first.
        monica.overrides["/api/v1/vaults"] = { TestMonica.delayed(TestMonica.json(monicaPage()), 2_000) }

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForText("Family")
            composeRule.waitForText("Famille")
        }
    }

    @Test
    fun aVaultNeverOpenedOpensOfflineFromTheList() {
        signInAndLoad()
        failVaultCalls()

        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.waitForText("Family")
            composeRule.onNodeWithText("Family").performClick()
            composeRule.waitForText("Not up to date", substring = true)
        }
    }

    /** The vault list as the instance would answer it now. */
    private fun monicaPage(): String {
        val data =
            monica.vaults.mapIndexed { i, v ->
                """{"type":"vault","id":"${v.id}","attributes":{"name":"${v.name}","colour":"${v.colour}","position":${i + 1},"is_default":${v.isDefault},"contact_count":${v.contactCount}}}"""
            }
        return """{"data":[${data.joinToString(",")}],"meta":{"page":1,"per_page":100,"total":${data.size}}}"""
    }
}
