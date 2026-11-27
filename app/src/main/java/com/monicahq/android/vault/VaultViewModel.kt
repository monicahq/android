package com.monicahq.android.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monicahq.android.MonicaApplication
import com.monicahq.android.auth.AuthError
import com.monicahq.android.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VaultUiState {
    data object Loading : VaultUiState

    data class Loaded(
        val vault: Vault,
    ) : VaultUiState

    /** Deleted on the web since the list was loaded, or never there. */
    data object NotFound : VaultUiState

    data class Failed(
        val error: AuthError,
    ) : VaultUiState
}

/** One vault. A placeholder for the vault screen, which a later feature fills. */
class VaultViewModel(
    private val vaultId: String,
    private val repository: VaultRepository,
) : ViewModel() {
    private val state = MutableStateFlow<VaultUiState>(VaultUiState.Loading)
    val uiState: StateFlow<VaultUiState> = state.asStateFlow()

    init {
        load()
    }

    fun tryAgain() {
        state.update { VaultUiState.Loading }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = repository.vault(vaultId)) {
                is ApiResult.Success -> state.update { VaultUiState.Loaded(result.value) }

                ApiResult.NotFound -> state.update { VaultUiState.NotFound }

                is ApiResult.Failed -> state.update { VaultUiState.Failed(result.error) }

                is ApiResult.Invalid -> state.update { VaultUiState.Failed(AuthError.ServerError) }

                // The app is leaving the signed-in screens.
                ApiResult.SessionEnded, ApiResult.Discarded -> Unit
            }
        }
    }

    companion object {
        fun factory(vaultId: String) =
            viewModelFactory {
                initializer { VaultViewModel(vaultId, (this[APPLICATION_KEY] as MonicaApplication).vaultRepository) }
            }
    }
}
