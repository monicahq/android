package com.monicahq.android.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monicahq.android.MonicaApplication
import com.monicahq.android.auth.AuthError
import com.monicahq.android.auth.CompleteResult
import com.monicahq.android.auth.SessionManager
import com.monicahq.android.auth.SessionState
import com.monicahq.android.auth.SignInIntent
import com.monicahq.android.auth.SignInManager
import com.monicahq.android.auth.SignOutReason
import com.monicahq.android.auth.StartResult
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import com.monicahq.android.instance.InstanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInStatus { Idle, Starting, Completing }

/**
 * Everything the welcome screen shows. [message] explains what just went wrong; [browserUrl] is
 * the sign-in page to open, until the screen reports it opened it.
 */
data class WelcomeUiState(
    val instanceUrl: String = DEFAULT_INSTANCE_URL,
    val status: SignInStatus = SignInStatus.Idle,
    val message: AuthError? = null,
    val browserUrl: String? = null,
)

private data class SignInProgress(
    val status: SignInStatus = SignInStatus.Idle,
    val message: AuthError? = null,
    val browserUrl: String? = null,
)

/** The welcome screen: logging in and signing up through the instance's page in the browser. */
class WelcomeViewModel(
    private val instanceRepository: InstanceRepository,
    private val signInManager: SignInManager,
    sessionManager: SessionManager,
) : ViewModel() {
    private val progress = MutableStateFlow(SignInProgress())

    /** True between opening the browser and coming back to the app. */
    private var browserOpen = false

    /** What the last attempt was for, to explain a return without an answer. */
    private var intent = SignInIntent.LogIn

    val uiState: StateFlow<WelcomeUiState> =
        combine(instanceRepository.url, progress) { url, progress ->
            WelcomeUiState(url, progress.status, progress.message, progress.browserUrl)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WelcomeUiState())

    init {
        viewModelScope.launch {
            sessionManager.state.collect { state ->
                // Nothing said about an earlier attempt still applies once someone is signed in.
                if (state is SessionState.SignedIn) {
                    browserOpen = false
                    progress.value = SignInProgress()
                }
                val message =
                    when ((state as? SessionState.SignedOut)?.reason) {
                        SignOutReason.Expired -> AuthError.SessionExpired
                        SignOutReason.Revoked -> AuthError.SessionRevoked
                        else -> null
                    }
                if (message != null) progress.update { it.copy(message = message) }
            }
        }
    }

    fun logIn() = start(SignInIntent.LogIn)

    fun signUp() = start(SignInIntent.SignUp)

    /** The screen opened [WelcomeUiState.browserUrl]. */
    fun onBrowserOpened() {
        browserOpen = true
        progress.update { it.copy(browserUrl = null) }
    }

    /** The browser sent the person back with an answer. */
    fun onCallback(uri: String) {
        browserOpen = false
        progress.update { it.copy(status = SignInStatus.Completing, message = null, browserUrl = null) }
        viewModelScope.launch {
            val message =
                when (val result = signInManager.complete(uri)) {
                    is CompleteResult.Failed -> result.error
                    CompleteResult.SignedIn, CompleteResult.Ignored -> null
                }
            progress.value = SignInProgress(message = message)
        }
    }

    /**
     * The app is in front again without an answer from the browser. The attempt is kept: the answer
     * can still come, for example from the link of the email confirming a new account.
     */
    fun onResume() {
        if (!browserOpen) return
        browserOpen = false
        val message = if (intent == SignInIntent.SignUp) AuthError.SignUpUnfinished else AuthError.Cancelled
        progress.value = SignInProgress(message = message)
    }

    private fun start(intent: SignInIntent) {
        if (progress.value.status != SignInStatus.Idle) return
        progress.value = SignInProgress(status = SignInStatus.Starting)
        this.intent = intent
        viewModelScope.launch {
            progress.value =
                when (val result = signInManager.start(instanceRepository.url.first(), intent)) {
                    is StartResult.OpenBrowser -> SignInProgress(status = SignInStatus.Starting, browserUrl = result.url)
                    is StartResult.Failed -> SignInProgress(message = result.error)
                }
        }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MonicaApplication
                    WelcomeViewModel(app.instanceRepository, app.signInManager, app.sessionManager)
                }
            }
    }
}
