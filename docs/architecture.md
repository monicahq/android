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
├── MainActivity.kt
├── ui/theme/                 # Material 3 theme
├── contacts/                 # one package per feature
│   ├── ContactsScreen.kt     # stateful entry + stateless screen
│   ├── ContactsViewModel.kt
│   └── ContactsUiState.kt
└── data/
    └── contacts/
        ├── ContactsRepository.kt
        └── ...data sources
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
- A screen with no state yet (like the welcome screen) is only the stateless composable, called
  from `MainActivity`; the `Route` and `ViewModel` arrive with the first state to hold.

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
- Add a Jetpack library (Hilt, Room, Navigation, DataStore, WorkManager) only when a feature needs it,
  and justify it in the feature's `plan.md`.
