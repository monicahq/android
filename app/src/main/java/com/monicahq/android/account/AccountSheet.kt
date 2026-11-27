package com.monicahq.android.account

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.monicahq.android.R
import com.monicahq.android.instance.instanceHost
import com.monicahq.android.ui.theme.MonicaTheme

/**
 * Who is signed in and to which instance, with the instance settings and signing out. Opened from
 * the account button of the vault list. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    uiState: AccountUiState,
    onInstanceSettings: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // The card colour sets the sheet apart from the page, which matters in dark theme.
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        AccountContent(uiState, onInstanceSettings, onSignOut)
    }
}

/** The sheet's content, apart so it can be previewed and tested without a sheet window. */
@Composable
internal fun AccountContent(
    uiState: AccountUiState,
    onInstanceSettings: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.account_button)
    // Scrolls so that nothing is cut with the largest font.
    Column(
        modifier =
            modifier
                .semantics { paneTitle = title }
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = uiState.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.account_connected, instanceHost(uiState.instanceUrl)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Borders in onSurfaceVariant: Material's default is too faint for a control (3:1 needed).
        val border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onInstanceSettings, border = border, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.welcome_instance_settings))
        }
        OutlinedButton(onClick = onSignOut, border = border, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.account_sign_out))
        }
    }
}

/** Signing out asks first: the person would have to log in again. */
@Composable
fun SignOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sign_out_title), modifier = Modifier.semantics { heading() }) },
        text = { Text(stringResource(R.string.sign_out_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.account_sign_out))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.sign_out_cancel))
            }
        },
    )
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AccountContentPreview() {
    MonicaTheme {
        AccountContent(
            uiState = AccountUiState(name = "Jane Doe", initials = "JD", instanceUrl = "https://app.monicahq.com"),
            onInstanceSettings = {},
            onSignOut = {},
        )
    }
}
