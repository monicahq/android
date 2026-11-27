# Contract: authentication inside the app

Public surface of the new app code: what screens and other features may rely on. Names are
indicative; the shape is the contract.

## SessionManager (held by `MonicaApplication`)

```kotlin
val state: StateFlow<SessionState>          // SignedOut(reason) | SignedIn(user, instanceUrl)
suspend fun logOut()                         // clears now, revokes on the server (or later)
fun apiClient(): ApiClient?                  // null when signed out; bound to one instance + generation
```

- Only `SessionManager` reads or writes tokens. `SessionState` never exposes them.
- Every authenticated call goes through `ApiClient`, which returns `ApiResult<T>`:
  `Success(value)`, `Offline`, `Timeout`, `NotAllowed`, `SessionEnded`, `ServerError`,
  `Discarded` (instance or session changed while in flight).

## SignInManager

```kotlin
suspend fun start(intent: LogIn | SignUp): StartResult   // OpenBrowser(uri) | Failed(AuthError)
suspend fun complete(callback: Uri): CompleteResult       // SignedIn | Failed(AuthError) | Ignored
fun cancel()                                               // instance switch; a resume without callback keeps the attempt
```

## InstanceChecker

```kotlin
suspend fun check(input: String): CheckResult   // Compatible(url, configuration) | Failed(AuthError)
```

Never writes anything. Saving happens in `InstanceRepository` after `Compatible` and, when signed
in, after the person confirmed the sign-out warning.

## Manifest

- `MainActivity`: `launchMode="singleTask"`, intent filter `VIEW` / `BROWSABLE` / `DEFAULT`,
  `scheme="com.monicahq.android"` (no host, so Android Lint refuses a `path`; the path is checked
  in code).
- `android:networkSecurityConfig="@xml/network_security_config"` (cleartext off, system CAs only;
  debug override allows cleartext and user CAs).
- `data_extraction_rules.xml` and `backup_rules.xml` exclude `datastore/auth.preferences_pb`.
- `<queries>` for `android.support.customtabs.action.CustomTabsService`.

## Screens

### Welcome (extended)

| State | Shows |
|---|---|
| Idle | as in 001, "Sign up" and "Log in" enabled; instance line |
| Starting / Completing | "Connecting to {instance}…" with progress, buttons disabled, spoken by TalkBack |
| Error(AuthError) | message under the buttons (live region), "Log in" enabled again; instance line still opens the sheet |
| Registration closed | "Sign up" shows "{instance} does not accept new accounts. Ask its administrator." |
| SessionEnded(reason) | "Your session expired" / "Your session was ended" message at the top |

### Instance sheet (extended)

| State | Shows |
|---|---|
| Editing | as in 001, plus "Use the official instance" |
| Checking | "Checking {address}…" progress, Save disabled |
| Invalid(AuthError) | error under the field (`isError`, announced), sheet stays open |
| ConfirmSwitch | when signed in: "Switching instance logs you out of {current}." Cancel / Switch and log out |

### Home placeholder (new)

Signed-in person's name, "Connected to {instance}", "Log out" (with confirmation). Errors from
`ApiResult` shown as a snackbar.

## Strings (en + fr, new keys)

Every `AuthError` (R24), every state above, the consent-related messages, "Log out", "Use the
official instance". Wording follows the web app ("Log in", "Log out", "Sign up", "instance").
