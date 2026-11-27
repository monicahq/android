package com.monicahq.android.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * The sheet where a person chooses the address of their Monica instance. Stateless: the draft and
 * the error come from [state], every action goes back through the callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InstanceSheet(
    state: InstanceSheetState,
    onDraftChange: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The card colour sets the sheet apart from the page, which matters in dark theme.
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        val title = stringResource(R.string.instance_sheet_title)
        val invalidMessage = stringResource(R.string.instance_sheet_invalid)
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
                isError = state.isError,
                supportingText =
                    if (state.isError) {
                        // Announced as soon as it appears: focus is still on "Save".
                        { Text(invalidMessage, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    } else {
                        null
                    },
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
                        .semantics { if (state.isError) error(invalidMessage) },
            )
            // Wraps onto two lines when the labels do not fit side by side (large font, long
            // translation). Buttons are 48 dp high rather than the design's 40 dp: touch targets.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val textColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
                TextButton(onClick = onReset, colors = textColors, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.instance_sheet_reset))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, colors = textColors, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.instance_sheet_cancel))
                    }
                    Button(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.instance_sheet_save))
                    }
                }
            }
        }
    }
}
