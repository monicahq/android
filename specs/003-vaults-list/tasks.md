---
description: "Task list for the vault list feature"
---

# Tasks: Vault List

**Input**: Design documents from `specs/003-vaults-list/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/monica-api.md,
contracts/ui.md, quickstart.md

**Tests**: Mandatory (constitution principles III and IV): unit tests for every ViewModel,
repository and non-UI class, Compose UI tests with accessibility checks for every screen state, one
end-to-end test per journey. Write each test before or with the code it covers.

**Organization**: Tasks are grouped by user story. Paths are relative to the repository root;
`main/` stands for `app/src/main/java/com/monicahq/android/`, `test/` for
`app/src/test/java/com/monicahq/android/`, `androidTest/` for
`app/src/androidTest/java/com/monicahq/android/`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story the task belongs to (US1…US5)

---

## Phase 1: Setup

**Purpose**: The one new library, and the icons every screen uses.

- [X] T001 Add Navigation Compose 2.10.2 (`navigation = "2.10.2"`, library `androidx-navigation-compose`) to `gradle/libs.versions.toml` and `implementation(libs.androidx.navigation.compose)` to `app/build.gradle.kts`; run `./gradlew assembleDebug` (research R4, plan Complexity Tracking)
- [X] T002 [P] Add vector drawables `ic_plus.xml`, `ic_vault.xml`, `ic_person.xml`, `ic_check.xml` in `app/src/main/res/drawable/`, traced from the Monica design system icons `plus`, `vault`, `user`, `check` (`_ds/…/_ds_bundle.js` in the Claude Design project), 24 dp, tinted by the caller (research R8)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The API layer, the vault model, the repository, colours and initials, used by every story.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests first

- [X] T003 [P] Extend `test/FakeMonica.kt` with an in-memory `vaults` list (empty by default) answering `GET /api/v1/vaults` (paged by `page`/`per_page`, with `meta.total`), `GET /api/v1/vaults/{id}` (404 when unknown) and `POST /api/v1/vaults` (201, appends, first vault becomes default, honours `is_default`), recording request bodies; add a `vault(...)` builder (contracts/monica-api.md)
- [X] T004 [P] Add cases to `test/network/ApiClientTest.kt`: 404 → `ApiResult.NotFound`; 422 with `{message, errors}` → `ApiResult.Invalid` keeping the first message per field; unparsable 422 → `Failed(ServerError)`; POST sends JSON body and `Content-Type: application/json`; POST is renewed and retried once after a 401; POST never sends the token to another origin (contracts/monica-api.md, "Status mapping")
- [X] T005 [P] Write `test/vault/VaultRepositoryTest.kt`: one page, three pages of 100 with order kept, empty account, stop on an empty page before `total`, `description` blank → null, unknown colour → `Graphite`, `vault(id)` found / `NotFound`, `create` body (blank description omitted, `is_default` only when true, no `structure_version_id`), `Invalid` passed through, signed out → `Discarded`, answers from the first instance discarded after switching (two `FakeMonica`s) (research R2, R3)
- [X] T006 [P] Write `test/vault/InitialsTest.kt`: vault initial of "personal" → "P", leading spaces, accented first letter, emoji first grapheme, locale uppercasing; person initials "Jane Doe" → "JD", "jane" → "J", "Jean-Pierre Martin" → "JM", "  " / "123" / "" → null (research R7)
- [X] T007 [P] Write `test/ui/theme/VaultColoursTest.kt` computing WCAG contrast for every `VaultColour` light and dark pair (initial on tint ≥ 4.5:1) and every swatch colour against the light page and the dark page and card (≥ 3:1) (research R6)

### Implementation

- [X] T008 In `main/network/ApiClient.kt`: add `ApiResult.NotFound` and `ApiResult.Invalid(message, fields)`; turn the private `get` into an internal `request(method, path, body: String?, parse)` with internal `get(path, parse)` and `post(path, jsonBody, parse)`; `send` adds the body and `Content-Type`; `finish` maps 404 and 422 (parse `message` and `errors`, first message per field) (data-model.md "ApiResult additions", contracts/monica-api.md). Update every `when` over `ApiResult` (e.g. `main/home/HomeViewModel.kt`) to compile. Makes T004 pass.
- [X] T009 [P] Create `main/vault/Vault.kt` with `Vault` (data-model.md `Vault.id`, `Vault.name`, `Vault.description`, `Vault.colour`, `Vault.isDefault`, `Vault.contactCount`), `VaultColour` with its API values and `fromApi` (data-model.md `VaultColour`), and `NewVault` (data-model.md `NewVault.name`, `NewVault.description`, `NewVault.colour`, `NewVault.makeDefault`)
- [X] T010 Create `main/vault/VaultRepository.kt`: `VaultRepository(sessions: SessionManager)` with `suspend fun vaults(): ApiResult<List<Vault>>` (paging per research R3), `suspend fun vault(id): ApiResult<Vault>`, `suspend fun create(newVault): ApiResult<Vault>`; private `@Serializable` documents for the vault object and page; `sessions.apiClient()` taken per call, `null` → `Discarded` (plan "Implementation notes"). Makes T005 pass.
- [X] T011 Add `val vaultRepository by lazy { VaultRepository(sessionManager) }` to `main/MonicaApplication.kt` and the same to `test/TestGraph.kt`
- [X] T012 [P] Create `main/vault/Initials.kt` with `vaultInitial(name, locale)` and `personInitials(name, locale): String?` using `BreakIterator` graphemes (research R7). Makes T006 pass.
- [X] T013 [P] Create `main/ui/theme/VaultColours.kt`: per `VaultColour`, light and dark `initial` and `tint` colours from the research R6 table, and a `@Composable fun VaultColour.colours()` choosing by `isSystemInDarkTheme()`; also expose the pure values for tests. Makes T007 pass.
- [X] T014 [P] Add every string key of contracts/ui.md "Strings" (English and French, `vault_contacts` as `<plurals>`) to `app/src/main/res/values/strings.xml` and `app/src/main/res/values-fr/strings.xml`; rename `home_connected` → `account_connected`, `home_sign_out` → `account_sign_out` and update their uses; keep `home_greeting`/`home_title` until T040 removes them. `StringResourcesParityTest` passes.

**Checkpoint**: `./gradlew testDebugUnitTest` green; the repository lists and creates vaults against `FakeMonica`.

---

## Phase 3: User Story 1 - See my vaults when I arrive (Priority: P1) 🎯 MVP

**Goal**: After sign-in, the person lands on "All your vaults" with one card per vault, can refresh,
and can open a vault (placeholder screen).

**Independent Test**: Sign in against an instance with three vaults (one default): the three cards
show in order with names, colours, contact counts and one "Default"; tapping one opens its screen;
back returns.

### Tests for User Story 1

- [X] T015 [P] [US1] Write `test/vault/VaultsViewModelTest.kt`: starts `Loading`; `onShown` → `Loaded(list)`; `Loaded(emptyList())`; failure → `Failed(error)`; `tryAgain` → `Loading` → `Loaded`; second `onShown` reloads quietly (list kept during, quiet failure leaves state unchanged); `refresh` sets `refreshing`, success replaces list, failure sets `refreshError` and keeps list; `refreshErrorShown` clears it; `SessionEnded`/`Discarded` change nothing (data-model.md "VaultsUiState", research R5)
- [X] T016 [P] [US1] Write `test/vault/VaultViewModelTest.kt`: `Loading` → `Loaded(vault)`; 404 → `NotFound`; failure → `Failed`; `tryAgain`; route id read from `SavedStateHandle` (data-model.md "VaultUiState")
- [X] T017 [P] [US1] Write `androidTest/vault/VaultsScreenTest.kt` (accessibility checks on, see `docs/accessibility.md`): loading shows "Loading your vaults"; three cards with names, "25 contacts", "1 contact", "No contacts" and exactly one "Default"; card announced "Personal, 25 contacts, default vault" and clicking calls `onVaultClick(id)`; title is a heading; "New vault" FAB calls `onNewVault`; failed state shows the error and "Try again" calls `onTryAgain`; refresh error shows the snackbar and calls `onRefreshErrorShown`; custom action "Refresh" calls `onRefresh`; account button "Your account" calls `onAccountClick`; French rendering ("Tous vos coffres") (contracts/ui.md "VaultsScreen")
- [X] T018 [P] [US1] Write `androidTest/vault/VaultScreenTest.kt` (accessibility checks on): loaded shows name as heading and description; no description → no empty text; `NotFound` shows "This vault no longer exists." and "Back to your vaults" calls `onBack`; failed + "Try again"; top bar "Back" calls `onBack` (contracts/ui.md "VaultScreen")

### Implementation for User Story 1

- [X] T019 [US1] Create `main/vault/VaultsViewModel.kt` with `VaultsUiState`, `onShown()`, `refresh()`, `tryAgain()`, `refreshErrorShown()` and a `Factory` reading `vaultRepository` (data-model.md "VaultsUiState", research R5). Makes T015 pass.
- [X] T020 [US1] Create `main/vault/VaultsScreen.kt`: `VaultsRoute` (collects state, `LifecycleResumeEffect` → `onShown`) and stateless `VaultsScreen` + private `VaultCard` and top bar per contracts/ui.md "VaultsScreen" (initial square from `VaultColours`, contact line with `vault_no_contacts`/`vault_contacts` plurals, outlined "Default" pill, merged card semantics read from its visible texts, scroll-dependent separator, `PullToRefreshBox`, custom "Refresh" action, extended FAB with bottom padding, loading/failed/snackbar states, `paneTitle`); light and dark `@Preview`s. Makes the list parts of T017 pass.
- [X] T021 [P] [US1] Create `main/vault/VaultViewModel.kt` (`VaultUiState`, the vault id given by its `factory(id)`, `tryAgain`). Makes T016 pass.
- [X] T022 [US1] Create `main/vault/VaultScreen.kt`: `VaultRoute` entry and stateless `VaultScreen` per contracts/ui.md "VaultScreen", with previews. Makes T018 pass.
- [X] T023 [US1] Create `main/SignedInApp.kt`: `@Serializable` routes `VaultsRoute`, `VaultRoute(id)`, `NewVaultRoute(hasVaults)` and a `NavHost` with the list and vault destinations (the form destination is added in T034); the account button temporarily does nothing until T038 (research R4)
- [X] T024 [US1] In `main/MainActivity.kt`, show `SignedInApp()` for `SessionState.SignedIn` instead of `HomeRoute()`
- [X] T025 [US1] Extend `androidTest/TestMonica.kt` with the same vault endpoints as T003 and update `androidTest/auth/SignInEndToEndTest.kt`, `SelfHostedSignInEndToEndTest.kt`, `SessionLifecycleEndToEndTest.kt`, `InstanceSwitchEndToEndTest.kt` and `androidTest/MainActivityTest.kt` to wait for "All your vaults" instead of "Hello, Jane Doe" (sign-out steps move to the account sheet in T039)
- [X] T026 [US1] Write `androidTest/vault/VaultsEndToEndTest.kt` journey 1: sign in with three vaults in `TestMonica` → list in order with one "Default" → tap the second → vault screen with its name → back → list

**Checkpoint**: US1 works end to end. Do not merge before US4 (Phase 7): sign-out is unreachable until then.

---

## Phase 4: User Story 2 - Understand an empty account (Priority: P1)

**Goal**: An account with no vault sees the empty state and a "New vault" button.

**Independent Test**: Sign in with no vault: icon, "No vault yet", explanation and "New vault" show; no FAB.

- [X] T027 [P] [US2] Add to `androidTest/vault/VaultsScreenTest.kt`: `Loaded(emptyList())` shows the vault icon hidden from TalkBack, "No vault yet" as a heading, the explanation, a "New vault" button calling `onNewVault`, no FAB, title still "All your vaults"; largest font scale keeps every text visible
- [X] T028 [US2] Implement the empty state in `main/vault/VaultsScreen.kt` per contracts/ui.md "VaultsScreen" → "Empty" (56 dp square with `ic_vault`, `vaults_empty_title`, `vaults_empty_body`, filled `vaults_new` button). Makes T027 pass.

**Checkpoint**: US1 and US2 both work; a new account lands on the empty state.

---

## Phase 5: User Story 5 - Light and dark (Priority: P1)

**Goal**: Every screen of the feature is right in dark mode and follows the system setting.

**Independent Test**: Each screen state in light and dark passes the accessibility checks; switching the system setting updates the open screen.

- [X] T029 [P] [US5] In `androidTest/vault/VaultsScreenTest.kt` and `androidTest/vault/VaultScreenTest.kt`, run each state (loading, list, empty, failed, not found) inside `MonicaTheme(darkTheme = true)` with accessibility checks on, and assert the eight colours each render a card (one vault per `VaultColour`)
- [X] T030 [US5] Review `main/vault/VaultsScreen.kt` and `main/vault/VaultScreen.kt` for any colour not taken from `MaterialTheme.colorScheme` or `VaultColours` (no raw colour, no `Color.White`); fix what T029 reveals

**Checkpoint**: P1 stories complete in light and dark.

---

## Phase 6: User Story 3 - Create a vault (Priority: P2)

**Goal**: "New vault" opens a form; creating returns to the list with the new vault; refusals show the instance's message.

**Independent Test**: Create "Family" in green: it appears at the end of the list. On an account at its plan ceiling, the instance's message shows under "Name" and the values stay.

### Tests for User Story 3

- [X] T031 [P] [US3] Write `test/vault/NewVaultViewModelTest.kt`: defaults (empty, `Blue`, `offerDefault` from the route); `canCreate` false when blank or saving; edits update state and clear that field's error; `create` sends trimmed values and sets `created`; `makeDefault` only sent when offered and ticked; `Invalid` fills `nameError`/`descriptionError`/`generalError` and keeps values; `Failed` sets `connectionError` and keeps values; second `create` while saving is ignored (one request); `leave` with input → `confirmDiscard`, without → closes; `dismissDiscard` (data-model.md "NewVaultUiState", `NewVault.name`, `NewVault.description`, `NewVault.makeDefault`; research R11)
- [X] T032 [P] [US3] Write `androidTest/vault/NewVaultScreenTest.kt` (accessibility checks on, light and dark): fields labelled; "Create vault" disabled when name blank; eight swatches named ("Blue"…), selected one has `selected` and the check icon; checkbox row shown only when `offerDefault`; saving shows "Creating…" and disables the button; `nameError` under the name field; discard dialog texts and callbacks; close "Cancel" calls `onLeave`; French rendering (contracts/ui.md "NewVaultScreen")

### Implementation for User Story 3

- [X] T033 [US3] Create `main/vault/NewVaultViewModel.kt` with `NewVaultUiState`, the events of data-model.md, `factory(offerDefault)` reading `vaultRepository`; name capped to 255 characters (data-model.md `NewVault.name`). Makes T031 pass.
- [X] T034 [US3] Create `main/vault/NewVaultScreen.kt`: `NewVaultRoute` entry (navigates back once when `created`, `BackHandler` → `leave`) and stateless `NewVaultScreen` per contracts/ui.md "NewVaultScreen", swatches from `VaultColours`, previews; add the `NewVaultRoute` destination to `main/SignedInApp.kt` and wire `onNewVault` from the list and the empty state with `hasVaults` = list not empty. Makes T032 pass.
- [X] T035 [US3] Add journey 2 to `androidTest/vault/VaultsEndToEndTest.kt`: sign in with no vault → empty state → "New vault" → name "Family", colour "Green" → "Create vault" → list shows "Family" with "Default"; and journey 3: `TestMonica` override answers 422 on `POST /api/v1/vaults` → message under "Name", name still typed

**Checkpoint**: Vaults can be created from both entry points.

---

## Phase 7: User Story 4 - Reach my account (Priority: P2)

**Goal**: The initials button opens the account sheet with instance settings and sign out; the placeholder home disappears.

**Independent Test**: Tap the initials → name and instance shown → Sign out → confirm → welcome screen.

### Tests for User Story 4

- [X] T036 [P] [US4] Move `test/home/HomeViewModelTest.kt` to `test/account/AccountViewModelTest.kt`, adapted to `AccountViewModel`, plus `initials` ("JD"; null for a name without letters)
- [X] T037 [P] [US4] Move `androidTest/home/HomeScreenTest.kt` to `androidTest/account/AccountSheetTest.kt`: name as heading, "Connected to {host}", "Instance settings" and "Sign out" callbacks, confirmation dialog, light and dark, accessibility checks on; add to `androidTest/vault/VaultsScreenTest.kt`: initials shown in the account button, person icon when initials are null, both named "Your account"

### Implementation for User Story 4

- [X] T038 [US4] Rename `main/home/HomeViewModel.kt` to `main/account/AccountViewModel.kt` (`AccountUiState` + `initials` via `personInitials`) and `main/home/HomeScreen.kt` to `main/account/AccountSheet.kt` as a `ModalBottomSheet` per contracts/ui.md "AccountSheet"; in `main/SignedInApp.kt` hold the sheet's open state, pass `AccountUiState` to `VaultsScreen` for the button, and show `InstanceSheetRoute` from "Instance settings" (research R10). Makes T036 and T037 pass.
- [X] T039 [US4] Update the sign-out and instance-switch steps of `androidTest/auth/SessionLifecycleEndToEndTest.kt` and `androidTest/auth/InstanceSwitchEndToEndTest.kt` to open the account sheet first ("Your account" → "Sign out")
- [X] T040 [US4] Delete the empty `main/home/` package and the now-unused `home_greeting`/`home_title` strings in both languages

**Checkpoint**: All five stories work; nothing from the placeholder home is lost.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T041 [P] Update `docs/architecture.md`: the package tree (`vault/`, `account/`, `SignedInApp.kt`), a "Talking to the API" section with the Laravel mapping of research R1 (ApiClient / repository / ViewModel / screen), `ApiResult.NotFound`/`Invalid`, and Navigation Compose in place of the "added only when needed" note
- [X] T042 [P] Add the feature to `docs/FEATURES.md` with the `feature-documentator` skill
- [X] T043 Run the `accessibility-reviewer` agent on the change and fix its findings; note the TalkBack pass (quickstart.md scenario 13) for the pull request
- [X] T044 Run `./gradlew spotlessApply lintDebug testDebugUnitTest koverVerifyDebug assembleDebug` and `./gradlew connectedDebugAndroidTest` (definition-of-done skill); fix until green, Kover ≥ 80%
- [ ] T045 Walk through the manual scenarios of `specs/003-vaults-list/quickstart.md` on an emulator against a local Monica, light and dark, English and French

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: after Setup; blocks every story.
- **US1 (Phase 3)**: after Foundational.
- **US2 (Phase 4)**: after US1 (same screen file, `VaultsScreen.kt`).
- **US5 (Phase 5)**: after US1 and US2 (checks their screens); repeated for the form inside T032.
- **US3 (Phase 6)**: after US1 (needs the `NavHost` and the list's `onNewVault`); independent of US2/US5 otherwise.
- **US4 (Phase 7)**: after US1 (needs `SignedInApp` and the account button); can run in parallel with US3. **Required before merging**, since US1 removes the placeholder home.
- **Polish (Phase 8)**: after all stories.

### Within Each Story

Tests first (they fail), then ViewModel, then screen, then navigation wiring, then end-to-end test.

### Parallel Opportunities

- Phase 1: T001 ∥ T002.
- Phase 2: tests T003–T007 all in parallel; then T009, T012, T013, T014 in parallel with T008; T010 after T008 + T009; T011 after T010.
- US1: T015–T018 in parallel; T021 ∥ T019.
- US3 and US4 can be built in parallel by two people after US1 (they touch `SignedInApp.kt` in different places: coordinate that file).
- Polish: T041 ∥ T042.

## Parallel Example: User Story 1

```text
Together: T015 VaultsViewModelTest, T016 VaultViewModelTest, T017 VaultsScreenTest, T018 VaultScreenTest
Then together: T019 VaultsViewModel, T021 VaultViewModel
Then: T020 VaultsScreen → T022 VaultScreen → T023 SignedInApp → T024 MainActivity → T025 → T026
```

## Implementation Strategy

### MVP

Phases 1 → 2 → 3 (US1) → 7 (US4). That is the smallest shippable increment: the person lands on
their vaults and can still sign out. Validate with the US1 and US4 independent tests.

### Incremental Delivery

1. MVP above.
2. US2 (empty state) and US5 (dark verification), so that a new account is not met by a blank page.
3. US3 (create a vault), which completes the "New vault" buttons of the design.
4. Polish, then one pull request for the whole feature (the stories are small enough to review
   together).
