# Quickstart: validating the vault list

## Prerequisites

- JDK 21 (`mise install`), an emulator or device with Android 9 (API 28) or later.
- For manual checks against a real Monica: a local instance from the `../monica` checkout, reachable
  from the emulator at `http://10.0.2.2:8000` with `APP_URL` set to that address (see
  `docs/architecture.md`, "HTTPS and local development"), and a debug build.

## Automated checks

```sh
./gradlew spotlessApply lintDebug testDebugUnitTest koverVerifyDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

Expected:
- Unit: `VaultRepositoryTest` (paging, order, 404, 422, isolation), `VaultsViewModelTest`,
  `VaultViewModelTest`, `NewVaultViewModelTest`, `AccountViewModelTest`, `InitialsTest`,
  `VaultColoursTest` (every ratio of research R6 ≥ 4.5:1), `ApiClientTest` (new 404/422/POST cases),
  `StringResourcesParityTest`. Kover ≥ 80%.
- UI: `VaultsScreenTest`, `VaultScreenTest`, `NewVaultScreenTest`, `AccountSheetTest`, each state
  in light and dark with accessibility checks enabled, plus French rendering.
- End-to-end (`VaultsEndToEndTest`): sign in against `TestMonica` with no vault → empty state → New
  vault → create "Family" → list shows "Family", "Default" → tap → vault screen → back. Existing
  sign-in tests now wait for "All your vaults" instead of "Hello, Jane Doe".

## Manual scenarios (spec user stories)

| # | Steps | Expected |
|---|---|---|
| 1 | Sign in with an account holding 3 vaults (one default) | "All your vaults", 3 cards in web order, one "Default", contact counts right |
| 2 | Pull the list down after renaming a vault on the web | New name appears |
| 3 | Airplane mode, pull down | List stays, snackbar "Your vaults could not be updated." |
| 4 | Airplane mode, reopen app from scratch | Error with "Try again"; still signed in |
| 5 | Account with no vault | Empty state; "New vault" opens the form |
| 6 | Create a vault with name + colour | Back on the list, new vault at the end |
| 7 | Free-plan account at its ceiling, create | Instance's message under "Name", values kept |
| 8 | Type a name, press back | "Discard this vault?" |
| 9 | Delete a vault on the web, tap it in the app | "This vault no longer exists."; back reloads the list without it |
| 10 | Initials button → Sign out → confirm | Welcome screen |
| 11 | Switch the phone to dark mode on each screen | Dark palette, initials readable, nothing light left |
| 12 | Largest font + display size | Text wraps, nothing cut, FAB never hides the last card |
| 13 | TalkBack: reach a vault, create a vault, sign out | Every control named; card read as "Name, N contacts, default vault"; "Refresh" in the actions menu |
| 14 | Phone in French | Every text in French |
