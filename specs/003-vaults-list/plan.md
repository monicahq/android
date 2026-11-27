# Implementation Plan: Vault List

**Branch**: `2026-11-27-vaults-list` | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/003-vaults-list/spec.md`

## Summary

Replace the signed-in placeholder home with the vault list from the design: one card per vault
(colour square with initial, name, contact count, "Default" badge), an empty state, a "New vault"
form, a minimal vault screen and an account panel holding what the placeholder offered (instance
settings, sign out). Everything comes from the existing Monica API (`GET /vaults`,
`GET /vaults/{id}`, `POST /vaults`); nothing is added to Monica.

Technically: `ApiClient` stays the single place that talks HTTP and gains generic `get`/`post` plus
`NotFound` and `Invalid` results; a new `VaultRepository` holds the vault endpoints (the
"Actions" of this resource); one ViewModel per screen plays the controller; Navigation Compose with
type-safe routes links the three screens. Light and dark follow the system with measured vault
colours (research R6).

## Technical Context

**Language/Version**: Kotlin 2.4.21 (built into AGP 9.5.3), JDK 21

**Primary Dependencies**: Jetpack Compose (BOM 2026.11.01), Material 3, Lifecycle 2.11.1, OkHttp
5.5.1, kotlinx.serialization 1.13.0, **new**: Navigation Compose 2.10.2

**Storage**: N/A (nothing new stored; the session stays in the existing encrypted DataStore)

**Testing**: JUnit 4 + kotlinx-coroutines-test + `FakeMonica`/`TestGraph` (JVM); Compose UI tests
with accessibility checks + `TestMonica` end-to-end (instrumented); Kover ≥ 80%

**Target Platform**: Android 9+ (minSdk 28), targetSdk 37

**Project Type**: mobile-app (single `app` module)

**Performance Goals**: list (or empty state) visible within 2 s after sign-in on a normal mobile
connection (SC-001): one request for almost every account (`per_page=100`)

**Constraints**: API only, instance isolation preserved (answers after sign-out/switch discarded),
WCAG 2.2 AA light and dark, English + French

**Scale/Scope**: 3 screens + 1 sheet, 1 repository, ~40 strings; accounts with 0 to a few hundred
vaults

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| Product context: API only, same words, web read first | Uses only documented `/api/v1/vaults` endpoints; wording from `lang/en.json`/`fr_FR.json` ("vault"/"coffre", "No vault yet", "New vault", "Create vault", "Default"); web `vaults/index` and `vaults/form` read. Data the API lacks (visits, faces, counts) is left out rather than worked around (FR-011). | ✅ |
| I. Simplicity | No use case layer, no repository interface (tests use `FakeMonica` through the real `ApiClient`), no icon library, no cache. One new library, justified below. | ✅ |
| II. Android practices | One `StateFlow<UiState>` per ViewModel, stateless screens, repository as source of truth for vaults, coroutines, no `!!`, strings in resources, dispatchers stay injected (`ApiClient` already runs I/O on OkHttp's threads). | ✅ |
| III. Unit tested | Tests for `VaultRepository`, every ViewModel (each state and event), `Initials`, `VaultColours`, new `ApiClient` cases. | ✅ |
| IV. UI tested | Compose tests for each screen state and interaction; one end-to-end test per journey. | ✅ |
| V. Native only | Compose + Jetpack only. | ✅ |
| VI. Multilingual | Every new text in `values/` and `values-fr/`; plurals for contact counts; parity test. | ✅ |
| VII. Accessible | Measured contrast (R6), merged card semantics, custom "Refresh" action, 48 dp targets, headings, pane title, checks in every UI test, TalkBack pass in the PR. | ✅ |
| Tech standards | Version in `libs.versions.toml`; ktlint; Lint. | ✅ |

**Post-design re-check**: same result. The design adds `NotFound`/`Invalid` to an existing sealed
type and one route file; no new module, no new abstraction.

## Project Structure

### Documentation (this feature)

```text
specs/003-vaults-list/
├── spec.md
├── plan.md              # this file
├── research.md          # R1–R11: architecture, errors, paging, navigation, colours, ...
├── data-model.md        # Vault, VaultColour, NewVault, ApiResult additions, UI states
├── quickstart.md        # automated and manual validation
├── contracts/
│   ├── monica-api.md    # the three endpoints and status mapping
│   └── ui.md            # screens, semantics, strings EN/FR
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/monicahq/android/
├── MainActivity.kt                 # SignedIn → SignedInApp() instead of HomeRoute()
├── SignedInApp.kt                  # NEW: NavHost + routes (VaultsDestination, VaultDestination, NewVaultDestination)
├── network/ApiClient.kt            # get/post generic (internal), ApiResult.NotFound / Invalid
├── vault/                          # NEW package
│   ├── Vault.kt                    # Vault, VaultColour, NewVault
│   ├── VaultRepository.kt          # vaults(), vault(id), create(NewVault) + private JSON types
│   ├── Initials.kt                 # vaultInitial(), personInitials()
│   ├── VaultsViewModel.kt          # list: load, quiet reload, refresh, try again
│   ├── VaultsScreen.kt             # VaultsRoute + stateless VaultsScreen + VaultCard
│   ├── VaultViewModel.kt
│   ├── VaultScreen.kt              # placeholder vault screen
│   ├── NewVaultViewModel.kt
│   └── NewVaultScreen.kt
├── account/                        # replaces home/
│   ├── AccountViewModel.kt         # was HomeViewModel (+ initials)
│   └── AccountSheet.kt             # was HomeScreen, now a bottom sheet
└── ui/theme/VaultColours.kt        # NEW: light/dark initial + tint per VaultColour

app/src/main/res/
├── drawable/ic_plus.xml, ic_vault.xml, ic_person.xml, ic_check.xml   # NEW
├── values/strings.xml, values-fr/strings.xml                         # new keys (contracts/ui.md)

app/src/test/java/com/monicahq/android/
├── FakeMonica.kt                   # + vaults endpoints backed by an in-memory list
├── network/ApiClientTest.kt        # + 404, 422, POST body, POST renew
├── vault/VaultRepositoryTest.kt, VaultsViewModelTest.kt, VaultViewModelTest.kt,
│   NewVaultViewModelTest.kt, InitialsTest.kt
├── account/AccountViewModelTest.kt # was HomeViewModelTest
└── ui/theme/VaultColoursTest.kt    # every pair ≥ 4.5:1

app/src/androidTest/java/com/monicahq/android/
├── TestMonica.kt                   # + vaults endpoints
├── vault/VaultsScreenTest.kt, VaultScreenTest.kt, NewVaultScreenTest.kt, VaultsEndToEndTest.kt
├── account/AccountSheetTest.kt     # was home/HomeScreenTest
└── auth/*EndToEndTest.kt           # wait for "All your vaults" instead of "Hello, Jane Doe"
```

**Structure Decision**: Single `app` module, packages by feature as in `docs/architecture.md`. The
vault feature gets its own package; `home/` becomes `account/` since the home is now the vault list.
`docs/architecture.md` gets a short "Talking to the API" section with the Laravel mapping of
research R1, and the new packages in its tree; `docs/FEATURES.md` gets the feature entry.

## Implementation notes

- **ApiClient**: refactor the private `get` into `request(method, path, body?, parse)`; `send` adds
  a JSON body and `Content-Type` for POST. A POST is retried after a renewal like a GET: the first
  attempt was refused with 401 before reaching any logic, so nothing was created. `finish` maps 404
  and 422 (parsing `{message, errors}`).
- **VaultRepository(sessions: SessionManager)**: each call takes `sessions.apiClient()`; `null`
  (signed out) → `ApiResult.Discarded`. It never holds the client, so an instance switch never
  reaches an old one.
- **ViewModels** get their dependencies through factories reading `MonicaApplication` via
  `APPLICATION_KEY`, as today (`vaultRepository` is added to the application container).
  `VaultViewModel` and `NewVaultViewModel` get their route's argument through their factory.
- **VaultsScreen** calls `viewModel.onShown()` in `LifecycleResumeEffect` (research R5).
- **Sign-in tests**: `FakeMonica`/`TestMonica` start with no vault, so the arrival screen after sign
  in is the empty state; tests that need vaults add them.

## Complexity Tracking

| Addition | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Navigation Compose 2.10.2 (new library) | First screens to navigate between (list → vault, list → form), with more to come in every next feature; the constitution names it as the preferred library | A hand-made back stack re-implements predictive back, saved state and per-destination ViewModel scoping (research R4) |
