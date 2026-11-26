# UI Contract: WelcomeScreen

```kotlin
@Composable
fun WelcomeRoute(viewModel: WelcomeViewModel = viewModel(factory = WelcomeViewModel.Factory))

@Composable
fun WelcomeScreen(
    uiState: WelcomeUiState,
    onSignUp: () -> Unit,
    onLogIn: () -> Unit,
    onOpenInstanceSheet: () -> Unit,
    onDraftChange: (String) -> Unit,
    onResetDraft: () -> Unit,
    onDismissInstanceSheet: () -> Unit,
    onSaveInstance: () -> Unit,
    modifier: Modifier = Modifier,
)
```

`WelcomeScreen` is stateless: it renders `uiState` and reports taps through the callbacks.
`WelcomeRoute` connects it to `WelcomeViewModel`. In this feature `onSignUp` and `onLogIn` are
no-ops.

## Elements, top to bottom

| Element | Semantics (what TalkBack and tests see) | Callback |
| --- | --- | --- |
| Instance settings button | Button, named `welcome_instance_settings`, 48 × 48 dp | `onOpenInstanceSheet` |
| Logo | Not in the semantics tree (decorative) | none |
| "Monica" wordmark | Text `app_name` | none |
| Illustration | Image, described by `welcome_illustration` | none |
| Title | Text `welcome_title`, heading | none |
| Description | Text `welcome_description` | none |
| Sign up | Button, text `welcome_sign_up`, filled, 56 dp high | `onSignUp` |
| Log in | Button, text `welcome_log_in`, outlined, 56 dp high | `onLogIn` |
| Instance line | Button, text `welcome_instance` with the host of `uiState.instanceUrl` | `onOpenInstanceSheet` |

## Instance sheet (shown when `uiState.sheet` is not null)

| Element | Semantics | Callback |
| --- | --- | --- |
| Sheet | Modal bottom sheet; tapping the scrim, swiping down or back dismisses it | `onDismissInstanceSheet` |
| Title | Text `instance_sheet_title`, heading | none |
| Explanation | Text `instance_sheet_description` | none |
| Address field | Text field labelled `instance_sheet_address`, value `sheet.draft`, URL keyboard, error `instance_sheet_invalid` when `sheet.isError` | `onDraftChange`; keyboard "Done" → `onSaveInstance` |
| Reset to default | Button `instance_sheet_reset` | `onResetDraft` |
| Cancel | Button `instance_sheet_cancel` | `onDismissInstanceSheet` |
| Save | Button `instance_sheet_save`, filled | `onSaveInstance` |

## Instance helpers

```kotlin
const val DEFAULT_INSTANCE_URL = "https://app.monicahq.com"
fun instanceHost(url: String): String
fun normalizeInstanceUrl(input: String): String?
```

Both follow the rules of [data-model.md](../data-model.md#instance).
