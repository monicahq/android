# Architecture

We follow Google's [Guide to app architecture](https://developer.android.com/topic/architecture)
and keep it as simple as possible. This page shows what that means concretely in this repo.

## Layers

```
UI layer        Composable screens  ←  ViewModel (StateFlow<UiState>)
                         │ events            ▲ state
                         ▼                   │
Data layer                   Repository  →  data sources (network, Room, DataStore)
```

- Data flows **down** as state, events flow **up** as function calls (Unidirectional Data Flow).
- A **domain layer** (use cases) is only added when the same logic is needed by several ViewModels.

## Package layout

Organize by feature, not by technical type:

```
com.monicahq.android
├── MainActivity.kt           # welcome screen or SignedInApp, following the session
├── SignedInApp.kt            # navigation while signed in
├── ui/theme/                 # Material 3 theme, vault colours
├── network/                  # ApiClient: the only class that sends requests
├── vault/                    # one package per feature
│   ├── Vault.kt              # the feature's types
│   ├── VaultRepository.kt    # its endpoints and JSON
│   ├── VaultsViewModel.kt    # one per screen, with its UiState
│   └── VaultsScreen.kt       # stateful entry + stateless screen
└── account/
```

## A screen, the reference way

```kotlin
// UI state: one immutable type describing everything the screen shows.
sealed interface ContactsUiState {
    data object Loading : ContactsUiState
    data class Success(val contacts: List<Contact>) : ContactsUiState
    data object Error : ContactsUiState
}

class ContactsViewModel(
    private val repository: ContactsRepository,
) : ViewModel() {
    val uiState: StateFlow<ContactsUiState> =
        repository.contacts()
            .map<List<Contact>, ContactsUiState> { ContactsUiState.Success(it) }
            .catch { emit(ContactsUiState.Error) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState.Loading)
}

// Stateful entry point: wires the ViewModel. Not tested directly.
@Composable
fun ContactsRoute(viewModel: ContactsViewModel, onContactClick: (Long) -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ContactsScreen(uiState = uiState, onContactClick = onContactClick)
}

// Stateless screen: pure function of its inputs. This is what UI tests target.
@Composable
fun ContactsScreen(uiState: ContactsUiState, onContactClick: (Long) -> Unit) { /* ... */ }
```

## Rules of thumb

- No business logic in composables; no Android `Context` in ViewModels.
- Repositories expose `Flow` for observable data and `suspend` functions for one-shot actions.
- Inject `CoroutineDispatcher`s; never call `Dispatchers.IO` directly inside a class.
- All user-visible text in `res/values/strings.xml`; give meaningful elements accessibility semantics.
- `MonicaApplication` creates the long-lived objects (DataStore, repositories) once, and
  ViewModel factories read them through `APPLICATION_KEY` (see `WelcomeViewModel.Factory`). Hilt
  replaces this container when several screens share several dependencies.
- Settings kept on the device (like the instance address) live in DataStore Preferences, behind a
  repository (`InstanceRepository`).
- Keep logic out of `*Screen.kt` and `*Sheet.kt` files: they are UI only and excluded from the
  coverage measure.

## Signing in and the session

Signing in follows OAuth 2.0 for native apps (RFC 8252): the instance's own sign-in page opens in a
Custom Tab, with PKCE, and the browser comes back to `com.monicahq.android:/oauth/callback`.

```
instance/   Instance.kt (addresses), InstanceChecker (is it a compatible Monica?), InstanceRepository
auth/       SignInManager (browser round trip), SessionManager (the session), AuthStore (encrypted),
            TokenEndpointClient (token, revoke), Pkce, AuthorizationRequest, AuthError
network/    HttpClients (no redirects, timeouts), ApiClient (authenticated calls to one instance)
welcome/    WelcomeViewModel (log in, sign up), InstanceSheetViewModel (check, switch)
account/    AccountViewModel, AccountSheet (who is signed in, instance settings, sign out)
```

- `SessionManager` is the only owner of tokens: it stores, renews and ends the session, and exposes
  a `StateFlow<SessionState>` that never holds a token. `MainActivity` shows the welcome screen or
  the home from it.
- `MainActivity` shows `SignedInApp` while signed in: its navigation, view models and back stack
  are dropped as soon as the session ends.
- Every authenticated call goes through the `ApiClient` that `SessionManager.apiClient()` returns.
  It is bound to one instance and one session *generation*: logging out or switching instance
  starts a new generation, cancels the old client's calls and discards their results, so nothing
  about one instance reaches another. Credentials are only added to requests for the session's own
  origin, and redirects are never followed.
- Only one renewal reaches the instance at a time (Monica ends a session whose refresh token is
  presented twice). Network errors never sign the person out; only a refused renewal does.
- The session, the pending sign-in and a logout the instance has not heard about yet are stored
  encrypted with an Android Keystore key, in the `auth` DataStore, which backups exclude.
- Network calls read the whole response on OkHttp's threads (`Call.fetch()`), never on the main
  thread.

## Talking to the API

Three layers, each with one job. For a Laravel developer: `ApiClient` is the HTTP kernel and its
middleware, a repository holds the Actions of one resource, a ViewModel is the controller, and the
`UiState` with its stateless screen is the Blade view.

| Layer | Example | Knows | Never knows |
|---|---|---|---|
| `network/ApiClient` | `get(path, parse)`, `post(path, json, parse)` | the token, renewal, origins, status codes → `ApiResult` | any endpoint but `/me` |
| repository, one per resource | `vault/VaultRepository` | the resource's endpoints, its JSON, paging | screens |
| ViewModel, one per screen | `vault/VaultsViewModel` | the screen's state and events | HTTP, JSON |
| `UiState` + `*Screen` | `VaultsUiState`, `VaultsScreen` | how it looks | where data comes from |

- A repository takes `SessionManager.apiClient()` at each call and never keeps it, so no call can
  reach a previous instance.
- `ApiResult` says how a call ended: `Success`, `Failed(AuthError)` (offline, server error...),
  `NotFound` (404), `Invalid(message, fields)` (422, the instance's own words for each field),
  `SessionEnded` (the app goes back to the welcome screen by itself) and `Discarded` (signed out
  meanwhile). Screens show `Invalid` texts as they are: the instance decides, in the account's
  language.
- No use case layer until two ViewModels share the same logic (constitution, principle I).
- A new resource is a new package (`contacts/`, `reminders/`...) with its repository, its
  ViewModels and its screens; its endpoints never go in `ApiClient`.

## Navigation

Navigation Compose with type-safe routes, in `SignedInApp.kt`: `VaultsDestination` (start),
`VaultDestination(id)`, `NewVaultDestination(hasVaults)`. A ViewModel that needs its route's
arguments gets them through its factory (`VaultViewModel.factory(id)`), so it stays a plain class in
unit tests.

### HTTPS and local development

Release builds only talk to `https://` instances, trusting the system's certificate authorities
(`res/xml/network_security_config.xml`). Debug builds override it
(`src/debug/res/xml/network_security_config.xml`) to accept `http://` and user-installed
certificate authorities, so the app can reach a Monica on the developer's machine: from the
emulator, the host is `http://10.0.2.2:8000` (or whatever port Monica serves on). Its `APP_URL`
must be that same address, since Monica builds its OAuth addresses from it.

## Languages

- English lives in `res/values/strings.xml` (default and fallback), each translation in
  `res/values-<lang>/strings.xml` with the same keys. French is provided.
- `generateLocaleConfig` (in `app/build.gradle.kts`) lists these languages in Android 13+'s
  per-app language setting; `res/resources.properties` says the default folder is English.
- A text that includes a value uses one format string (`Instance: %1$s`), never concatenation, so
  each language can place the value where its grammar wants it.

## Theme

`ui/theme` maps the Monica design system (shadcn-like greys, no accent colour, system sans) onto
Material 3 colour and type roles, in light and dark. There is no dynamic colour. Use
`MaterialTheme.colorScheme` and `MaterialTheme.typography` in screens, never raw colours.
The eight vault colours are content, not interface: `VaultColours.kt` holds a measured pair
(initial, tint) per colour and theme, each at least 4.5:1, and `VaultColour.colours()` picks the
pair of the theme in use.
- Add a Jetpack library (Hilt, Room, DataStore, WorkManager) only when a feature needs it,
  and justify it in the feature's `plan.md`.
