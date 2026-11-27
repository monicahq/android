# Data Model: Vault List

Nothing is stored on the phone. Every type below lives in memory while a screen shows it.

## Vault (`vault/Vault.kt`)

What the app knows about one vault, read from `GET /api/v1/vaults` and `GET /api/v1/vaults/{id}`.

| Field | Type | From the API | Rule |
|---|---|---|---|
| `id` | `String` | `id` (UUID) | Stable; the only reference kept (never the slug, which changes on rename). |
| `name` | `String` | `attributes.name` | Plain text, up to 255 characters. |
| `description` | `String?` | `attributes.description` | `null` when nobody described the vault. Blank is treated as `null`. |
| `colour` | `VaultColour` | `attributes.colour` | One of eight; an unknown value becomes `VaultColour.Graphite`. |
| `isDefault` | `Boolean` | `attributes.is_default` | Exactly one vault is default while the account has any. |
| `contactCount` | `Int` | `attributes.contact_count` | Archived and partial contacts included (the server's count). |

Order: the list keeps the server's order (`position`), so `position` itself is not kept.

## VaultColour (`vault/Vault.kt`)

An enum of the eight server colours, each with its API value:
`Blue #3f5fd0`, `Green #4f8a49`, `Ochre #9a6a24`, `Rust #9a3f2b`, `Violet #6b4fa8`,
`Teal #2f7f86`, `Rose #a8447a`, `Graphite #4a4a52`.

- `fromApi(value)`: case-insensitive match, `Graphite` for anything else.
- Each colour has a spoken name in string resources (used by the form's swatches), English and
  French.
- How each colour is drawn in light and dark (initial and tint) is in `ui/theme/VaultColours.kt`
  (research R6), not in the model.

## NewVault (`vault/Vault.kt`)

What the creation form sends with `POST /api/v1/vaults`.

| Field | Type | Sent as | Rule |
|---|---|---|---|
| `name` | `String` | `name` | Trimmed; must not be blank; up to 255 characters. |
| `description` | `String?` | `description` | Trimmed; blank → not sent. |
| `colour` | `VaultColour` | `colour` | Default `Blue`. |
| `makeDefault` | `Boolean` | `is_default: true` | Sent only when `true` (offered only when the account has vaults). |

## ApiResult additions (`network/ApiClient.kt`)

| Case | When | Carries |
|---|---|---|
| `Invalid` | 422 | `message: String`, `fields: Map<String, String>` (first message of each field in `errors`) |
| `NotFound` | 404 | nothing |

Existing cases unchanged: `Success`, `Failed(AuthError)`, `SessionEnded`, `Discarded`.

## UI states

### VaultsUiState (`vault/VaultsViewModel.kt`)

```text
VaultsUiState(
  content: Loading | Loaded(vaults: List<Vault>) | Failed(error: AuthError),
  refreshing: Boolean,          // pull to refresh in progress
  refreshError: AuthError?,     // shown once as a message, then cleared
)
```

`Loaded(emptyList())` is the empty state.

Transitions:

```text
Loading --load ok--> Loaded
Loading --load failed--> Failed --tryAgain--> Loading
Loaded --onShown (quiet reload) ok--> Loaded(new list)       (failure: unchanged)
Loaded --refresh--> Loaded + refreshing --ok--> Loaded(new list)
                                         --failed--> Loaded + refreshError
any --SessionEnded/Discarded--> unchanged (the app leaves the signed-in tree)
```

### VaultUiState (`vault/VaultViewModel.kt`)

```text
Loading | Loaded(vault: Vault) | NotFound | Failed(error: AuthError)
```

`tryAgain()` from `Failed` returns to `Loading`.

### NewVaultUiState (`vault/NewVaultViewModel.kt`)

```text
NewVaultUiState(
  name: String, description: String, colour: VaultColour, makeDefault: Boolean,
  offerDefault: Boolean,            // from NewVaultRoute.hasVaults
  saving: Boolean,
  nameError: String?, descriptionError: String?, generalError: String?,  // instance's words
  connectionError: AuthError?,
  confirmDiscard: Boolean,
  created: Boolean,                 // the vault exists: the screen goes back to the list
  closed: Boolean,                  // leaving confirmed, or nothing typed: the screen closes
)
canCreate = name.isNotBlank() && !saving
hasInput  = name.isNotBlank() || description.isNotBlank()
```

Events: `onNameChange`, `onDescriptionChange`, `onColourChange`, `onMakeDefaultChange`, `create`,
`leave` (→ `confirmDiscard` when `hasInput`, else `closed`), `confirmDiscard`,
`dismissDiscard`, `dismissConnectionError`. Editing a field clears that field's error.

### AccountUiState (`account/AccountViewModel.kt`)

Today's `HomeUiState` renamed: `name`, `initials: String?` (research R7), `instanceUrl`,
`sheetOpen`, `confirmSignOut`. The home's `/me` check on opening and its `error` are gone: the
vault list's first load finds out the same things (an ended session, an unreachable instance) and
shows them itself.
