package com.monicahq.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.monicahq.android.account.AccountSheet
import com.monicahq.android.account.AccountViewModel
import com.monicahq.android.account.SignOutDialog
import com.monicahq.android.vault.NewVaultRoute
import com.monicahq.android.vault.VaultRoute
import com.monicahq.android.vault.VaultsRoute
import com.monicahq.android.welcome.InstanceSheetRoute
import com.monicahq.android.welcome.InstanceSheetViewModel
import kotlinx.serialization.Serializable

/** The vault list, where the person lands once signed in. */
@Serializable
data object VaultsDestination

/** One vault, by its id. */
@Serializable
data class VaultDestination(
    val id: String,
)

/** The vault creation form; the default option is offered only when the account has vaults. */
@Serializable
data class NewVaultDestination(
    val hasVaults: Boolean,
)

/**
 * Everything shown while signed in. It is dropped when the session ends: the next sign-in starts
 * from a new back stack with new screens, and the account's view model forgets its open sheet or
 * dialog (see [AccountViewModel]).
 */
@Composable
fun SignedInApp(
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
    instanceSheetViewModel: InstanceSheetViewModel = viewModel(factory = InstanceSheetViewModel.Factory),
) {
    val account by accountViewModel.uiState.collectAsStateWithLifecycle()
    val instanceSheet by instanceSheetViewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = VaultsDestination) {
        composable<VaultsDestination> { entry ->
            VaultsRoute(
                accountInitials = account.initials,
                onAccountClick = accountViewModel::openSheet,
                onVaultClick = { id -> entry.ifOnTop { navController.navigate(VaultDestination(id)) } },
                onNewVault = { hasVaults -> entry.ifOnTop { navController.navigate(NewVaultDestination(hasVaults)) } },
            )
        }
        composable<VaultDestination> { entry ->
            VaultRoute(vaultId = entry.toRoute<VaultDestination>().id, onBack = { entry.ifOnTop { navController.popBackStack() } })
        }
        composable<NewVaultDestination> { entry ->
            NewVaultRoute(
                offerDefault = entry.toRoute<NewVaultDestination>().hasVaults,
                onDone = { entry.ifOnTop { navController.popBackStack() } },
            )
        }
    }
    if (account.sheetOpen) {
        AccountSheet(
            uiState = account,
            onInstanceSettings = {
                accountViewModel.openInstanceSettings()
                instanceSheetViewModel.open()
            },
            onSignOut = accountViewModel::askSignOut,
            onDismiss = accountViewModel::closeSheet,
        )
    }
    if (account.confirmSignOut) {
        SignOutDialog(onConfirm = accountViewModel::confirmSignOut, onDismiss = accountViewModel::dismissSignOut)
    }
    instanceSheet?.let { InstanceSheetRoute(state = it, viewModel = instanceSheetViewModel) }
}

/**
 * Runs [action] only while this screen is the one on top and settled. A second tap while it
 * animates away would otherwise open a screen twice, or go back twice and leave an empty screen.
 */
private fun NavBackStackEntry.ifOnTop(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
