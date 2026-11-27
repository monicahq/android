package com.monicahq.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monicahq.android.auth.REDIRECT_URI
import com.monicahq.android.auth.SessionState
import com.monicahq.android.home.HomeRoute
import com.monicahq.android.ui.theme.MonicaTheme
import com.monicahq.android.welcome.WelcomeRoute
import com.monicahq.android.welcome.WelcomeViewModel

/**
 * The app's only activity. It opens sign-in pages in a Custom Tab and receives the browser's
 * answer: `singleTask` brings this activity back and closes the tab.
 */
class MainActivity : ComponentActivity() {
    private val welcomeViewModel: WelcomeViewModel by viewModels { WelcomeViewModel.Factory }

    /** True once the browser covered the app; a brief pause (a system dialog) does not count. */
    private var wasStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleCallback(intent)
        val sessionManager = (application as MonicaApplication).sessionManager
        setContent {
            MonicaTheme {
                val session by sessionManager.state.collectAsStateWithLifecycle()
                MonicaRoot(session = session, welcomeViewModel = welcomeViewModel, openBrowser = ::openBrowser)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallback(intent)
    }

    override fun onStop() {
        super.onStop()
        wasStopped = true
    }

    override fun onResume() {
        super.onResume()
        // The browser's answer, if any, arrived just before through onNewIntent.
        if (wasStopped) welcomeViewModel.onResume()
        wasStopped = false
    }

    private fun handleCallback(intent: Intent) {
        val data = intent.data?.toString() ?: return
        if (intent.action != Intent.ACTION_VIEW || !data.startsWith(REDIRECT_URI) || intent.hasExtra(HANDLED)) return
        // Handled once: a recreated activity gets the same intent again.
        intent.putExtra(HANDLED, true)
        welcomeViewModel.onCallback(data)
    }

    private companion object {
        const val HANDLED = "com.monicahq.android.CALLBACK_HANDLED"
    }

    /** A Custom Tab when a browser offers one, else the default browser. */
    private fun openBrowser(url: String) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, url.toUri())
        } catch (_: ActivityNotFoundException) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
        }
    }
}

/** Shows the welcome screen or the signed-in app, following the session. */
@Composable
private fun MonicaRoot(
    session: SessionState,
    welcomeViewModel: WelcomeViewModel,
    openBrowser: (String) -> Unit,
) {
    when (session) {
        SessionState.Loading -> Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
        is SessionState.SignedOut -> WelcomeRoute(viewModel = welcomeViewModel, openBrowser = openBrowser)
        is SessionState.SignedIn -> HomeRoute()
    }
}
