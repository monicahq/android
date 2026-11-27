package com.monicahq.android.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.monicahq.android.R
import com.monicahq.android.instance.instanceHost

/** Connects [InstanceSheet] to its [InstanceSheetViewModel]. */
@Composable
fun InstanceSheetRoute(
    state: InstanceSheetState,
    viewModel: InstanceSheetViewModel,
) {
    InstanceSheet(
        state = state,
        onDraftChange = viewModel::onDraftChange,
        onReset = viewModel::resetDraft,
        onDismiss = viewModel::dismiss,
        onSave = viewModel::save,
        onConfirmSwitch = viewModel::confirmSwitch,
        onCancelSwitch = viewModel::cancelSwitch,
    )
}

/**
 * The sheet where a person chooses the address of their Monica instance. The address is checked
 * before it is saved, and leaving the instance the person is signed in to asks first. Stateless:
 * everything comes from [state], every action goes back through the callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InstanceSheet(
    state: InstanceSheetState,
    onDraftChange: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onConfirmSwitch: () -> Unit,
    onCancelSwitch: () -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    // Moves focus to the field, so the error is read and the address can be corrected at once.
    LaunchedEffect(state.error, state.failedAttempts) {
        if (state.error != null) {
            // The sheet's window must be attached and laid out before the field can take focus.
            withFrameNanos {}
            fieldFocus.requestFocus()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The card colour sets the sheet apart from the page, which matters in dark theme.
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val title = stringResource(R.string.instance_sheet_title)
        val errorMessage = state.error?.let { stringResource(it.message) }
        // Scrolls so that nothing is cut with the largest font or the keyboard open.
        Column(
            modifier =
                Modifier
                    .semantics { paneTitle = title }
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.instance_sheet_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                label = { Text(stringResource(R.string.instance_sheet_address)) },
                placeholder = { Text(stringResource(R.string.instance_sheet_placeholder)) },
                isError = errorMessage != null,
                readOnly = state.isChecking,
                // Not a live region: focus moves to the field, whose error semantics read it.
                supportingText = errorMessage?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                shape = RoundedCornerShape(6.dp),
                // The design's hairline border is too faint for a control (3:1 needed). The focused
                // label is text (4.5:1 needed), which the dark theme's blue primary is not on a card.
                colors =
                    OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedLabelColor = MaterialTheme.colorScheme.onBackground,
                    ),
                // Screen readers read this message instead of Material's generic "Invalid input".
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .semantics { if (errorMessage != null) error(errorMessage) },
            )
            if (state.isChecking) {
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.instance_sheet_checking, instanceHost(state.draft)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            // Wraps onto two lines when the labels do not fit side by side (large font, long
            // translation). Buttons are 48 dp high rather than the design's 40 dp: touch targets.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val textColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = onReset, enabled = !state.isChecking, colors = textColors, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.instance_sheet_reset))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, colors = textColors, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.instance_sheet_cancel))
                    }
                    Button(onClick = onSave, enabled = !state.isChecking, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.instance_sheet_save))
                    }
                }
            }
        }
    }
    state.switchFrom?.let { current ->
        AlertDialog(
            onDismissRequest = onCancelSwitch,
            title = { Text(stringResource(R.string.instance_switch_title), modifier = Modifier.semantics { heading() }) },
            text = { Text(stringResource(R.string.instance_switch_message, current)) },
            confirmButton = {
                TextButton(onClick = onConfirmSwitch, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.instance_switch_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelSwitch, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.instance_sheet_cancel))
                }
            },
        )
    }
}
