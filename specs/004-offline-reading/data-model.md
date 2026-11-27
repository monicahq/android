# Data Model: Offline Reading

Database `offline.db` (Room, schema version 1), encrypted with SQLCipher, in
`noBackupFilesDir/offline/`. Exported schema JSON is kept in `app/schemas/` so every later
migration is reviewed and tested against it.

## Synchronisation columns (every table holding instance data)

| Column | Type | Rule |
|---|---|---|
| `local_id` | TEXT, primary key | Today equal to `server_id`. Later: a UUID made on the phone for a record created offline. |
| `server_id` | TEXT, unique, nullable | The instance's id (UUID). Null only for a record not yet created on the instance (never today). |
| `sync_state` | TEXT, not null, default `synced` | One of `synced`, `pending_create`, `pending_update`, `pending_delete`, `failed`. Always `synced` in this feature. |
| `server_updated_at` | INTEGER, nullable | `attributes.updated_at` as answered (seconds since 1970). |
| `synced_at` | INTEGER, not null | When this row last came from the instance (milliseconds, phone clock). |

**Update rule** (pure function, `offline/SyncRule.kt`): given the rows on the phone and a complete
answer from the instance for a collection:
1. a `synced` row whose `server_id` is in the answer is replaced by the answer's values;
2. a `synced` row whose `server_id` is absent from the answer is deleted;
3. a server record with no row is inserted as `synced`;
4. a row whose `sync_state` is not `synced` is never replaced nor deleted.

For a single record: the answer replaces its `synced` row; a `NotFound` deletes its `synced` row.

## Table `vaults`

The synchronisation columns, plus:

| Column | Type | From the API |
|---|---|---|
| `name` | TEXT, not null | `attributes.name` |
| `description` | TEXT, nullable | `attributes.description` (blank stored as null) |
| `colour` | TEXT, not null | `attributes.colour` as the app understood it (`VaultColour.apiValue`; an unknown colour is already Slate) |
| `is_default` | INTEGER (boolean), not null | `attributes.is_default` |
| `contact_count` | INTEGER, not null | `attributes.contact_count` |
| `position` | INTEGER, not null | The vault's place in the instance's complete answer (0, 1, 2…), so the list reads back exactly in the instance's order (`ORDER BY position, server_id`) |

Index: unique on `server_id`.

## Table `sync_meta`

When a collection or record was last updated from the instance, for the "not up to date" line.

| Column | Type | Rule |
|---|---|---|
| `resource` | TEXT, primary key | `vaults` (the list), `vault:<server_id>` (one vault) |
| `synced_at` | INTEGER, not null | Milliseconds, phone clock, at the last successful answer |

## Table `session_meta` (exactly one row)

| Column | Type | Rule |
|---|---|---|
| `id` | INTEGER, primary key | Always 1 |
| `instance_url` | TEXT, not null | The session's instance (issuer) |
| `user_id` | TEXT, not null | The signed-in person's id |
| `last_contact_at` | INTEGER, not null | Last answer only given to an accepted token (2xx, 403, 404, 422), written at most once per hour |
| `last_seen_at` | INTEGER, not null | The latest time the phone's clock showed while the copy was open (opening, contact) |

The copy is usable only when `instance_url` and `user_id` match the session,
`now ≥ last_seen_at`, `now ≥ last_contact_at` and `now - last_contact_at ≤ 30 days`. Otherwise it is
erased (research R5). Checked when opening and on every read. A clock set back between two dates
is caught by `last_seen_at`; one set back before the app was ever opened again cannot be, and is an
accepted limit, like a session ended from the web while the phone stays offline.

## Files next to the database

| File | Content |
|---|---|
| `offline.key` | The 32-byte SQLCipher passphrase, encrypted with AES-256-GCM under Keystore alias `monica_offline` (IV in front). |
| `offline.db` (+ `-wal`, `-shm`, `-journal`) | The SQLCipher database. |

Erasure order: close the database → delete Keystore key `monica_offline` → delete `offline.key` →
delete the database files. A new session creates a new key and passphrase.

## Reserved for offline writing (NOT created in this feature)

Table `pending_operations`, added by migration 1 → 2 with the first feature that writes offline:
`id` (INTEGER autoincrement), `created_at`, `kind` (`create`/`update`/`delete`), `resource`
(`vault`, `contact`...), `target_local_id`, `payload` (JSON of the request body), `attempts`,
`last_error` (the instance's message), `idempotency_key` (needs the Monica API to support it first).

## App types

- `Cached<T>(value: T, syncedAt: Long)` — what a repository returns when it reads the copy.
- `Owner(instanceUrl: String, userId: String)` — whom the copy belongs to.
- `OfflineCopy` (interface) — `open(owner, now)`, `erase()`, `contacted(now)`, `isOwner(owner)`,
  and the database's DAOs for repositories; `RoomOfflineCopy` is the real one, `FakeOfflineCopy`
  the JVM tests' one.
- `VaultsUiState` gains `staleSince: Long?`; `VaultUiState.Loaded` gains `staleSince: Long?`. Null
  means the screen shows the instance's latest answer, or is still waiting for its first answer.

### VaultsUiState transitions (changes from spec 003 in bold)

```text
start: copy exists → **Loaded(copy), staleSince = null** ; no copy → Loading
Loaded(copy) --answer ok--> Loaded(answer), staleSince = null
Loaded(copy) --answer failed--> **Loaded(copy), staleSince = copy.syncedAt**
Loading --answer failed--> Failed(error)                         (unchanged: no copy)
pull to refresh failed --> Loaded(list), staleSince = syncedAt, refreshError (snackbar, unchanged)
```
