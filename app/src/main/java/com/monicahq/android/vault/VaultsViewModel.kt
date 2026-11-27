package com.monicahq.android.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monicahq.android.MonicaApplication
import com.monicahq.android.auth.AuthError
import com.monicahq.android.network.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the vault list shows. */
data class VaultsUiState(
    val content: VaultsContent = VaultsContent.Loading,
    /** A pull to refresh is in progress. */
    val refreshing: Boolean = false,
    /** A pull to refresh failed while a list was shown: said once, then cleared. */
    val refreshError: AuthError? = null,
)

sealed interface VaultsContent {
    data object Loading : VaultsContent

    /** An empty list is the empty state. */
    data class Loaded(
        val vaults: List<Vault>,
    ) : VaultsContent

    data class Failed(
        val error: AuthError,
    ) : VaultsContent
}

/**
 * The vault list. It is loaded each time the screen is shown again (after creating a vault, coming
 * back from one, or returning to the app), quietly: the list stays while it reloads, and a quiet
 * reload that fails changes nothing. Only a pull to refresh reports its failure.
 */
class VaultsViewModel(
    private val repository: VaultRepository,
) : ViewModel() {
    private val state = MutableStateFlow(VaultsUiState())
    val uiState: StateFlow<VaultsUiState> = state.asStateFlow()

    private var loading: Job? = null

    /** The screen is shown, for the first time or again. */
    fun onShown() {
        if (loading?.isActive == true) return
        load(pulled = false)
    }

    /** The person pulled the list down, or used the "Refresh" action. */
    fun refresh() {
        loading?.cancel()
        // Cleared, so that the same failure again is said again.
        state.update { it.copy(refreshing = true, refreshError = null) }
        load(pulled = true)
    }

    fun tryAgain() {
        loading?.cancel()
        state.update { it.copy(content = VaultsContent.Loading) }
        load(pulled = false)
    }

    fun refreshErrorShown() {
        state.update { it.copy(refreshError = null) }
    }

    private fun load(pulled: Boolean) {
        loading =
            viewModelScope.launch {
                val result = repository.vaults()
                state.update { current -> current.after(result, pulled) }
            }
    }

    private fun VaultsUiState.after(
        result: ApiResult<List<Vault>>,
        pulled: Boolean,
    ): VaultsUiState {
        val error =
            when (result) {
                is ApiResult.Success -> return copy(content = VaultsContent.Loaded(result.value), refreshing = false)

                // The app is leaving the signed-in screens: nothing to show.
                ApiResult.SessionEnded, ApiResult.Discarded -> return copy(refreshing = false)

                is ApiResult.Failed -> result.error

                ApiResult.NotFound, is ApiResult.Invalid -> AuthError.ServerError
            }
        return when {
            content !is VaultsContent.Loaded -> copy(content = VaultsContent.Failed(error), refreshing = false)
            pulled -> copy(refreshing = false, refreshError = error)
            else -> this
        }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer { VaultsViewModel((this[APPLICATION_KEY] as MonicaApplication).vaultRepository) }
            }
    }
}
