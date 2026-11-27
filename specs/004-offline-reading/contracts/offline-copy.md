# Contract: the offline copy

What every feature can rely on, and what it must do. This is the reference for constitution
Principle VIII; a later feature that keeps data offline follows it without redesigning anything.

## Guarantees (given by `OfflineCopy` and `SessionManager`)

1. **One owner.** The copy belongs to one instance and one account. It is opened by `SessionManager`
   when a session starts or is restored, never by a feature.
2. **Erased at every session end**, before `SessionState.SignedOut` is published: sign-out (even when
   the instance could not be told), instance switch, refused renewal, session rejected by the
   instance.
3. **Erased at start** when it belongs to another owner, cannot be opened, is older than 30 days since
   the last contact, or the clock is earlier than that contact.
4. **No late write.** A write for an owner that is no longer current is dropped.
5. **Encrypted at rest** (SQLCipher, passphrase wrapped by a Keystore key), **never backed up**
   (`noBackupFilesDir`), never logged.
6. **Everything at once.** Erasure deletes the whole database: a feature never erases its own tables.

## What a feature does to read offline

1. Add its table(s) with the five synchronisation columns (data-model.md), and a Room migration with
   its test against the exported schema.
2. In its repository: a `cachedX(): Cached<…>?` read, and, after each successful API answer, apply
   the update rule (`SyncRule`) inside one transaction and set its `sync_meta` row. Writes go through
   the owner check.
3. In its ViewModel: show the copy at once; on a failed answer with a copy shown, set `staleSince`.
4. In its screen: keep `StaleNotice(staleSince)` composed (it shows nothing while up to date), outside
   any scrolling list.
5. Tests: copy shown offline, "not up to date" state, update replacing the copy, and that its table
   is empty after `erase()` (one shared test already covers the whole database; adding the table to
   it is enough).

## What a feature never does

- Write to the copy anything the instance did not answer (no offline writes until a feature adds the
  `pending_operations` queue and the constitution allows it).
- Keep data on the phone anywhere else (DataStore, files, preferences, logs).
- Open, erase or re-key the database itself.

## Strings (English / French)

| Key | English | French |
|---|---|---|
| `stale_notice` | Not up to date. Last updated %1$s. | Pas à jour. Mis à jour %1$s. |

`%1$s` is the relative time formatted by ICU's `RelativeDateTimeFormatter` in the screen's locale
("3 hours ago" / "il y a 3 heures").
