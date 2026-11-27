package com.monicahq.android.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.monicahq.android.MonicaApplication
import com.monicahq.android.auth.AuthError
import com.monicahq.android.auth.SessionManager
import com.monicahq.android.auth.SessionState
import com.monicahq.android.auth.SignInManager
import com.monicahq.android.instance.CheckResult
import com.monicahq.android.instance.DEFAULT_INSTANCE_URL
import com.monicahq.android.instance.InstanceChecker
import com.monicahq.android.instance.InstanceRepository
import com.monicahq.android.instance.instanceHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the instance sheet shows while it is open. [switchFrom] is set while the person confirms
 * leaving the instance they are signed in to, whose address it holds.
 */
data class InstanceSheetState(
    val draft: String,
    val isChecking: Boolean = false,
    val error: AuthError? = null,
    val switchFrom: String? = null,
    /** Counts failed checks, so the same error after a second attempt is still announced. */
    val failedAttempts: Int = 0,
)

/**
 * The instance sheet: an address is checked against the instance before it is saved, and leaving
 * the instance the person is signed in to signs them out first.
 */
class InstanceSheetViewModel(
    private val instanceRepository: InstanceRepository,
    private val checker: InstanceChecker,
    private val sessionManager: SessionManager,
    private val signInManager: SignInManager,
) : ViewModel() {
    private val sheet = MutableStateFlow<InstanceSheetState?>(null)

    /** Null while the sheet is closed. */
    val uiState: StateFlow<InstanceSheetState?> = sheet.asStateFlow()

    /** The address that passed the check, waiting for the person to confirm the switch. */
    private var checkedUrl: String? = null

    fun open() {
        viewModelScope.launch { sheet.value = InstanceSheetState(draft = instanceRepository.url.first()) }
    }

    fun onDraftChange(draft: String) {
        sheet.update { it?.takeUnless { it.isChecking }?.copy(draft = draft, error = null) ?: it }
    }

    /** Puts the official instance in the field. Nothing is saved until [save]. */
    fun resetDraft() {
        sheet.update { it?.takeUnless { it.isChecking }?.copy(draft = DEFAULT_INSTANCE_URL, error = null) ?: it }
    }

    fun dismiss() {
        sheet.value = null
    }

    /** Checks the draft, then saves it, or asks first when it signs the person out. */
    fun save() {
        val current = sheet.value?.takeUnless { it.isChecking || it.switchFrom != null } ?: return
        sheet.value = current.copy(isChecking = true, error = null)
        viewModelScope.launch {
            when (val result = checker.check(current.draft)) {
                is CheckResult.Failed -> {
                    sheet.update { it?.copy(isChecking = false, error = result.error, failedAttempts = it.failedAttempts + 1) }
                }

                is CheckResult.Compatible -> {
                    // The person closed the sheet while the instance was being checked.
                    if (sheet.value == null) return@launch
                    val saved = instanceRepository.url.first()
                    val session = sessionManager.state.value
                    when {
                        result.url == saved -> {
                            sheet.value = null
                        }

                        session is SessionState.SignedIn -> {
                            checkedUrl = result.url
                            sheet.update { it?.copy(isChecking = false, switchFrom = instanceHost(session.instanceUrl)) }
                        }

                        else -> {
                            switchTo(result.url)
                        }
                    }
                }
            }
        }
    }

    fun confirmSwitch() {
        val url = checkedUrl ?: return
        checkedUrl = null
        viewModelScope.launch {
            sessionManager.logOut()
            switchTo(url)
        }
    }

    fun cancelSwitch() {
        checkedUrl = null
        sheet.update { it?.copy(switchFrom = null) }
    }

    /** A sign-in started on the previous instance must never complete on the new one. */
    private suspend fun switchTo(url: String) {
        signInManager.cancel()
        instanceRepository.setUrl(url)
        sheet.value = null
    }

    companion object {
        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as MonicaApplication
                    InstanceSheetViewModel(app.instanceRepository, app.instanceChecker, app.sessionManager, app.signInManager)
                }
            }
    }
}
