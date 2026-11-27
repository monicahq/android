# Contract: Screens, semantics and strings

Design reference: `Vaults Android.dc.html` (Claude Design project `c63853f6-…`), frames "With
vaults" and "Empty state". Colours come from `MaterialTheme.colorScheme` and `VaultColours`, never
raw values. Every size in dp/sp scales with the system settings.

## Destinations (`SignedInApp.kt`)

| Route | Screen | Back |
|---|---|---|
| `VaultsDestination` (start) | `VaultsScreen` | leaves the app |
| `VaultDestination(id: String)` | `VaultScreen` | to the list |
| `NewVaultDestination(hasVaults: Boolean)` | `NewVaultScreen` | asks first when something is typed |

(`VaultsRoute`, `VaultRoute`, `NewVaultRoute` are the stateful entry composables of each screen.)

## VaultsScreen (stateless)

```text
VaultsScreen(
  uiState: VaultsUiState, accountInitials: String?,
  onVaultClick: (String) -> Unit, onNewVault: () -> Unit,
  onRefresh: () -> Unit, onTryAgain: () -> Unit, onRefreshErrorShown: () -> Unit,
  onAccountClick: () -> Unit,
)
```

Layout, from the design:
- **Top bar**, 64 dp: account button on the left (48 dp target, 36 dp circle with initials or the
  person icon, label "Your account"), Monica logo centred (decorative), nothing on the right. A 1 dp
  `outlineVariant` line under it once the list can scroll backward.
- **Title** "All your vaults" (`headlineMedium`-sized, heading semantics), in every state.
- **Card**, 8 dp corners, `outlineVariant` border, `background` fill, 12 dp between cards, 16 dp side
  margins: 44 dp square (8 dp corners) with the initial in the vault's colours; name
  (`titleMedium`, wraps); contact line ("12 contacts", `onSurfaceVariant`); "Default" pill (24 dp
  min height, outlined) on the default vault. The web's footer (open tasks, dates this month) and
  faces are not drawn (spec FR-011).
- **Extended FAB** "New vault" with the plus icon, bottom right, 16 dp from the edges, above the
  navigation bar. The list's bottom padding (88 dp + system bar) keeps the last card clear.
- **Empty**: centred vault icon in a 56 dp square (card colour, hairline), "No vault yet"
  (heading), the explanation, a filled "New vault" button (48 dp). No FAB.
- **Loading**: a centred `CircularProgressIndicator` with the label "Loading your vaults"; it is
  static when animations are off.
- **Failed**: the error's message (`AuthError.message`) and a "Try again" button.
- **Refresh failure**: a snackbar with "Your vaults could not be updated." (dismiss action).

Semantics:
- Each card: one merged, clickable node, role Button, read from its visible texts in order:
  "Personal, 25 contacts, Default" (no separate description to keep in sync). Clicking opens the
  vault. The initial square is hidden from accessibility (the name is read).
- The list carries the custom action "Refresh" (`onRefresh`).
- Screen `paneTitle` = "All your vaults", announced when the app arrives here after sign-in.

## VaultScreen (stateless, placeholder)

`VaultScreen(uiState: VaultUiState, onBack: () -> Unit, onTryAgain: () -> Unit)`

Top app bar with a back button ("Back") and the vault name as title; the square, the name as a
heading, the description when there is one. `NotFound`: "This vault no longer exists." with "Back to
your vaults". `Failed`: message + "Try again".

## NewVaultScreen (stateless)

```text
NewVaultScreen(
  uiState: NewVaultUiState,
  onNameChange, onDescriptionChange, onColourChange, onMakeDefaultChange,
  onCreate, onLeave, onConfirmDiscard, onDismissDiscard, onConnectionErrorShown,
)
```

- Top app bar: close button ("Cancel") and title "New vault" (heading).
- "Name" field (required, single line, IME action Next, error text under it), "Description" field
  (optional, multi-line), "Colour" group: eight 48 dp swatches in a wrapping row, selectable group
  semantics, each labelled with its colour name ("Blue", …) and `selected` state; the selected one
  also shows a check icon (not colour alone).
- "Make this the default vault" checkbox row (whole row toggles, 48 dp), only when
  `offerDefault`.
- "Create vault" filled button, disabled when `!canCreate`, showing a progress indicator and the
  label "Creating…" while saving. General error above it.
- Discard dialog: "Discard this vault?" / "What you typed will be lost." / "Discard" / "Keep editing".
- Connection error: snackbar with the `AuthError` message.

## AccountSheet (stateless)

`AccountSheet(uiState: AccountUiState, onInstanceSettings, onSignOut, onDismiss)` and
`SignOutDialog(onConfirm, onDismiss)`

Modal bottom sheet (pane title "Your account"): name (heading), "Connected to {host}",
"Instance settings", "Sign out". "Sign out" closes the sheet and opens the existing confirmation
dialog.

## Strings (English / French)

New keys; French reuses `lang/fr_FR.json` of the Monica repository where the text exists there.

| Key | English | French |
|---|---|---|
| `vaults_title` | All your vaults | Tous vos coffres |
| `vaults_loading` | Loading your vaults | Chargement de vos coffres |
| `vaults_empty_title` | No vault yet | Aucun coffre pour l’instant |
| `vaults_empty_body` | A vault holds the people you want to remember, with their notes, dates and reminders. Start with one for yourself; you can share it or add others later. | Un coffre réunit les personnes dont vous voulez vous souvenir, avec leurs notes, leurs dates et leurs rappels. Commencez par un coffre pour vous ; vous pourrez le partager ou en ajouter d’autres plus tard. |
| `vaults_new` | New vault | Nouveau coffre |
| `vaults_refresh` | Refresh | Actualiser |
| `vaults_refresh_failed` | Your vaults could not be updated. | Vos coffres n’ont pas pu être mis à jour. |
| `vaults_try_again` | Try again | Réessayer |
| `vault_default` | Default | Par défaut |
| `vault_no_contacts` | No contacts | Aucun contact |
| `vault_contacts` (plurals) | one: %d contact / other: %d contacts | one: %d contact / other: %d contacts |
| `vault_not_found` | This vault no longer exists. | Ce coffre n’existe plus. |
| `vault_back_to_list` | Back to your vaults | Retour à vos coffres |
| `navigate_back` | Back | Retour |
| `account_button` | Your account | Votre compte |
| `new_vault_title` | New vault | Nouveau coffre |
| `new_vault_name` | Name | Nom |
| `new_vault_description` | Description (optional) | Description (facultatif) |
| `new_vault_colour` | Colour | Couleur |
| `new_vault_make_default` | Make this the default vault | Faire de ce coffre le coffre par défaut |
| `new_vault_create` | Create vault | Créer le coffre |
| `new_vault_creating` | Creating… | Création… |
| `new_vault_cancel` | Cancel | Annuler |
| `new_vault_discard_title` | Discard this vault? | Abandonner ce coffre ? |
| `new_vault_discard_message` | What you typed will be lost. | Ce que vous avez saisi sera perdu. |
| `new_vault_discard` | Discard | Abandonner |
| `new_vault_keep_editing` | Keep editing | Continuer |
| `colour_blue` … `colour_graphite` | Blue, Green, Ochre, Rust, Violet, Teal, Rose, Graphite | Bleu, Vert, Ocre, Rouille, Violet, Bleu canard, Rose, Graphite |

Removed: `home_greeting`, `home_title` (the placeholder home is gone).
Icons: `ic_plus`, `ic_vault`, `ic_person`, `ic_check`, plus `ic_arrow_back` (vault screen) and
`ic_close` (form). Kept and reused:
`home_connected` (renamed `account_connected`), `home_sign_out` (renamed `account_sign_out`),
`sign_out_*`, `welcome_instance_settings`.
