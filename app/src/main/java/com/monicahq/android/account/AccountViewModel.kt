package com.monicahq.android.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monicahq.android.MonicaApplication
import com.monicahq.android.auth.SessionManager
import com.monicahq.android.auth.SessionState
import com.monicahq.android.vault.personInitials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Who is signed in, and where: the account button, the account sheet and signing out. */
data class AccountUiState(
    val name: String = "",
    /** For the account button; null when the name has no letters (a person icon is shown). */
    val initials: String? = null,
    val instanceUrl: String = "",
    val sheetOpen: Boolean = false,
    val confirmSignOut: Boolean = false,
)

private data class AccountLocal(
    val sheetOpen: Boolean = false,
    val confirmSignOut: Boolean = false,
)

/**
 * The signed-in person. Whether the session still holds is found out by the screens' own calls
 * (the vault list's first load): an ended session takes the app back to the welcome screen.
 */
class AccountViewModel(
    private val sessionManager: SessionManager,
) : ViewModel() {
    private val local = MutableStateFlow(AccountLocal())

    val uiState: StateFlow<AccountUiState> =
        combine(sessionManager.state, local, ::toUiState)
            // Starts from the session at hand, so the button never shows empty initials.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), toUiState(sessionManager.state.value, AccountLocal()))

    private fun toUiState(
        session: SessionState,
        local: AccountLocal,
    ): AccountUiState {
        val signedIn = session as? SessionState.SignedIn
        val name = signedIn?.user?.name.orEmpty()
        return AccountUiState(name, personInitials(name), signedIn?.instanceUrl.orEmpty(), local.sheetOpen, local.confirmSignOut)
    }

    fun openSheet() {
        local.update { it.copy(sheetOpen = true) }
    }

    fun closeSheet() {
        local.update { it.copy(sheetOpen = false) }
    }

    /** Closes the sheet: the instance sheet opens in its place. */
    fun openInstanceSettings() = closeSheet()

    /** Closes the sheet and asks for confirmation. */
    fun askSignOut() {
        local.update { it.copy(sheetOpen = false, confirmSignOut = true) }
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
                initializer { AccountViewModel((this[APPLICATION_KEY] as MonicaApplication).sessionManager) }
            }
    }
}
