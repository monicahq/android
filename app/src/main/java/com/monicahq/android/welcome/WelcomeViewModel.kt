package com.monicahq.android.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monicahq.android.MonicaApplication
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import com.monicahq.android.instance.InstanceRepository
import com.monicahq.android.instance.normalizeInstanceUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the instance sheet shows while it is open. */
data class InstanceSheetState(
    val draft: String,
    val isError: Boolean = false,
)

/** Everything the welcome screen shows. [sheet] is null when the instance sheet is closed. */
data class WelcomeUiState(
    val instanceUrl: String = DEFAULT_INSTANCE_URL,
    val sheet: InstanceSheetState? = null,
)

class WelcomeViewModel(
    private val instanceRepository: InstanceRepository,
) : ViewModel() {
    private val sheet = MutableStateFlow<InstanceSheetState?>(null)

    val uiState: StateFlow<WelcomeUiState> =
        combine(instanceRepository.url, sheet) { url, sheet -> WelcomeUiState(url, sheet) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WelcomeUiState())

    fun openInstanceSheet() {
        sheet.value = InstanceSheetState(draft = uiState.value.instanceUrl)
    }

    fun onDraftChange(draft: String) {
        sheet.update { it?.copy(draft = draft, isError = false) }
    }

    /** Puts the default address in the field. Nothing is saved until [saveInstance]. */
    fun resetDraft() {
        sheet.update { it?.copy(draft = DEFAULT_INSTANCE_URL, isError = false) }
    }

    fun dismissInstanceSheet() {
        sheet.value = null
    }

    /** Saves the draft and closes the sheet, or keeps it open with an error if it is not an address. */
    fun saveInstance() {
        val current = sheet.value ?: return
        val url = normalizeInstanceUrl(current.draft)
        if (url == null) {
            sheet.value = current.copy(isError = true)
            return
        }
        sheet.value = null
        viewModelScope.launch { instanceRepository.setUrl(url) }
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    WelcomeViewModel((this[APPLICATION_KEY] as MonicaApplication).instanceRepository)
                }
            }
    }
}
