package com.monicahq.android.home

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monicahq.android.R
import com.monicahq.android.instance.instanceHost
import com.monicahq.android.ui.theme.MonicaTheme
import com.monicahq.android.welcome.InstanceSheetRoute
import com.monicahq.android.welcome.InstanceSheetViewModel

/** Connects [HomeScreen] to its view models. */
@Composable
fun HomeRoute(
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    sheetViewModel: InstanceSheetViewModel = viewModel(factory = InstanceSheetViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheet by sheetViewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        onChangeInstance = sheetViewModel::open,
        onSignOut = viewModel::askSignOut,
        onConfirmSignOut = viewModel::confirmSignOut,
        onDismissSignOut = viewModel::dismissSignOut,
        onErrorShown = viewModel::dismissError,
    )
    sheet?.let { InstanceSheetRoute(state = it, viewModel = sheetViewModel) }
}

/**
 * The signed-in home: who is signed in and to which instance, a placeholder until the first real
 * screens. Stateless.
 */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onChangeInstance: () -> Unit,
    onSignOut: () -> Unit,
    onConfirmSignOut: () -> Unit,
    onDismissSignOut: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.error?.let { stringResource(it.message) }
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // Stays until dismissed, so the message can be read again.
            snackbarHostState.showSnackbar(errorMessage, withDismissAction = true, duration = SnackbarDuration.Indefinite)
            onErrorShown()
        }
    }
    val title = stringResource(R.string.home_title)
    Scaffold(
        // Announced when the app switches to this screen after signing in.
        modifier = modifier.fillMaxSize().semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.home_greeting, uiState.name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.home_connected, instanceHost(uiState.instanceUrl)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Borders in onSurfaceVariant: Material's default is too faint for a control (3:1 needed).
            val border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onChangeInstance, border = border, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.welcome_instance_settings))
            }
            OutlinedButton(onClick = onSignOut, border = border, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.home_sign_out))
            }
        }
    }
    if (uiState.confirmSignOut) {
        AlertDialog(
            onDismissRequest = onDismissSignOut,
            title = { Text(stringResource(R.string.sign_out_title), modifier = Modifier.semantics { heading() }) },
            text = { Text(stringResource(R.string.sign_out_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmSignOut, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.home_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSignOut, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sign_out_cancel))
                }
            },
        )
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenPreview() {
    MonicaTheme {
        HomeScreen(
            uiState = HomeUiState(name = "Jane Doe", instanceUrl = "https://app.monicahq.com"),
            onChangeInstance = {},
            onSignOut = {},
            onConfirmSignOut = {},
            onDismissSignOut = {},
            onErrorShown = {},
        )
    }
}
