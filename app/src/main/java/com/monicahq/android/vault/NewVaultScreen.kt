package com.monicahq.android.vault

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monicahq.android.R
import com.monicahq.android.ui.theme.MonicaTheme
import com.monicahq.android.ui.theme.colours

/** Connects [NewVaultScreen] to its view model; [onDone] goes back to the list. */
@Composable
fun NewVaultRoute(
    offerDefault: Boolean,
    onDone: () -> Unit,
    viewModel: NewVaultViewModel = viewModel(factory = NewVaultViewModel.factory(offerDefault)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(uiState.created, uiState.closed) {
        if (uiState.created || uiState.closed) onDone()
    }
    BackHandler(enabled = !uiState.created && !uiState.closed) { viewModel.leave() }
    NewVaultScreen(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onColourChange = viewModel::onColourChange,
        onMakeDefaultChange = viewModel::onMakeDefaultChange,
        onCreate = viewModel::create,
        onLeave = viewModel::leave,
        onConfirmDiscard = viewModel::confirmDiscard,
        onDismissDiscard = viewModel::dismissDiscard,
        onConnectionErrorShown = viewModel::dismissConnectionError,
    )
}

/** The form that creates a vault: a name, an optional description and a colour. Stateless. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewVaultScreen(
    uiState: NewVaultUiState,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onColourChange: (VaultColour) -> Unit,
    onMakeDefaultChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onLeave: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConnectionErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val connectionMessage = uiState.connectionError?.let { stringResource(it.message) }
    LaunchedEffect(connectionMessage) {
        if (connectionMessage != null) {
            snackbarHostState.showSnackbar(connectionMessage, withDismissAction = true, duration = SnackbarDuration.Long)
            onConnectionErrorShown()
        }
    }
    val nameFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    // A refusal moves focus to the field at fault, so TalkBack reads it with the instance's words.
    LaunchedEffect(uiState.nameError, uiState.descriptionError) {
        // The field must be laid out before it can take focus.
        withFrameNanos {}
        when {
            uiState.nameError != null -> nameFocus.requestFocus()
            uiState.descriptionError != null -> descriptionFocus.requestFocus()
        }
    }
    val title = stringResource(R.string.new_vault_title)
    Scaffold(
        modifier = modifier.fillMaxSize().semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onLeave) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.new_vault_cancel))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        // Above the keyboard, which is usually still open after tapping "Create vault".
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.imePadding()) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // The design's hairline is too faint for a field (3:1 needed); the focused label is
            // text, which the dark theme's blue primary is not on the page.
            val fieldColors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                )
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.new_vault_name)) },
                isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { { Text(it) } },
                singleLine = true,
                readOnly = uiState.saving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(6.dp),
                colors = fieldColors,
                modifier =
                    Modifier.fillMaxWidth().focusRequester(nameFocus).semantics {
                        uiState.nameError?.let { error(it) }
                    },
            )
            OutlinedTextField(
                value = uiState.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.new_vault_description)) },
                isError = uiState.descriptionError != null,
                supportingText = uiState.descriptionError?.let { { Text(it) } },
                minLines = 3,
                readOnly = uiState.saving,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(6.dp),
                colors = fieldColors,
                modifier =
                    Modifier.fillMaxWidth().focusRequester(descriptionFocus).semantics {
                        uiState.descriptionError?.let { error(it) }
                    },
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.new_vault_colour),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    VaultColour.entries.forEach { colour ->
                        Swatch(colour, selected = colour == uiState.colour, onClick = { onColourChange(colour) })
                    }
                }
            }
            if (uiState.offerDefault) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = uiState.makeDefault, role = Role.Checkbox, onValueChange = onMakeDefaultChange),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = uiState.makeDefault, onCheckedChange = null)
                    Text(
                        text = stringResource(R.string.new_vault_make_default),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            uiState.generalError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(onClick = onCreate, enabled = uiState.canCreate, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (uiState.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.new_vault_creating), modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text(stringResource(R.string.new_vault_create))
                }
            }
        }
    }
    if (uiState.confirmDiscard) {
        AlertDialog(
            onDismissRequest = onDismissDiscard,
            title = { Text(stringResource(R.string.new_vault_discard_title), modifier = Modifier.semantics { heading() }) },
            text = { Text(stringResource(R.string.new_vault_discard_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmDiscard, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.new_vault_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDiscard, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.new_vault_keep_editing))
                }
            },
        )
    }
}

/** One colour to choose: named for TalkBack, and the chosen one carries a check, not only a ring. */
@Composable
private fun Swatch(
    colour: VaultColour,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colours = colour.colours()
    val label = stringResource(colour.label)
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, shape) else Modifier)
                    .padding(if (selected) 4.dp else 0.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colours.initial),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colours.tint, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewVaultScreenPreview() {
    MonicaTheme {
        NewVaultScreen(
            uiState = NewVaultUiState(name = "Family", colour = VaultColour.Green, offerDefault = true),
            onNameChange = {},
            onDescriptionChange = {},
            onColourChange = {},
            onMakeDefaultChange = {},
            onCreate = {},
            onLeave = {},
            onConfirmDiscard = {},
            onDismissDiscard = {},
            onConnectionErrorShown = {},
        )
    }
}
