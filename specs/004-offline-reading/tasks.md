---
description: "Task list for offline reading"
---

# Tasks: Offline Reading

**Input**: Design documents from `specs/004-offline-reading/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/offline-copy.md,
quickstart.md. **PR #7 merged into `main` and this branch rebased on it (T001).**

**Tests**: Mandatory (constitution III, IV, VIII). Each test task comes before the code it covers.

**Organization**: by user story. Story 3 (privacy) is built **before** stories 1 and 2: no data is
written to the phone until every way of erasing it works. Paths: `main/` =
`app/src/main/java/com/monicahq/android/`, `test/` = `app/src/test/java/com/monicahq/android/`,
`androidTest/` = `app/src/androidTest/java/com/monicahq/android/`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1…US5 from spec.md

---

## Phase 1: Setup

- [X] T001 Rebase `2026-11-27-offline-reading` on `main` once PR #7 is merged; check that `main/vault/VaultRepository.kt`, `VaultsViewModel.kt` and `SignedInApp.kt` exist and `./gradlew testDebugUnitTest` passes before any change
- [X] T002 Add to `gradle/libs.versions.toml`: `room = "2.8.5"`, `ksp = "2.3.14"`, `sqlcipher = "4.20.0"`; libraries `androidx-room-runtime`, `androidx-room-ktx`, `androidx-room-compiler`, `androidx-room-testing`, `sqlcipher-android` (`net.zetetic:sqlcipher-android`); plugins `ksp` (`com.google.devtools.ksp`) and `room` (`androidx.room`). In `app/build.gradle.kts`: apply both plugins, `room { schemaDirectory("$projectDir/schemas") }`, `implementation` room-runtime, room-ktx and sqlcipher-android, `ksp(room-compiler)`, `androidTestImplementation(room-testing)`; run `./gradlew assembleDebug` to prove KSP works with AGP 9's built-in Kotlin (research R1)
- [X] T003 In the `kover` block of `app/build.gradle.kts`, exclude Room's generated classes (`*_Impl`, `*_Impl$*`) from coverage
- [X] T004 [P] Add `stale_notice` (EN "Not up to date. Last updated %1$s.", FR "Pas à jour. Mis à jour %1$s.") to `app/src/main/res/values/strings.xml` and `values-fr/strings.xml`, and an `ic_offline` vector drawable (cloud with a slash, design-system stroke style) in `app/src/main/res/drawable/` (contracts/offline-copy.md "Strings")

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the encrypted database, the update rule and the copy's interface, with fakes for JVM tests.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests first

- [X] T005 [P] Write `test/offline/SyncRuleTest.kt` for the four rules of data-model.md "Update rule": replace a `synced` row present in the answer, delete a `synced` row absent from it, insert a new record as `synced`, never replace nor delete a row whose `sync_state` is not `synced` (data-model.md `vaults.sync_state`, `vaults.server_id`, `vaults.local_id`); plus the single-record rules (answer replaces, `NotFound` deletes, pending row kept)
- [X] T006 [P] Write `androidTest/offline/OfflineDatabaseTest.kt` against a real `RoomOfflineCopy` in a temporary `noBackupFilesDir` subfolder: vaults read back `ORDER BY position, server_id` (data-model.md `vaults.position`); `server_id` unique (data-model.md `vaults.server_id`); the database file does not start with `SQLite format 3` and its bytes contain no vault name; files live under `noBackupFilesDir`; `erase()` deletes the Keystore key `monica_offline`, `offline.key` and `offline.db`/`-wal`/`-shm`/`-journal`; after `destroyKey()` behind its back, `openOrErase` erases and reopens an empty copy; every table listed in `sqlite_master` is gone or empty after `erase()` (covers tables added later, FR-015)
- [X] T007 [P] Add to `androidTest/auth/KeystoreTokenCipherTest.kt`: two aliases do not decrypt each other's values; after `destroyKey()` an old value no longer decrypts and a new one round-trips

### Implementation

- [X] T008 In `main/auth/TokenCipher.kt`: `KeystoreTokenCipher(alias: String = "monica_session")` and `fun destroyKey()` on `TokenCipher` (deletes the alias from the Keystore); update `test/auth/FakeTokenCipher.kt` so `destroyKey()` makes earlier values undecryptable (research R2)
- [X] T009 [P] Create `main/offline/OfflineCopy.kt`: `Owner(instanceUrl, userId)`, `Cached<T>(value, syncedAt)`, interface `OfflineCopy` (`openOrErase(owner, now)`, `erase()`, `contacted(now)`, `val vaults: VaultCopy`) and interface `VaultCopy` (`all(): Cached<List<Vault>>?`, `one(id): Cached<Vault>?`, `replaceAll(owner, vaults, now)`, `replaceOne(owner, vault, now)`, `remove(owner, id)`) per data-model.md "App types"
- [X] T010 [P] Create `main/offline/SyncRule.kt`: pure functions computing upserts and deletions from rows and an answer (data-model.md "Update rule"). Makes T005 pass
- [X] T011 Create `main/offline/OfflineDatabase.kt`: `@Database(version = 1, exportSchema = true)` with entities `VaultEntity` (data-model.md `vaults.local_id`, `vaults.server_id`, `vaults.sync_state`, `vaults.server_updated_at`, `vaults.synced_at`, `vaults.name`, `vaults.description`, `vaults.colour`, `vaults.is_default`, `vaults.contact_count`, `vaults.position`; unique index on `server_id`), `SyncMetaEntity` (data-model.md `sync_meta.resource`, `sync_meta.synced_at`), `SessionMetaEntity` (data-model.md `session_meta.id`, `session_meta.instance_url`, `session_meta.user_id`, `session_meta.last_contact_at`), and their suspend DAOs; commit the generated `app/schemas/com.monicahq.android.offline.OfflineDatabase/1.json`
- [X] T012 [P] Create `main/offline/Passphrase.kt`: 32 bytes from `SecureRandom`, stored as `offline.key` encrypted by the given `TokenCipher` (data-model.md "Files next to the database"); read, create, delete
- [X] T013 Create `main/offline/RoomOfflineCopy.kt` implementing `OfflineCopy` and an inner Room-backed `VaultCopy`: `System.loadLibrary("sqlcipher")` once; database at `File(noBackupFilesDir, "offline/offline.db")` with `openHelperFactory(SupportOpenHelperFactory(passphrase))`; `openOrErase` checks `session_meta` owner, `0 ≤ now − last_contact_at ≤ 30 days`, and that the database opens, erasing otherwise (research R5); `erase()` in the order of data-model.md; `contacted(now)` writes at most once per hour (research R7); every write runs in one transaction, applies `SyncRule`, sets `sync_meta`, and is dropped under a mutex unless the owner is current (research R6); all I/O on an injected `CoroutineDispatcher`. Makes T006 pass
- [X] T014 [P] Create `test/offline/FakeOfflineCopy.kt` (in-memory `OfflineCopy` and `VaultCopy` using the real `SyncRule`, recording `erase()` calls and the state at which they happened)
- [X] T015 Wire it: `main/MonicaApplication.kt` builds `RoomOfflineCopy(noBackupFilesDir, KeystoreTokenCipher("monica_offline"), Dispatchers.IO)` and passes it to `SessionManager` and `VaultRepository`; `test/TestGraph.kt` passes a `FakeOfflineCopy`

**Checkpoint**: unit tests and `OfflineDatabaseTest` pass; nothing writes to the copy yet.

---

## Phase 3: User Story 3 - The copy never outlives the session (Priority: P1) 🔒 first

**Goal**: every session end erases the copy before the welcome screen; start-up sweeps stale or foreign copies; contact is recorded.

**Independent Test**: for each way a session ends, the copy is erased before `SignedOut`; a copy older than 30 days, from the future, or of another owner is erased at start.

### Tests for User Story 3

- [X] T016 [P] [US3] Add to `test/auth/SessionManagerTest.kt`: `erase()` happens before `SignedOut` is published for sign-out (instance reachable and not), refused renewal (expired and revoked) and rejected session; `load()` signed out with a copy → erased; signed in with another owner, `last_contact_at` older than 30 days, clock earlier than `last_contact_at` (data-model.md `session_meta.last_contact_at`), or an unopenable copy → erased before `SignedIn`; `signIn()` opens the copy for the new owner after erasing another owner's; a write started before sign-out and finishing after it is dropped; `contacted` reaches the copy
- [X] T017 [P] [US3] Add to `test/network/ApiClientTest.kt`: contact is reported for 2xx, 404 and 422 answers, not for 401, redirects to another origin, or network failures

### Implementation for User Story 3

- [X] T018 [US3] In `main/auth/SessionManager.kt`: constructor takes `offlineCopy: OfflineCopy`; `endLocked()` calls `offlineCopy.erase()` (failures caught, key already destroyed) before `_state.value = SignedOut`; `load()` calls `openOrErase(Owner(issuer, user.id), clock())` before publishing `SignedIn`, or `erase()` when signed out; `signIn()` calls `openOrErase` for the new owner; new `internal fun contacted()` forwards to `offlineCopy.contacted(clock())` (plan "Implementation notes"). Makes T016 pass
- [X] T019 [US3] In `main/network/ApiClient.kt` `finish()`: call `sessions.contacted()` for every answer that is not a 401 from the session's origin. Makes T017 pass
- [X] T020 [US3] Write `androidTest/offline/OfflinePrivacyEndToEndTest.kt` against `TestMonica`: sign in, load the list, sign out with `TestMonica` stopped, relaunch offline → welcome screen and `offline.db` absent; sign in, load, end the session on the server (refuse tokens), relaunch online → welcome screen with the reason and no copy; switch instance → no data of the first instance in the database

**Checkpoint**: erasure proven at every session end; still no screen shows the copy.

---

## Phase 4: User Story 1 - See my vaults without a connection (Priority: P1) 🎯 MVP

**Goal**: the vault list is kept, shown offline with "not up to date", updated when online.

**Independent Test**: load the list, stop the instance, relaunch: same list with the line; restart the instance: line gone.

### Tests for User Story 1

- [X] T021 [P] [US1] Add to `test/vault/VaultRepositoryTest.kt`: `cachedVaults()` null with no copy; `vaults()` success stores the list (order, colour as answered, data-model.md `vaults.colour`, `vaults.description`) and `cachedVaults()` returns it with `syncedAt`; a failed call leaves the copy unchanged; a renamed/removed/added vault is reflected after the next success; a write for a previous owner is dropped
- [X] T022 [P] [US1] Add to `test/vault/VaultsViewModelTest.kt`: with a copy, the first state is `Loaded(copy)` with `staleSince = null` (no `Loading`); a failed answer keeps the copy with `staleSince = syncedAt`; a later success clears it; no copy + failure → `Failed` (unchanged); pull to refresh failing sets `staleSince` and `refreshError` (data-model.md "VaultsUiState transitions")
- [X] T023 [P] [US1] Write `androidTest/ui/StaleNoticeTest.kt`: text "Not up to date. Last updated 3 hours ago." and French, polite live region, icon hidden from TalkBack, wraps at font scale 2, accessibility checks in light and dark
- [X] T024 [P] [US1] Add to `androidTest/vault/VaultsScreenTest.kt`: the line shows under the title when `staleSince != null` and not otherwise, with the list; accessibility checks in both themes with the line

### Implementation for User Story 1

- [X] T025 [US1] In `main/vault/VaultRepository.kt`: take `OfflineCopy`; `cachedVaults()` from `copy.vaults.all()`; after a successful `vaults()`, `copy.vaults.replaceAll(owner, list, now)` with the owner captured with the `ApiClient` (research R6, R8). Makes T021 pass
- [X] T026 [US1] In `main/vault/VaultsViewModel.kt`: add `staleSince: Long?` to `VaultsUiState`; first load shows the copy before calling the API; failure with a copy sets `staleSince`; success clears it. Makes T022 pass
- [X] T027 [P] [US1] Create `main/ui/StaleNotice.kt` (`StaleNotice(staleSince: Long, now: Long)`; `DateUtils.getRelativeTimeSpanString`; `stale_notice`; `ic_offline` decorative; `liveRegion = Polite`; theme colours only) (research R9). Makes T023 pass
- [X] T028 [US1] In `main/vault/VaultsScreen.kt`: show `StaleNotice` under the title in the list when `uiState.staleSince != null`; preview with the line in light and dark. Makes T024 pass
- [X] T029 [US1] Write `androidTest/offline/OfflineEndToEndTest.kt` journey 1: sign in with three vaults, list shown, stop `TestMonica`, relaunch → same three vaults in order with the line; start a new `TestMonica` at the same address answering → line gone after reload

**Checkpoint**: MVP: vaults readable offline, safely.

---

## Phase 5: User Story 2 - Instant screens when online (Priority: P1)

**Goal**: the copy shows at once, then the answer replaces it without spinner or jump.

**Independent Test**: with an instance answering after 3 s, the list is visible immediately; a rename on the server appears afterwards without action.

- [X] T030 [P] [US2] Add to `test/vault/VaultsViewModelTest.kt`: with a copy and an answer delayed 300 ms, the state is never `Loading` and goes copy → answer; `refreshing` stays false during that automatic update
- [X] T031 [P] [US2] Add to `androidTest/vault/VaultsScreenTest.kt`: switching from a copy of 3 vaults to an answer where the second is renamed keeps the first card in place and shows no progress indicator
- [X] T032 [US2] Make T030–T031 pass in `main/vault/VaultsViewModel.kt` and `main/vault/VaultsScreen.kt` if they do not already (cards keyed by id; the automatic update never sets `refreshing`)
- [X] T033 [US2] Add journey 2 to `androidTest/offline/OfflineEndToEndTest.kt`: rename a vault in `TestMonica` with a delayed answer, relaunch → old name first, then the new one

---

## Phase 6: User Story 4 - Open a vault without a connection (Priority: P2)

**Goal**: any vault in the copy opens offline, with the line; a vault gone on the server leaves the copy.

**Independent Test**: offline, open a vault never opened: name, colour, description and the line.

### Tests for User Story 4

- [X] T034 [P] [US4] Add to `test/vault/VaultRepositoryTest.kt`: `cachedVault(id)` returns a vault stored by the list even if never opened; `vault(id)` success replaces it and sets `sync_meta` `vault:<id>` (data-model.md `sync_meta.resource`); `NotFound` removes it from the copy and from `cachedVaults()`
- [X] T035 [P] [US4] Add to `test/vault/VaultViewModelTest.kt`: copy shown at once; failure keeps it with `staleSince`; `NotFound` → `NotFound`
- [X] T036 [P] [US4] Add to `androidTest/vault/VaultScreenTest.kt`: the line shows in `Loaded` with `staleSince`; accessibility checks with the line in both themes

### Implementation for User Story 4

- [X] T037 [US4] In `main/vault/VaultRepository.kt`: `cachedVault(id)` (the vault's own `sync_meta` time, else the list's); write after `vault(id)` success; `remove` on `NotFound`. Makes T034 pass
- [X] T038 [US4] In `main/vault/VaultViewModel.kt`: `staleSince` on `VaultUiState.Loaded`; copy first, then the answer. Makes T035 pass
- [X] T039 [US4] In `main/vault/VaultScreen.kt`: `StaleNotice` under the name when `staleSince != null`. Makes T036 pass
- [X] T040 [US4] Add journey 3 to `androidTest/offline/OfflineEndToEndTest.kt`: offline, tap a vault never opened → its screen with the line

---

## Phase 7: User Story 5 - Every later resource reads offline the same way (Priority: P3)

**Goal**: the mechanism is documented and its erasure is proven to cover tables it does not know.

**Independent Test**: review of the contract; the generic "every table empty after erase" test exists (T006).

- [X] T041 [US5] Add an "Offline copy" section to `docs/architecture.md`: the guarantees and the five steps of `specs/004-offline-reading/contracts/offline-copy.md`, the `offline/` package in the tree, where `SessionManager` erases; link the contract
- [X] T042 [P] [US5] Add to `docs/testing.md`: `FakeOfflineCopy`, `SyncRule` tests, the device database tests and how to test a later table's migration with `MigrationTestHelper`

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T043 [P] Add the feature lines to `docs/FEATURES.md` with the `feature-documentator` skill
- [X] T044 Run the `accessibility-reviewer` agent on the change and fix its confirmed findings
- [ ] T045 Run `./gradlew spotlessApply lintDebug testDebugUnitTest koverVerifyDebug assembleDebug assembleRelease` and `./gradlew connectedDebugAndroidTest` (definition-of-done skill, step 8 included); fix until green
- [ ] T046 Walk through `specs/004-offline-reading/quickstart.md` manual scenarios, including the release APK (11), `strings` on the pulled database (9), the backup restore (10), and an Android 15+ emulator for 16 KB pages

---

## Dependencies & Execution Order

- **Setup (1)** → **Foundational (2)** → **US3 (3)** → **US1 (4)** → **US2 (5)** and **US4 (6)** in parallel → **US5 (7)** → **Polish (8)**.
- US3 comes before US1 on purpose: the first task that writes real data (T025) must find every erasure path already in place and tested.
- US2 depends on US1 (same ViewModel and screen). US4 depends on US1's repository changes (T025) but not on US2.
- Within a story: tests first (they fail), then repository, ViewModel, screen, end-to-end.

### Parallel Opportunities

- Phase 1: T004 alongside T002–T003.
- Phase 2: T005, T006, T007 together; then T009, T010, T012, T014 together; T011 → T013 → T015.
- US3: T016 ∥ T017. US1: T021–T024 together; T027 ∥ T025. US2 ∥ US4 once US1 is done.
- Polish: T043 ∥ T041/T042.

## Parallel Example: User Story 1

```text
Together: T021 repository tests, T022 view model tests, T023 StaleNotice tests, T024 screen tests
Then: T025 repository ∥ T027 StaleNotice → T026 view model → T028 screen → T029 end-to-end
```

## Implementation Strategy

### MVP

Phases 1 → 2 → 3 (US3) → 4 (US1): vaults readable offline, with every erasure guarantee. US3 is
part of the MVP; shipping US1 without it is not an option.

### Incremental Delivery

1. MVP above.
2. US2 (instant online) and US4 (vault offline).
3. US5 documentation, then Polish, then one pull request.
