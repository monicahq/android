package com.monicahq.android

import com.monicahq.android.auth.AuthStore
import com.monicahq.android.auth.CompleteResult
import com.monicahq.android.auth.FakeTokenCipher
import com.monicahq.android.auth.REDIRECT_URI
import com.monicahq.android.auth.SessionManager
import com.monicahq.android.auth.SignInIntent
import com.monicahq.android.auth.SignInManager
import com.monicahq.android.auth.StartResult
import com.monicahq.android.auth.TokenEndpointClient
import com.monicahq.android.instance.FakePreferencesDataStore
import com.monicahq.android.instance.InstanceChecker
import com.monicahq.android.instance.InstanceRepository
import com.monicahq.android.network.HttpClients
import com.monicahq.android.offline.FakeOfflineCopy
import com.monicahq.android.vault.VaultRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals

/** The app's objects wired as in MonicaApplication, with fakes for storage and a clock tests move. */
class TestGraph {
    @Volatile
    var now = 1_700_000_000_000L

    val http = HttpClients.create()
    val authData = FakePreferencesDataStore()
    val store = AuthStore(authData, FakeTokenCipher())
    val tokenClient = TokenEndpointClient(http)
    val offlineCopy = FakeOfflineCopy()
    val sessionManager = SessionManager(store, tokenClient, http, offlineCopy, clock = { now })
    val checker = InstanceChecker(http, appVersion = "0.1.0", allowHttp = true)
    val signInManager = SignInManager(store, checker, tokenClient, sessionManager, deviceName = "Test phone", clock = { now })
    val instanceRepository = InstanceRepository(FakePreferencesDataStore())
    val vaultRepository = VaultRepository(sessionManager, offlineCopy, clock = { now })

    /** Starts a sign-in on [monica] and returns the authorization address the browser would open. */
    suspend fun startSignIn(
        monica: FakeMonica,
        intent: SignInIntent = SignInIntent.LogIn,
    ): String {
        val start = signInManager.start(monica.url, intent)
        check(start is StartResult.OpenBrowser) { "sign-in did not start: $start" }
        return start.url
    }

    /** The address the browser would open the app with, for the sign-in that opened [authorizationUrl]. */
    fun callback(
        authorizationUrl: String,
        code: String = FakeMonica.CODE,
    ): String = "$REDIRECT_URI?code=$code&state=${authorizationUrl.toHttpUrl().queryParameter("state")}"

    /** Signs in on [monica] from start to end. */
    suspend fun signIn(monica: FakeMonica) {
        sessionManager.load()
        val url = startSignIn(monica)
        assertEquals(CompleteResult.SignedIn, signInManager.complete(callback(url)))
    }
}
