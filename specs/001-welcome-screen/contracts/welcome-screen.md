# UI Contract: WelcomeScreen

```kotlin
@Composable
fun WelcomeScreen(
    instanceHost: String,
    onSignUp: () -> Unit,
    onLogIn: () -> Unit,
    onInstanceSettings: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Stateless. Renders exactly what it is given and reports taps through the callbacks; it never
decides what a tap does. In this feature `MainActivity` passes `instanceHost(DEFAULT_INSTANCE_URL)`
and empty lambdas.

## Elements, top to bottom

| Element | Semantics (what TalkBack and tests see) | Callback |
| --- | --- | --- |
| Instance settings button | Button, named `welcome_instance_settings`, 48 × 48 dp | `onInstanceSettings` |
| Logo | Not in the semantics tree (decorative) | none |
| "Monica" wordmark | Text `app_name` | none |
| Illustration | Image, described by `welcome_illustration` | none |
| Title | Text `welcome_title`, heading | none |
| Description | Text `welcome_description` | none |
| Sign up | Button, text `welcome_sign_up`, filled, 56 dp high | `onSignUp` |
| Log in | Button, text `welcome_log_in`, outlined, 56 dp high | `onLogIn` |
| Instance line | Button, text `welcome_instance` with the host | `onInstanceSettings` |

## Instance helpers

```kotlin
const val DEFAULT_INSTANCE_URL = "https://app.monicahq.com"
fun instanceHost(url: String): String
```

`instanceHost` follows the derivation rule of [data-model.md](../data-model.md#instance).
