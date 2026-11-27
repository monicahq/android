package com.monicahq.android.vault

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monicahq.android.R
import com.monicahq.android.ui.StaleNotice
import com.monicahq.android.ui.theme.MonicaTheme

/** Connects [VaultScreen] to its view model. */
@Composable
fun VaultRoute(
    vaultId: String,
    onBack: () -> Unit,
    viewModel: VaultViewModel = viewModel(factory = VaultViewModel.factory(vaultId)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    VaultScreen(uiState = uiState, onBack = onBack, onTryAgain = viewModel::tryAgain)
}

/**
 * One vault: its square, name and description. A placeholder until the vault screen of the design
 * arrives; it proves the way from the list and back. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    uiState: VaultUiState,
    onBack: () -> Unit,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Until the vault is known, the screen is still named for TalkBack.
    val name = (uiState as? VaultUiState.Loaded)?.vault?.name ?: stringResource(R.string.vault_title)
    val loading = stringResource(R.string.vault_loading)
    Scaffold(
        modifier = modifier.fillMaxSize().semantics { paneTitle = name },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when (uiState) {
            VaultUiState.Loading -> {
                Box(content, contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.semantics { contentDescription = loading })
                }
            }

            is VaultUiState.Loaded -> {
                Column(
                    modifier = content.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    VaultSquare(uiState.vault, Modifier.size(56.dp))
                    Text(
                        text = uiState.vault.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    StaleNotice(uiState.staleSince)
                    uiState.vault.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            VaultUiState.NotFound -> {
                Message(stringResource(R.string.vault_not_found), stringResource(R.string.vault_back_to_list), onBack, content)
            }

            is VaultUiState.Failed -> {
                Message(stringResource(uiState.error.message), stringResource(R.string.vaults_try_again), onTryAgain, content)
            }
        }
    }
}

@Composable
private fun Message(
    text: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        OutlinedButton(
            onClick = onAction,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(action)
        }
    }
}

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VaultScreenPreview() {
    MonicaTheme {
        VaultScreen(
            uiState = VaultUiState.Loaded(Vault("1", "Family", "Everybody I am related to.", VaultColour.Green, false, 12)),
            onBack = {},
            onTryAgain = {},
        )
    }
}
