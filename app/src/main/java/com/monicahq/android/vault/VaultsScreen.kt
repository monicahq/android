package com.monicahq.android.vault

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monicahq.android.R
import com.monicahq.android.auth.AuthError
import com.monicahq.android.ui.theme.MonicaTheme
import com.monicahq.android.ui.theme.colours

/** Connects [VaultsScreen] to its view model; reloads the list each time it is shown again. */
@Composable
fun VaultsRoute(
    accountInitials: String?,
    onAccountClick: () -> Unit,
    onVaultClick: (String) -> Unit,
    onNewVault: (hasVaults: Boolean) -> Unit,
    viewModel: VaultsViewModel = viewModel(factory = VaultsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose {}
    }
    VaultsScreen(
        uiState = uiState,
        accountInitials = accountInitials,
        onAccountClick = onAccountClick,
        onVaultClick = onVaultClick,
        onNewVault = { onNewVault((uiState.content as? VaultsContent.Loaded)?.vaults?.isNotEmpty() == true) },
        onRefresh = viewModel::refresh,
        onTryAgain = viewModel::tryAgain,
        onRefreshErrorShown = viewModel::refreshErrorShown,
    )
}

/**
 * Where the person lands once signed in: every vault of the account, the empty state when there is
 * none, and a way to create one. Stateless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultsScreen(
    uiState: VaultsUiState,
    accountInitials: String?,
    onAccountClick: () -> Unit,
    onVaultClick: (String) -> Unit,
    onNewVault: () -> Unit,
    onRefresh: () -> Unit,
    onTryAgain: () -> Unit,
    onRefreshErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailed = stringResource(R.string.vaults_refresh_failed)
    LaunchedEffect(uiState.refreshError) {
        if (uiState.refreshError != null) {
            snackbarHostState.showSnackbar(refreshFailed, withDismissAction = true, duration = SnackbarDuration.Long)
            onRefreshErrorShown()
        }
    }
    val listState = rememberLazyListState()
    val title = stringResource(R.string.vaults_title)
    val vaults = (uiState.content as? VaultsContent.Loaded)?.vaults
    Scaffold(
        // Announced when the app arrives here after signing in.
        modifier = modifier.fillMaxSize().semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopBar(
                initials = accountInitials,
                onAccountClick = onAccountClick,
                // The line appears once cards pass under the bar, as in the design.
                showLine = vaults != null && listState.canScrollBackward,
            )
        },
        floatingActionButton = {
            if (!vaults.isNullOrEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onNewVault,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.vaults_new), modifier = Modifier.padding(start = 10.dp))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val content = Modifier.padding(padding).fillMaxSize()
        when (val state = uiState.content) {
            VaultsContent.Loading -> {
                Loading(content)
            }

            is VaultsContent.Failed -> {
                Failed(state.error, onTryAgain, content)
            }

            is VaultsContent.Loaded if state.vaults.isEmpty() -> {
                Empty(onNewVault, content)
            }

            is VaultsContent.Loaded -> {
                val refreshLabel = stringResource(R.string.vaults_refresh)
                PullToRefreshBox(isRefreshing = uiState.refreshing, onRefresh = onRefresh, modifier = content) {
                    LazyColumn(
                        state = listState,
                        // The pull gesture has an alternative in TalkBack's and Switch Access's
                        // actions, on the list and on each card (TalkBack focuses the cards).
                        modifier =
                            Modifier.fillMaxSize().semantics {
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction(refreshLabel) {
                                            onRefresh()
                                            true
                                        },
                                    )
                            },
                        // The bottom space keeps the floating button from hiding the last card.
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { Title(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) }
                        items(state.vaults, key = { it.id }) { vault ->
                            VaultCard(vault, onClick = { onVaultClick(vault.id) }, onRefresh = onRefresh)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    initials: String?,
    onAccountClick: () -> Unit,
    showLine: Boolean,
) {
    Column(Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp)) {
            AccountButton(initials, onAccountClick, Modifier.align(Alignment.CenterStart))
            Image(
                painter = painterResource(R.drawable.ic_logo),
                // Decorative: the screen's title says where the person is.
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(width = 30.dp, height = 27.dp),
            )
        }
        HorizontalDivider(color = if (showLine) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.background)
    }
}

/** The person's initials in a circle, or a person icon when their name has no letters. */
@Composable
private fun AccountButton(
    initials: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.account_button)
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    // Grows into a pill rather than cutting the letters at the largest font size.
                    .sizeIn(minWidth = 36.dp, minHeight = 36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(horizontal = 6.dp)
                    // The button's name says what it is; the letters would only repeat it.
                    .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (initials != null) {
                Text(initials, fontSize = 13.sp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            } else {
                Icon(painterResource(R.drawable.ic_person), contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Title(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.vaults_title),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * One vault: its square, name, contacts and, for the default vault, a badge. One control for
 * TalkBack, read as "Personal, 25 contacts, Default".
 */
@Composable
private fun VaultCard(
    vault: Vault,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val refreshLabel = stringResource(R.string.vaults_refresh)
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier =
            Modifier.fillMaxWidth().semantics {
                role = Role.Button
                customActions =
                    listOf(
                        CustomAccessibilityAction(refreshLabel) {
                            onRefresh()
                            true
                        },
                    )
            },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            VaultSquare(vault, Modifier.size(44.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(vault.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text =
                        if (vault.contactCount == 0) {
                            stringResource(R.string.vault_no_contacts)
                        } else {
                            pluralStringResource(R.plurals.vault_contacts, vault.contactCount, vault.contactCount)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (vault.isDefault) DefaultBadge()
        }
    }
}

/** The vault's initial on its tinted square. Hidden from TalkBack: the name says it. */
@Composable
internal fun VaultSquare(
    vault: Vault,
    modifier: Modifier = Modifier,
) {
    val colours = vault.colour.colours()
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(colours.tint).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(vaultInitial(vault.name), color = colours.initial, fontSize = 16.sp, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DefaultBadge() {
    // "Default" alone could be heard as "default contacts".
    val description = stringResource(R.string.vault_default_description)
    Box(
        modifier =
            Modifier
                .heightIn(min = 24.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .padding(horizontal = 10.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.vault_default),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.vaults_loading)
    Column(modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
        Title()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.semantics { contentDescription = label })
        }
    }
}

@Composable
private fun Failed(
    error: AuthError,
    onTryAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Title()
        Text(
            text = stringResource(error.message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            // Said when it replaces the loading indicator.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        OutlinedButton(
            onClick = onTryAgain,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.vaults_try_again))
        }
    }
}

@Composable
private fun Empty(
    onNewVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp)) {
        Title()
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_vault),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.vaults_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.vaults_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Button(onClick = onNewVault, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(painterResource(R.drawable.ic_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.vaults_new), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

private val previewVaults =
    listOf(
        Vault("1", "Personal", null, VaultColour.Blue, isDefault = true, contactCount = 25),
        Vault("2", "Family", null, VaultColour.Green, isDefault = false, contactCount = 12),
        Vault("3", "Work", null, VaultColour.Ochre, isDefault = false, contactCount = 1),
    )

@Preview(name = "Light", showSystemUi = true)
@Preview(name = "Dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VaultsScreenPreview() {
    MonicaTheme {
        VaultsScreen(VaultsUiState(VaultsContent.Loaded(previewVaults)), "JD", {}, {}, {}, {}, {}, {})
    }
}

@Preview(name = "Empty, light", showSystemUi = true)
@Preview(name = "Empty, dark", showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VaultsScreenEmptyPreview() {
    MonicaTheme {
        VaultsScreen(VaultsUiState(VaultsContent.Loaded(emptyList())), "JD", {}, {}, {}, {}, {}, {})
    }
}
