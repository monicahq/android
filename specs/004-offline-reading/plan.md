# Implementation Plan: Offline Reading

**Branch**: `2026-11-27-offline-reading` | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-offline-reading/spec.md`

**Prerequisite**: PR #7 (`specs/003-vaults-list`) merged into `main` and this branch rebased on it.
The vault repository, view models and screens this plan changes exist only there.

## Summary

Keep what the instance answers in an encrypted database on the phone, show it at once, and keep
showing it, marked "not up to date", when the instance cannot be reached. Room 2.8.5 with SQLCipher
4.20.0 (the person's choice), the passphrase wrapped by a Keystore key of its own, the files in
`noBackupFilesDir`. `SessionManager`, which every session end already goes through, erases the whole
copy before announcing the sign-out, and sweeps at start (other owner, unreadable, 30 days without
contact, clock earlier than the last contact). The schema carries synchronisation columns from
version 1 and refreshes never touch rows with pending changes, so offline writing can come later
without changing any record's identity. Vaults (list and one vault) are the first and only resource
here; the contract in `contracts/offline-copy.md` is how every later one joins.

## Technical Context

**Language/Version**: Kotlin 2.4.21 (built into AGP 9.5.3), JDK 21

**Primary Dependencies**: existing stack (Compose BOM 2026.11.01, Material 3, Navigation 2.10.2,
OkHttp, kotlinx.serialization); **new**: Room 2.8.5 (`room-runtime`, `room-ktx`, `room-compiler`
via KSP 2.3.14, `room-testing` for migrations), SQLCipher for Android 4.20.0
(`net.zetetic:sqlcipher-android`, which brings `androidx.sqlite`)

**Storage**: `noBackupFilesDir/offline/offline.db` (SQLCipher) + `offline.key` (wrapped
passphrase); Keystore alias `monica_offline`

**Testing**: JVM (JUnit 4, coroutines-test, `FakeMonica`, `TestGraph`, new `FakeOfflineCopy`);
instrumented (real SQLCipher database, Compose UI tests with accessibility checks, `TestMonica`
end-to-end); Kover ≥ 80% with Room-generated code excluded

**Target Platform**: Android 9+ (minSdk 28), targetSdk 37; 16 KB page devices (Android 15+)

**Project Type**: mobile-app (single `app` module)

**Performance Goals**: list visible from the copy within 1 s of opening (SC-001); the database is
opened off the main thread; a list of 100 vaults reads in a few milliseconds

**Constraints**: constitution VIII guarantees (encryption, isolation, erasure before `SignedOut`, no
backup, 30 days); no data written that the instance did not answer; no logging of copy contents

**Scale/Scope**: 3 tables, 1 resource (vaults), ~2 screens changed, 1 shared composable,
1 string; mechanism reused by every later resource

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| Product context: API only, server is the truth | The copy only holds API answers; no endpoint added; writes stay online. | ✅ |
| I. Simplicity | Two new libraries and a schema wider than today's need: justified in Complexity Tracking (person's decisions R1, R3). No use case layer; ViewModels keep one `StateFlow`; the write queue is designed but not built. | ✅ with justification |
| II. Android practices | Room as the constitution's preferred storage; repositories stay the single source of truth for screens; DAOs suspend, database off the main thread with an injected dispatcher; no `!!`. | ✅ |
| III. Unit tested | `SyncRule`, `SessionManager` erasure and sweep, repositories, ViewModels on the JVM with `FakeOfflineCopy`. | ✅ |
| IV. UI tested | Stale line in every state and theme; end-to-end offline journey on the emulator. | ✅ |
| V. Native only | Room and SQLCipher are native Android libraries. | ✅ |
| VI. Multilingual | One new string, EN + FR; relative time localised by Android. | ✅ |
| VII. Accessible | Stale line in words with an icon, polite live region, contrast in both themes, wraps at the largest font. | ✅ |
| VIII. Readable offline | This feature implements it: every rule of the principle maps to FR-001–FR-017 and to `contracts/offline-copy.md`. | ✅ |
| Tech standards | Versions in `libs.versions.toml`; ktlint; Lint; release build checked. | ✅ |

**Post-design re-check**: same result. The design adds one package (`offline/`), changes
`SessionManager` in exactly two places (`endLocked`, `load`/`signIn`), and changes the vault
repository and view models without touching their screens' structure.

## Project Structure

### Documentation (this feature)

```text
specs/004-offline-reading/
├── spec.md
├── plan.md                  # this file
├── research.md              # R1–R12
├── data-model.md            # tables, sync columns, update rule, files, reserved write queue
├── quickstart.md            # automated and manual validation
├── contracts/
│   └── offline-copy.md      # guarantees and the recipe for every later resource
└── tasks.md                 # /speckit-tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/monicahq/android/
├── MonicaApplication.kt             # builds RoomOfflineCopy, gives it to SessionManager and repositories
├── auth/
│   ├── TokenCipher.kt               # KeystoreTokenCipher(alias) + destroyKey()
│   └── SessionManager.kt            # erase in endLocked(); sweep in load(); open in signIn(); contacted()
├── network/ApiClient.kt             # reports contact (any non-401 answer) to SessionManager
├── offline/                         # NEW
│   ├── OfflineCopy.kt               # interface, Owner, Cached<T>
│   ├── RoomOfflineCopy.kt           # open / erase / sweep / passphrase / owner check
│   ├── OfflineDatabase.kt           # @Database(version = 1), entities, DAOs
│   ├── SyncRule.kt                  # the update rule (pure)
│   └── Passphrase.kt                # 32 random bytes, wrapped by KeystoreTokenCipher("monica_offline")
├── vault/
│   ├── VaultRepository.kt           # cachedVaults(), cachedVault(id); writes after success; NotFound deletes
│   ├── VaultsViewModel.kt           # copy at once; staleSince
│   ├── VaultsScreen.kt              # StaleNotice under the title
│   ├── VaultViewModel.kt            # copy at once; staleSince
│   └── VaultScreen.kt               # StaleNotice
└── ui/StaleNotice.kt                # NEW shared composable

app/schemas/com.monicahq.android.offline.OfflineDatabase/1.json   # exported schema (committed)

app/src/test/java/com/monicahq/android/
├── offline/FakeOfflineCopy.kt, SyncRuleTest.kt
├── auth/SessionManagerTest.kt       # + erasure, sweep, owner, 30 days, clock, no late write
├── vault/VaultRepositoryTest.kt, VaultsViewModelTest.kt, VaultViewModelTest.kt   # + copy cases
└── TestGraph.kt                     # wires FakeOfflineCopy

app/src/androidTest/java/com/monicahq/android/
├── offline/OfflineDatabaseTest.kt   # real SQLCipher: queries, encryption at rest, noBackup, erasure
├── ui/StaleNoticeTest.kt
├── vault/VaultsScreenTest.kt, VaultScreenTest.kt   # + stale line states
└── offline/OfflineEndToEndTest.kt   # the offline journey
```

**Structure Decision**: one new feature-neutral package, `offline/`, because the copy belongs to the
session, not to vaults: every later resource uses it. `auth/` depends on `offline/` (the session
erases the copy); `offline/` depends on nothing of `auth/` but the cipher. Build changes: the KSP
plugin, Room's schema export to `app/schemas/`, and Kover excluding Room's generated `*_Impl`
classes.

## Implementation notes

- **Order inside `endLocked()`**: bump generation, cancel the client, clear the stored session,
  `offlineCopy.erase()`, then `_state.value = SignedOut`. Erasure failures (I/O) are caught and the
  start-up sweep finishes the job; the Keystore key is deleted first, so the data is already
  unreadable.
- **`load()`**: compute the owner from the stored session, then `offlineCopy.openOrErase(owner, now)`
  before publishing `SignedIn`; signed out → `erase()` if anything is on disk.
- **Dispatcher**: `RoomOfflineCopy` takes an injected `CoroutineDispatcher` (IO in the app, test
  dispatcher in tests) for opening and erasing; Room's suspend DAOs use their own executors.
- **Owner check on writes**: repositories capture `Owner` with the `ApiClient` they use; the copy
  drops the write if the owner changed (research R6).
- **Contact**: `ApiClient.finish` calls `sessions.contacted()` for answers that are not 401;
  `SessionManager` forwards to the copy, which writes at most once per hour.
- **Migration discipline**: `exportSchema = true`; every later version adds a `Migration` and a
  `MigrationTestHelper` test. No destructive fallback: an unreadable or unmigratable copy is erased
  by the sweep instead, which is equivalent and explicit.
- **Docs**: `docs/architecture.md` gets an "Offline copy" section (the contract, in short) and the
  package tree; `docs/testing.md` explains `FakeOfflineCopy` and the device database tests;
  `docs/FEATURES.md` gets the feature lines.

## Complexity Tracking

| Addition | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Room 2.8.5 + KSP 2.3.14 | Constitution's preferred local storage; queries and order for every later resource (contacts, search) | Encrypted JSON files per answer: simpler, but the person chose a database ready for synchronisation (research R1) |
| SQLCipher 4.20.0 (native, ≈ 3–4 MB per ABI) | The whole database file must be unreadable without the phone's key (FR-012, SC-005) | Encrypting values column by column leaks structure and prevents queries; Jetpack Security is deprecated (R1) |
| Synchronisation columns and the "never touch pending rows" rule, unused today | The person asked for a schema ready for sync from day one; changing every record's identity later is the expensive part (R3) | Adding them with offline writing: a migration of every table and a change of every repository at that time. The write queue itself is *not* built now (purely additive later) |
