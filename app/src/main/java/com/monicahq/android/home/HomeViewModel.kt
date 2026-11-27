package com.monicahq.android.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monicahq.android.MonicaApplication
import com.monicahq.android.auth.AuthError
import com.monicahq.android.auth.SessionManager
import com.monicahq.android.auth.SessionState
import com.monicahq.android.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The signed-in home, a placeholder until the first real screens arrive. */
data class HomeUiState(
    val name: String = "",
    val instanceUrl: String = "",
    val confirmSignOut: Boolean = false,
    val error: AuthError? = null,
)

private data class HomeLocal(
    val confirmSignOut: Boolean = false,
    val error: AuthError? = null,
)

class HomeViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val local = MutableStateFlow(HomeLocal())

    val uiState: StateFlow<HomeUiState> =
        combine(sessionManager.state, local, ::toUiState)
            // Starts from the session at hand, so the screen never shows an empty greeting.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(sessionManager.state.value, HomeLocal()))

    init {
        refresh()
    }

    private fun toUiState(
        session: SessionState,
        local: HomeLocal,
    ): HomeUiState {
        val signedIn = session as? SessionState.SignedIn
        return HomeUiState(signedIn?.user?.name.orEmpty(), signedIn?.instanceUrl.orEmpty(), local.confirmSignOut, local.error)
    }

    /** Talks to the instance once, so a session ended elsewhere, or a connection problem, shows up. */
    fun refresh() {
        viewModelScope.launch {
            val result = sessionManager.apiClient()?.me() ?: return@launch
            if (result is ApiResult.Failed) local.update { it.copy(error = result.error) }
        }
    }

    fun dismissError() {
        local.update { it.copy(error = null) }
    }

    fun askSignOut() {
        local.update { it.copy(confirmSignOut = true) }
    }

    fun dismissSignOut() {
        local.update { it.copy(confirmSignOut = false) }
    }

    fun confirmSignOut() {
        local.update { it.copy(confirmSignOut = false) }
        viewModelScope.launch { sessionManager.logOut() }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer { HomeViewModel((this[APPLICATION_KEY] as MonicaApplication).sessionManager) }
            }
    }
}
