# Quickstart: validating offline reading

## Prerequisites

- PR #7 (vault list) merged into `main`, and this branch rebased on it.
- JDK 21, an emulator with Android 9+ (and one with Android 15+ for 16 KB pages), a debug build.
- For manual checks: a local Monica at `http://10.0.2.2:8000` (see `docs/architecture.md`).

## Automated checks

```sh
./gradlew spotlessApply lintDebug testDebugUnitTest koverVerifyDebug assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest
```

Expected:
- JVM: `SyncRuleTest` (the four rules of data-model.md), `SessionManagerTest` (erasure at each of
  the four session ends before `SignedOut`; start-up sweep; 30-day and clock rules; owner change;
  no write after erase; contact recorded at most once per hour), `VaultRepositoryTest` (copy read,
  replaced, `NotFound` deletes, writes dropped for an old owner), `VaultsViewModelTest` and
  `VaultViewModelTest` (copy shown at once, `staleSince` on failure, cleared on success).
- Device: `OfflineDatabaseTest` (DAO queries and order; database file is not plain SQLite and holds
  no vault name; files under `noBackupFilesDir`; erasure removes the key, the passphrase and every
  database file; a copy whose key was destroyed is erased and reopened empty), screen tests with the
  "not up to date" line (light, dark, French, largest font, accessibility checks), and
  `OfflineEndToEndTest` (sign in, load the list, stop `TestMonica`, relaunch: list shown with the
  line; restart `TestMonica`: line gone; sign out: relaunch offline shows the welcome screen and no
  data).

## Manual scenarios (spec user stories)

| # | Steps | Expected |
|---|---|---|
| 1 | Sign in, open the list, airplane mode, force-stop, reopen | Same list at once, "Not up to date. Last updated …" |
| 2 | Airplane mode off, return to the app | List updates, line disappears |
| 3 | Rename a vault on the web, reopen the app online | Old name at once, then the new one, no spinner |
| 4 | Offline, open a vault never opened before | Its name, colour, description, with the line |
| 5 | Sign out offline, reopen offline | Welcome screen; nothing of the account anywhere |
| 6 | Switch instance; sign in to the other; go offline | Only the second instance's vaults ever appear |
| 7 | End the phone's session from the web, reopen online | Welcome screen with the reason; relaunch offline shows no data |
| 8 | Offline, set the clock 31 days ahead, reopen | No data: connection error |
| 9 | `adb shell run-as com.monicahq.android ls no_backup/offline` then pull `offline.db` and run `strings` | No vault name readable |
| 10 | `adb shell bmgr backupnow com.monicahq.android` (or a device transfer), restore on a clean emulator | No data before signing in again |
| 11 | Release APK (`assembleRelease`, installed with a debug signing config) | Opens the list from the copy offline |
| 12 | TalkBack on the stale line; largest font; dark theme; French | Read on appearance; wraps; contrast passes; French text |
