# Research: Offline Reading

Every decision below is final for this feature. The two that the person made themselves are marked.

## R1. Storage: Room + SQLCipher *(decided by the person)*

**Decision**: A Room database (`androidx.room` 2.8.5, compiled with KSP 2.3.14) encrypted with
SQLCipher for Android (`net.zetetic:sqlcipher-android` 4.20.0), plugged in through
`openHelperFactory(SupportOpenHelperFactory(passphrase))`. `System.loadLibrary("sqlcipher")` runs
once before the database is first opened.

**Rationale**: The person chose a real database over encrypted files, and asked for a schema ready
for synchronisation (R3). Room is the constitution's preferred local storage; SQLCipher is the
established way to encrypt a whole SQLite file on Android, and its factory works with Room 2's
SupportSQLite path. Queries, ordering and future full-text search come for free.

**Alternatives considered**:
- *Encrypted files, one per API answer* (recommended earlier: no library, erasure by deleting a
  folder). Rejected by the person in favour of a database ready for sync.
- *Room without SQLCipher, encrypting each column's value in Kotlin*: names and descriptions would
  be encrypted, but table shapes, row counts and indexes would leak, and every query on an encrypted
  column is impossible. Rejected.
- *Android's own encrypted storage* (Jetpack Security `EncryptedFile` / `EncryptedSharedPreferences`):
  deprecated, and not a database. Rejected.

**Costs accepted** (Complexity Tracking in plan.md): a native library (≈ 3–4 MB per ABI, split by
the app bundle), KSP in the build, a table and migrations per resource, the passphrase management of
R2. sqlcipher-android 4.6+ ships 16 KB-page-aligned libraries, required by Android 15+ devices.

## R2. Keys and crypto-erasure

**Decision**:
- The database passphrase is 32 random bytes from `SecureRandom`, created at the first write of a
  session.
- It is stored only encrypted, with AES-256-GCM, under a Keystore key of its own (alias
  `monica_offline`, never the session's `monica_session`), in a file next to the database.
- Erasing the copy, in this order: close the database; delete the Keystore key `monica_offline`;
  delete the wrapped passphrase; delete `offline.db`, `-wal`, `-shm`, `-journal`.

**Rationale**: Deleting the Keystore key first makes every byte left on disk unreadable at once, even
if the process dies before the files are deleted (FR-012, SC-005). A new session gets a new key and a
new passphrase, so nothing of an earlier session can ever be decrypted again. The key never leaves
the secure hardware; a backup has neither the key nor the files (R4).

The existing `KeystoreTokenCipher` already does AES-256-GCM with a fresh IV per value; it is
generalised to take its alias and gains `destroyKey()`. The session keeps its alias and never calls
`destroyKey()`.

**Alternatives considered**: deriving the passphrase from the session's tokens (they rotate at each
renewal: the database would need rekeying every hour); a Keystore key used directly by SQLCipher
(SQLCipher needs raw key bytes, which a hardware key never gives out).

## R3. Schema ready for synchronisation *(asked by the person)*

**Decision**: every table holding instance data has the same five synchronisation columns from
version 1 of the schema:

| Column | Meaning today | Meaning when writing offline arrives |
|---|---|---|
| `local_id` (primary key) | equal to `server_id` | a UUID made on the phone for a record not yet created on the instance |
| `server_id` (unique, nullable) | the instance's id | null until the instance created the record |
| `sync_state` | always `synced` | `pending_create`, `pending_update`, `pending_delete`, `failed` |
| `server_updated_at` | `updated_at` answered by the instance | detects that the instance changed a record edited offline |
| `synced_at` | when this row last came from the instance | same |

And one rule, enforced now: **an update from the instance only replaces and removes rows whose
`sync_state` is `synced`.** Today every row is `synced`, so this changes nothing visible; on the day
offline writing arrives, pending changes cannot be overwritten or deleted by a refresh.

The queue of operations to send (`pending_operations`: id, created_at, kind, target local_id,
payload, attempts, last_error) is specified in data-model.md but **not created** in this feature: it
is added by a migration with the first feature that writes offline (constitution VIII keeps writes
online until then). Creating it now would ship an unused table and unused code.

**Rationale**: the costly part of adding offline writes later is changing the identity of every
record (local id vs server id) and the update rule; doing both now costs a few columns and one tested
rule. The queue is purely additive.

## R4. Where the files live, and backups

**Decision**: the database and the wrapped passphrase live in
`Context.noBackupFilesDir/offline/` (`offline.db` and `offline.key`). Room accepts an absolute path.

**Rationale**: Android never includes `noBackupFilesDir` in cloud backups or device transfers, on
every supported version (FR-011, SC-004). No backup rule can be forgotten. The existing backup rules
stay as they are; a test asserts the database path is inside `noBackupFilesDir`.

## R5. One erasure point: the session's end

**Decision**: `SessionManager` owns the copy's life, through an `OfflineCopy` it is given:
- `endLocked()`, the single function every session end goes through (sign-out, instance switch via
  sign-out, refused renewal, session rejected), calls `offlineCopy.erase()` **before** it publishes
  `SignedOut` (FR-009, FR-010).
- `load()` at start: signed out with a copy on disk (the process died mid-erasure) → erase; signed in
  but the copy belongs to another instance or account, is older than 30 days since the last contact,
  or the clock is earlier than that contact, or the copy cannot be opened → erase, all before
  `SignedIn` is published (FR-008, FR-013).
- `signIn()` success: the copy is opened for the new owner; any copy of another owner is erased
  first.

Erasure deletes the whole database, so it covers every table, including tables later features add,
without any of them doing anything (FR-015).

**Rationale**: every way a session can end already passes through `endLocked()`; hooking there
cannot miss a case, and holding the session's lock while erasing means no request of the old session
can write afterwards.

## R6. No write after the end

**Decision**: the copy records its owner (instance + user id) when opened and forgets it when erased.
Every write takes the owner the repository saw when it started the request and is ignored, under the
copy's own lock, unless it is still the current owner. A request finishing after a sign-out is
already discarded by `ApiClient` (generation check); this is the second barrier for the instant
between the answer and the write.

## R7. "Last contact" for the 30-day limit

**Decision**: any answer from the instance to an authenticated request that is not a 401 (2xx, 404,
422...) counts as contact. `ApiClient` tells `SessionManager`, which records it in the copy
(`session_meta.last_contact_at`), at most once every hour to avoid writing at every request. On
opening, `now - last_contact_at > 30 days` or `now < last_contact_at` erases.

**Rationale**: an answer proves the session was still accepted at that time; a 401 or no answer
proves nothing. One write per hour keeps the precision far below the 30-day limit.

## R8. Showing the copy, then the instance's answer

**Decision**: repositories gain a read of the copy next to their network call:
- `VaultRepository.cachedVaults(): Cached<List<Vault>>?` reads the copy (null when none);
  `vaults()` calls the API as today and, on success, replaces the synced rows and records
  `synced_at` for the collection (`sync_meta`), in one transaction.
- `vault(id)` the same for one vault; a `NotFound` deletes its synced row (FR-007).
- ViewModels show the copy at once (no loading state), then the answer. When the answer fails and a
  copy is shown, the state carries `staleSince` (the collection's `synced_at`), which the screen turns
  into the "not up to date" line.

**Rationale**: a one-shot read plus the existing call keeps the ViewModels as they are today (UDF,
one `StateFlow`), with one new field. Observing the database with `Flow` would be the next step when
several screens show the same data at once; not needed for two screens.

## R9. The "not up to date" line

**Decision**: one shared composable `StaleNotice(staleSince: Long)` in `ui/`, under the screen's
title: an icon plus "Not up to date. Last updated 3 hours ago." / "Pas à jour. Mis à jour il
y a 3 heures.". The relative time is formatted by ICU's `RelativeDateTimeFormatter` in the screen's own locale
(so the sentence never mixes two languages). It is always composed, empty when up to date, as a
polite live region: TalkBack announces a region whose content changes; on the list it sits outside
the scrolling content, so it is seen and heard even when the list is scrolled, and text, not colour,
carries the meaning. Every later screen uses the same composable (FR-016, FR-017, story 5).

## R10. Testing on the JVM and on a device

**Decision**:
- Room and SQLCipher need Android: DAO queries, the migration baseline, encryption at rest (the file
  does not start with `SQLite format 3` and contains no vault name) and the noBackup path are tested
  in `androidTest`, on the CI emulator.
- On the JVM, `OfflineCopy` is an interface with a hand-written in-memory fake (constitution:
  fakes over mocks). The synchronisation rule (R3) is a pure Kotlin function over the rows, tested on
  the JVM, and called by the repository inside the database transaction.
- `SessionManager` tests use the fake to check erasure at every session end, the start-up sweep, the
  30-day and clock rules, and that no write lands after an erase.

**Rationale**: JVM tests stay fast and cover the logic; device tests prove the real encryption and
SQL.

## R11. Release build

**Decision**: rely on the consumer R8 rules that sqlcipher-android and Room ship; `assembleRelease`
(definition of done) proves the minified build links, and a smoke run of the release APK opening the
database is part of quickstart.md. A rule is added to `proguard-rules.pro` only if that run fails.

## R12. Exceptions (constitution VIII)

None in this feature: the vault list and the vault screen both read offline. Creating a vault stays
online (writes), as the constitution says.
