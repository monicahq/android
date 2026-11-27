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

/** The vault creation form. Errors in `String` are the instance's own words. */
data class NewVaultUiState(
    val name: String = "",
    val description: String = "",
    val colour: VaultColour = VaultColour.Blue,
    val makeDefault: Boolean = false,
    /** Only when the account already has a vault: the first one is always the default. */
    val offerDefault: Boolean = false,
    val saving: Boolean = false,
    val nameError: String? = null,
    val descriptionError: String? = null,
    /** A refusal about anything but the name or the description, e.g. the colour. */
    val generalError: String? = null,
    val connectionError: AuthError? = null,
    val confirmDiscard: Boolean = false,
    /** The vault exists: the screen goes back to the list. */
    val created: Boolean = false,
    /** Leaving was confirmed, or nothing was typed: the screen closes. */
    val closed: Boolean = false,
) {
    val canCreate: Boolean get() = name.isNotBlank() && !saving
    val hasInput: Boolean get() = name.isNotBlank() || description.isNotBlank()
}

/**
 * Creates a vault with a name, a description and a colour, as the web form does (without the
 * template choice: the instance starts it from the account's default template). The instance
 * decides what it accepts, including the free plan's ceiling, and says why it refuses.
 */
class NewVaultViewModel(
    offerDefault: Boolean,
    private val repository: VaultRepository,
) : ViewModel() {
    private val state = MutableStateFlow(NewVaultUiState(offerDefault = offerDefault))
    val uiState: StateFlow<NewVaultUiState> = state.asStateFlow()

    fun onNameChange(name: String) {
        state.update { it.copy(name = name.take(MAX_NAME_LENGTH), nameError = null) }
    }

    fun onDescriptionChange(description: String) {
        state.update { it.copy(description = description, descriptionError = null) }
    }

    fun onColourChange(colour: VaultColour) {
        state.update { it.copy(colour = colour, generalError = null) }
    }

    fun onMakeDefaultChange(makeDefault: Boolean) {
        state.update { it.copy(makeDefault = makeDefault) }
    }

    fun create() {
        val form = state.value
        if (!form.canCreate) return
        state.update { it.copy(saving = true, nameError = null, descriptionError = null, generalError = null, connectionError = null) }
        viewModelScope.launch {
            val result =
                repository.create(
                    NewVault(
                        name = form.name,
                        description = form.description,
                        colour = form.colour,
                        makeDefault = form.offerDefault && form.makeDefault,
                    ),
                )
            state.update { it.after(result) }
        }
    }

    private fun NewVaultUiState.after(result: ApiResult<Vault>): NewVaultUiState =
        when (result) {
            is ApiResult.Success -> {
                copy(saving = false, created = true)
            }

            is ApiResult.Invalid -> {
                val general =
                    result.fields
                        .filterKeys { it != "name" && it != "description" }
                        .values
                        .firstOrNull()
                copy(
                    saving = false,
                    nameError = result.fields["name"],
                    descriptionError = result.fields["description"],
                    // A refusal naming no field of the form is shown as the instance put it.
                    generalError = general ?: result.message.takeIf { result.fields.isEmpty() },
                )
            }

            is ApiResult.Failed -> {
                copy(saving = false, connectionError = result.error)
            }

            ApiResult.NotFound -> {
                copy(saving = false, connectionError = AuthError.ServerError)
            }

            // The app is leaving the signed-in screens.
            ApiResult.SessionEnded, ApiResult.Discarded -> {
                copy(saving = false)
            }
        }

    fun dismissConnectionError() {
        state.update { it.copy(connectionError = null) }
    }

    /** Back, or Cancel: asks first when something was typed. */
    fun leave() {
        state.update { if (it.hasInput) it.copy(confirmDiscard = true) else it.copy(closed = true) }
    }

    fun confirmDiscard() {
        state.update { it.copy(confirmDiscard = false, closed = true) }
    }

    fun dismissDiscard() {
        state.update { it.copy(confirmDiscard = false) }
    }

    companion object {
        /** The instance's limit for a vault name. */
        const val MAX_NAME_LENGTH = 255

        fun factory(offerDefault: Boolean) =
            viewModelFactory {
                initializer { NewVaultViewModel(offerDefault, (this[APPLICATION_KEY] as MonicaApplication).vaultRepository) }
            }
    }
}
