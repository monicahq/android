---

description: "Task list for Secure Multi-Instance Sign In"
---

# Tasks: Secure Multi-Instance Sign In

**Input**: Design documents from `specs/002-multi-instance-auth/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Required by the constitution (principles III and IV). Every class gets JVM tests, every
screen state gets Compose UI tests, and every user story gets an end-to-end instrumented test.
Write the tests first and check that they fail.

**Organization**: Tasks are grouped by user story. Paths are relative to this repository; Monica
paths start with `../monica/`. Main code lives in `app/src/main/java/com/monicahq/android/` (written
`main/…` below), JVM tests in `app/src/test/java/com/monicahq/android/` (`test/…`), and instrumented
tests in `app/src/androidTest/java/com/monicahq/android/` (`androidTest/…`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: User story from spec.md (US1–US6)

---

## Phase 1: Setup

**Purpose**: Put the branch on the right base, deliver the server half, add libraries.

- [x] T001 Rebase `2026-11-26-multi-instance-auth` onto `2026-11-25-welcome-screen` (or onto `main` once it is merged) so `main/instance/`, `main/welcome/`, `MonicaApplication.kt`, constitution 1.3.1 and `specs/001-welcome-screen/` are present. Stop and ask if the rebase conflicts.
- [x] T002 In `../monica`, specify and deliver the server half with `monica:speckit-specify` → `monica:speckit-plan` → `monica:speckit-tasks` → `monica:speckit-implement`, using `specs/002-multi-instance-auth/contracts/monica-server.md` and research R1–R16 as input. It covers:
  - the mobile client row (data-model.md `oauth_clients` mobile client);
  - the `mobile_sessions` table, every column (data-model.md `mobile_sessions.*`);
  - `UserActionEnum` cases;
  - the `api` scope and the scope binding;
  - forced consent and `prompt=create`;
  - the metadata route and the `/api/v1/status` `mobile` block;
  - `/oauth/revoke`, the `/api/v1` guard and the logout fix;
  - one-hour tokens;
  - the web "Mobile apps" section;
  - rate limits, API docs, translations and changelog.

  Its own PR, merged before T060. Done: monicahq/monica#8053, merged 2026-11-26 (`3a4ba252f`).
- [x] T003 Add OkHttp, `mockwebserver3`, kotlinx-serialization-json, the Kotlin serialization plugin and `androidx.browser` (latest stable versions) to `gradle/libs.versions.toml`, and apply them in `build.gradle.kts` and `app/build.gradle.kts` (`buildFeatures.buildConfig = true` for `BuildConfig.DEBUG`). Run `./gradlew assembleDebug`.
- [x] T004 [P] Add `app/src/main/res/xml/network_security_config.xml` (cleartext not permitted, system trust anchors only) and `app/src/debug/res/xml/network_security_config.xml` (cleartext permitted, system + user trust anchors), and reference it from `app/src/main/AndroidManifest.xml` (research R23).
- [x] T005 [P] Exclude `datastore/auth.preferences_pb` from cloud backup and device transfer in `app/src/main/res/xml/data_extraction_rules.xml` and `app/src/main/res/xml/backup_rules.xml` (research R20).

---

## Phase 2: Foundational (blocks every story)

**Purpose**: Errors, addresses, HTTP clients, encrypted storage, PKCE. Everything here is tested
against MockWebServer or fakes, so it can be built before the Monica PR is merged.

### Tests first

- [x] T006 [P] Update `test/instance/InstanceTest.kt` for `normalizeInstanceUrl(input, allowHttp)` (data-model.md `Instance.url`):
  - `http` refused when `allowHttp` is false, accepted when true;
  - lowercase scheme and host;
  - user info, query and fragment refused;
  - port and path kept;
  - trailing slash removed.

  Also test `origin()` and `isUnderBase()` helpers.
- [x] T007 [P] Write `test/auth/PkceTest.kt`:
  - the RFC 7636 Appendix B vector;
  - verifier length and alphabet (data-model.md `PendingSignIn.codeVerifier`);
  - state of 32 random bytes (data-model.md `PendingSignIn.state`);
  - two calls never return the same value.
- [x] T008 [P] Write `test/auth/AuthStoreTest.kt` with a `FakeTokenCipher`:
  - save, read and clear `Session`, `PendingSignIn` and `PendingRevocation`, each as one value;
  - an undecryptable value is deleted and read as absent;
  - `toString()` hides tokens and verifier.
- [x] T009 [P] Write `test/network/HttpClientsTest.kt` with MockWebServer:
  - a `302` is returned, not followed;
  - timeouts are set;
  - a server with an untrusted `HeldCertificate` raises an exception that `AuthError.from()` maps to `Certificate`;
  - unknown host maps to `Unreachable`, a socket timeout to `Timeout` (research R24).

### Implementation

- [x] T010 [P] Create `main/auth/AuthError.kt`: the sealed type of research R24 and `AuthError.from(Throwable)`. No exception message is kept.
- [x] T011 Update `main/instance/Instance.kt`: `normalizeInstanceUrl(input, allowHttp)` and origin helpers (data-model.md `Instance.url`). Update the callers in `main/welcome/WelcomeViewModel.kt` to pass `BuildConfig.DEBUG` through the ViewModel factory, and update `specs/001-welcome-screen/spec.md` FR-015 and its `http` edge case to say release builds refuse `http`.
- [x] T012 [P] Create `main/auth/Pkce.kt`: `SecureRandom` verifier, state and S256 challenge (data-model.md `PendingSignIn.codeVerifier`, `PendingSignIn.state`).
- [x] T013 [P] Create `main/auth/TokenCipher.kt`:
  - the `TokenCipher` interface;
  - `KeystoreTokenCipher` (non-exportable AES-256-GCM key in `AndroidKeyStore`, fresh IV per encryption, IV stored alongside the ciphertext).

  Create `test/auth/FakeTokenCipher.kt`.
- [x] T014 Create `main/auth/AuthStore.kt` with `@Serializable` `Session`, `PendingSignIn` and `PendingRevocation`. Every field follows data-model.md `Session.*`, `PendingSignIn.*` and `PendingRevocation.*`. Each is encrypted as a single value in the `auth` DataStore, with `toString()` overridden (research R27).
- [x] T015 [P] Create `main/network/HttpClients.kt`: an OkHttp builder with `followRedirects(false)`, `followSslRedirects(false)`, connect, read and call timeouts, and no logging interceptor (research R22, R27).
- [x] T016 [P] Write `androidTest/auth/KeystoreTokenCipherTest.kt`: round trip on a device, different ciphertexts for the same input, and a tampered ciphertext refused.
- [x] T017 Wire `AuthStore` (with `KeystoreTokenCipher`) and `HttpClients` into `main/MonicaApplication.kt`. Inject dispatchers.

**Checkpoint**: `./gradlew testDebugUnitTest koverVerifyDebug` green.

---

## Phase 3: User Story 2 - Check my self-hosted instance before saving it (Priority: P1)

The instance check comes before User Story 1 because signing in needs a discovered configuration.
It also covers User Story 2's acceptance scenarios 1, 3, 4 and 6.

**Goal**: The sheet checks an address against a real Monica before saving it, refuses incompatible
or unsafe ones with a precise message, and can restore the official instance.

**Independent Test**: Against a MockWebServer Monica, enter valid, non-Monica, too-old, too-new,
off-origin, unreachable, untrusted-certificate and subdirectory addresses. Only the valid ones are
saved, and the previous instance stays otherwise.

### Tests first

- [x] T018 [P] [US2] Write `test/instance/InstanceCheckerTest.kt` with MockWebServer (contracts/monica-server.md §1–2):
  - compatible instance;
  - compatible under `/monica`;
  - `mobile` block missing → `MonicaTooOld`;
  - `minimum_android_version` above the app's → `AppTooOld` (data-model.md `InstanceConfiguration.minimumAndroidVersion`);
  - `api_version` 0 → `MonicaTooOld` (data-model.md `InstanceConfiguration.apiVersion`);
  - `issuer` ≠ base → `NoAppSignIn` (data-model.md `InstanceConfiguration.issuer`), including an `issuer` on another address set by `mcp.authorization_server`;
  - `scopes_supported` without `api` (as Monica serves it) → still `Compatible`, scope taken from `mobile.scope`;
  - an endpoint on another host or outside the base path → `NoAppSignIn` (data-model.md `InstanceConfiguration.authorizationEndpoint`, `.tokenEndpoint`, `.revocationEndpoint`);
  - no `S256` → `NoAppSignIn` (data-model.md `InstanceConfiguration.codeChallengeMethods`);
  - empty `client_id` → `NoAppSignIn` (data-model.md `InstanceConfiguration.clientId`);
  - HTML answer → `NotMonica`;
  - `301` → `Moved`;
  - `503` → `ServerError`.
- [x] T019 [P] [US2] Extend `test/instance/InstanceRepositoryTest.kt`: save and read the configuration cache, which is cleared when the URL changes. Dropped: the configuration is discovered again at every sign-in (data-model.md), so no cache is needed; "Sign up" learns that registration is closed when tapped.
- [x] T020 [P] [US2] Extend `test/welcome/WelcomeViewModelTest.kt` for sheet states:
  - `Checking` → saved on `Compatible`;
  - `Invalid(error)` keeps the previous URL;
  - "Use the official instance" restores the default;
  - the error clears when the person edits the field.
- [x] T021 [P] [US2] Extend `androidTest/welcome/WelcomeScreenTest.kt` for the sheet:
  - checking progress announced;
  - each error text under the field with `isError`;
  - "Use the official instance" button;
  - French texts;
  - accessibility checks in light and dark.

### Implementation

- [x] T022 [US2] Create `main/instance/InstanceConfiguration.kt` (`@Serializable`, every field per data-model.md `InstanceConfiguration.*`) and the DTOs for the status and metadata documents (contracts/monica-server.md §1–2).
- [x] T023 [US2] Create `main/instance/InstanceChecker.kt`: normalise, then GET `{base}/api/v1/status`, then GET `{base}/.well-known/oauth-authorization-server`, then validate per data-model.md `InstanceConfiguration.*` and return `CheckResult`. It never writes anything.
- [x] T024 [US2] Extend `main/instance/InstanceRepository.kt` with the configuration cache in the `settings` DataStore, cleared on `setUrl`. Dropped, see T019.
- [x] T025 [US2] Extend `main/welcome/WelcomeViewModel.kt` and `main/welcome/InstanceSheet.kt`:
  - `Checking` and `Invalid` states;
  - save only after `Compatible`;
  - "Use the official instance";
  - errors announced as live regions, with focus moved to the field.
- [x] T026 [P] [US2] Add every `AuthError` message for the instance check (R24) and the sheet states (contracts/android-auth.md) to `app/src/main/res/values/strings.xml` and `app/src/main/res/values-fr/strings.xml`.

**Checkpoint**: The sheet refuses everything unsafe; signing in is not there yet.

---

## Phase 4: User Story 1 - Log in to the official Monica instance (Priority: P1) 🎯 MVP

**Goal**: "Log in" → Custom Tab → Monica's web sign-in (with two-factor) → back in the app, signed in,
home shows the person and the instance. The session survives restarts and process death.

**Independent Test**: With a MockWebServer Monica, start sign-in, deliver the callback intent
(including after the activity is recreated), and check the home screen. Do it again after
restarting the app.

### Tests first

- [x] T027 [P] [US1] Write `test/auth/AuthorizationRequestTest.kt` (contracts/monica-server.md §3):
  - URL built from data-model.md `InstanceConfiguration.authorizationEndpoint` with every parameter;
  - `prompt=consent` for `LogIn` (data-model.md `PendingSignIn.intent`);
  - parsing of `code` + `state`, `error=access_denied` and other errors;
  - URI with a wrong scheme or path → `Rejected`.
- [x] T028 [P] [US1] Write `test/auth/SignInManagerTest.kt` with MockWebServer and `FakeTokenCipher`:
  - `start` saves the pending sign-in before returning the URL;
  - `complete` with the right state exchanges the code at the pending `tokenEndpoint` (data-model.md `PendingSignIn.tokenEndpoint`) with `code_verifier` and `device_name`, loads `/api/v1/me`, then saves the session (data-model.md `Session.issuer`, `Session.accessTokenExpiresAt`, `Session.refreshTokenObtainedAt`);
  - wrong state → `Failed(SignInFailed)`, nothing sent;
  - no pending sign-in → `Ignored`;
  - pending sign-in older than 1 hour → `Failed`, cleared (data-model.md `PendingSignIn.createdAt`);
  - same callback twice → the second is `Ignored`;
  - `access_denied` → `Denied`;
  - `invalid_grant` on exchange → `SignInFailed`;
  - `cancel()` → `Cancelled`;
  - a new `SignInManager` over the same store completes a sign-in started by the previous one (process death).
- [x] T029 [P] [US1] Write `test/auth/SessionManagerTest.kt` (sign-in part): state `SignedOut` at start without a session; `SignedIn` restored from the store at start; no tokens in `SessionState`.
- [x] T030 [P] [US1] Write `test/home/HomeViewModelTest.kt`: shows the name and instance from `SessionState.SignedIn`.
- [x] T031 [P] [US1] Extend `test/welcome/WelcomeViewModelTest.kt`:
  - "Log in" → `Starting` → open-browser event;
  - callback outcomes → `Error(…)` or signed in;
  - resume without a callback → `Cancelled` message.
- [x] T032 [P] [US1] Write `androidTest/home/HomeScreenTest.kt` (name, instance, "Log out" button, French, accessibility checks), and extend `androidTest/welcome/WelcomeScreenTest.kt` for the `Starting`, `Completing` and `Error` states.
- [x] T033 [US1] Write `androidTest/auth/SignInEndToEndTest.kt`, which drives `MainActivity` against a MockWebServer Monica (debug build, `http://localhost`):
  - tap "Log in";
  - capture the authorization URL;
  - deliver `com.monicahq.android:/oauth/callback?code=…&state=…` as an intent;
  - home is shown.

  Then the same with the activity recreated (`ActivityScenario.recreate()`) before the callback, and with the app relaunched afterwards (still signed in).

### Implementation

- [x] T034 [US1] Create `main/auth/AuthorizationRequest.kt`: build the URL and parse the callback into `Code`, `Denied`, `Error` or `Rejected` (research R19).
- [x] T035 [US1] Create `main/auth/TokenEndpointClient.kt`:
  - code exchange and renewal requests (form-encoded, contracts/monica-server.md §4);
  - revoke (§5);
  - mapping of `invalid_grant`, `invalid_client`, `5xx` and IO errors to typed results.
- [x] T036 [US1] Create `main/auth/SessionManager.kt`:
  - `state` restored from `AuthStore`;
  - `generation`;
  - `startSession(session)`;
  - `apiClient()`.

  Renewal and logout come in US3 and US4.
- [x] T037 [US1] Create `main/network/ApiClient.kt`, bound to the issuer origin and a generation (data-model.md `Session.issuer`):
  - adds `Authorization` only to requests on that origin;
  - `ApiResult` type;
  - `me()` returning `user`.
- [x] T038 [US1] Create `main/auth/SignInManager.kt` (contracts/android-auth.md): `start`, `complete` and `cancel` over `AuthStore`, `Pkce`, `AuthorizationRequest`, `TokenEndpointClient` and `ApiClient`, with `device_name` per research R28.
- [x] T039 [US1] Update `app/src/main/AndroidManifest.xml`:
  - `MainActivity` `launchMode="singleTask"`;
  - callback intent filter (scheme `com.monicahq.android`, path `/oauth/callback`, `BROWSABLE`);
  - `<queries>` for Custom Tabs services.
- [x] T040 [US1] Update `main/MainActivity.kt`: The sign-in is cancelled only when the app was stopped (covered by the browser), not on a brief pause.
  - open the URL in a Custom Tab, falling back to `ACTION_VIEW`;
  - pass callback intents from `onCreate` and `onNewIntent` to the ViewModel;
  - on `onResume` with a pending sign-in and no callback, call `cancel`;
  - the root composable switches between welcome and home on `SessionManager.state` (research R25).
- [x] T041 [P] [US1] Create `main/home/HomeViewModel.kt` and `main/home/HomeScreen.kt`: name, "Connected to {instance}", and a "Log out" button (its action comes in US4).
- [x] T042 [US1] Extend `main/welcome/WelcomeViewModel.kt` and `main/welcome/WelcomeScreen.kt`:
  - "Log in" → `Starting` ("Connecting to {instance}…", buttons disabled, announced);
  - `Completing`;
  - `Error(AuthError)` shown under the buttons as a live region.
- [x] T043 [US1] Wire `SignInManager`, `SessionManager` and `HomeViewModel` factories in `main/MonicaApplication.kt`.
- [x] T044 [P] [US1] Add the sign-in, home, cancelled, denied and failed strings to `values/strings.xml` and `values-fr/strings.xml`.

**Checkpoint**: MVP. Logging in works end to end against a MockWebServer Monica, and against a real
Monica once T002 is merged.

---

## Phase 5: User Story 3 - Stay signed in reliably (Priority: P2)

**Goal**: Silent renewal, exactly one renewal for concurrent expiries, no sign-out on network errors,
and clear handling of revoked or expired sessions and of forbidden actions.

**Independent Test**: With MockWebServer:
- 20 concurrent requests hit `401` and only 1 renewal reaches the server;
- offline keeps the session;
- `invalid_grant` signs out with the right reason;
- `403` does not renew.

### Tests first

- [x] T045 [P] [US3] Extend `test/auth/SessionManagerTest.kt` (renewal):
  - expired access is renewed before the request (data-model.md `Session.accessTokenExpiresAt`);
  - 20 concurrent `refresh(stale)` → 1 token call, all get the new token (Monica treats a second renewal with the same refresh token as `invalid_grant`, so a second call would sign the person out);
  - a renewal finishing after a generation change is discarded;
  - network error → still `SignedIn`, result `Offline`;
  - `invalid_grant` → `SignedOut(Revoked)`, or `SignedOut(Expired)` once the refresh token's 30 days have passed (data-model.md `Session.refreshTokenObtainedAt`);
  - the session store is never half-written: crash simulated between the token answer and saving → old session intact.
- [x] T046 [P] [US3] Write `test/network/ApiClientTest.kt`:
  - `401` from the issuer on a request with the current token → one renewal and one retry;
  - a second `401` → `SessionEnded`, no loop;
  - `401` on a request that carried an older token → retried with the current one, without renewal;
  - `403` → `NotAllowed`, no renewal;
  - `500` → `ServerError`;
  - IO error → `Offline`, session kept;
  - `302` to another host → returned as `Moved`, and the recorded request on the second MockWebServer is absent.
- [x] T047 [P] [US3] Extend `test/welcome/WelcomeViewModelTest.kt` and `androidTest/welcome/WelcomeScreenTest.kt`: `SignedOut(Expired)` and `SignedOut(Revoked)` show "Your session expired" and "Your session was ended".
- [x] T048 [US3] Write `androidTest/auth/SessionLifecycleEndToEndTest.kt`, signed in against MockWebServer:
  - expired token → renewed transparently;
  - server answers `invalid_grant` on renewal → welcome screen with "Your session was ended";
  - server down → still on home with the offline message.

### Implementation

- [x] T049 [US3] Add to `main/auth/SessionManager.kt` `refresh(staleAccessToken)` under a `Mutex`, with the generation check, atomic save through `AuthStore`, and outcome mapping (research R21).
- [x] T050 [US3] Add to `main/network/ApiClient.kt` proactive renewal before sending, and an OkHttp `Authenticator` that renews only for a `401` from the issuer origin with the current token and `priorResponse == null`. Also add the `ApiResult` mapping for `403`, `5xx`, `3xx` and IO errors (research R21, R22, R24).
- [x] T051 [US3] Show `ApiResult` errors on `main/home/HomeScreen.kt` (snackbar) and the session-ended reasons on `main/welcome/WelcomeScreen.kt`. Add their strings in English and French.

**Checkpoint**: Hours of use with short tokens and network cuts never sign the person out.

---

## Phase 6: User Story 4 - Log out and switch instance safely (Priority: P2)

**Goal**:
- Logging out ends this phone's session on the server, deferring the revocation when offline.
- Switching instance warns, cancels everything about the old instance, and requires a new sign-in.
- Nothing crosses between instances.

**Independent Test**: Two MockWebServers, A and B. Sign in on A, start requests and a renewal, then
switch to B. B records nothing from A, A receives a revoke, and the app asks to log in to B.

### Tests first

- [x] T052 [P] [US4] Extend `test/auth/SessionManagerTest.kt` (logout):
  - `logOut()` clears the session immediately, then calls revoke with the refresh token (contracts/monica-server.md §5);
  - revoke failing for network reasons → `PendingRevocation` saved, retried at the next start, dropped after success, after a non-network answer, or after 30 days (data-model.md `PendingRevocation.createdAt`);
  - no `revocationEndpoint` → local clear only (data-model.md `InstanceConfiguration.revocationEndpoint`).
- [x] T053 [P] [US4] Write `test/auth/InstanceIsolationTest.kt` with two MockWebServers:
  - switch during an in-flight request → result `Discarded`, B never receives an `Authorization` header or a request from A's `ApiClient`;
  - switch during a renewal → the renewal result is not saved, B receives no refresh token;
  - switch with a pending sign-in → pending cleared, a later callback is `Ignored`;
  - a failed check of B → session on A untouched.
- [x] T054 [P] [US4] Extend `test/welcome/WelcomeViewModelTest.kt` (`ConfirmSwitch` state when signed in) and `test/home/HomeViewModelTest.kt` (logout confirmation then `SignedOut(LoggedOut)`).
- [x] T055 [P] [US4] Extend `androidTest/welcome/WelcomeScreenTest.kt` (switch confirmation dialog) and `androidTest/home/HomeScreenTest.kt` (logout confirmation dialog), with accessibility checks.
- [x] T056 [US4] Write `androidTest/auth/InstanceSwitchEndToEndTest.kt`: signed in on A, switch to B from the sheet, confirm → welcome screen showing B; A recorded a revoke; B recorded no credential.

### Implementation

- [x] T057 [US4] Add to `main/auth/SessionManager.kt`: `switchInstance()` is `logOut()` followed by saving the new address (InstanceSheetViewModel).
  - `logOut()`;
  - `switchInstance()`, which bumps the generation, cancels the old `ApiClient` (`dispatcher.cancelAll()`), clears the session and the pending sign-in, and revokes;
  - `PendingRevocation` retry at start (research R26).
- [x] T058 [US4] Extend `main/welcome/WelcomeViewModel.kt`, `main/welcome/InstanceSheet.kt` and `main/home/HomeViewModel.kt`/`HomeScreen.kt`:
  - the `ConfirmSwitch` dialog ("Switching instance logs you out of {current}.");
  - the logout confirmation;
  - `InstanceRepository.setUrl` called only after confirming, and after `switchInstance()`.
- [x] T059 [P] [US4] Add the logout and switch strings in English and French.

**Checkpoint**: The isolation tests prove SC-005.

---

## Phase 7: User Story 2 (remaining) - Log in to a self-hosted instance end to end (Priority: P1)

**Goal**: User Story 2 acceptance scenarios 2 and 5, against a real Monica carrying the server half.

- [ ] T060 [US2] With the T002 Monica PR merged, run quickstart.md §1 and §3 steps 1 to 6 against a fresh install and against an upgraded install of Monica, both served over HTTPS, the second also under a `/monica` path. Record the results, and any subdirectory limitation, in `specs/002-multi-instance-auth/quickstart.md`.
- [x] T061 [P] [US2] Write `androidTest/auth/SelfHostedSignInEndToEndTest.kt`: same as T033 with the MockWebServer Monica served under `/monica`. The instance line and the progress text show the self-hosted address.

---

## Phase 8: User Story 5 - Sign up from the app (Priority: P3)

**Goal**: "Sign up" runs the same flow with `prompt=create`, or explains that the instance does not
accept new accounts.

**Independent Test**: With `registration_enabled` true, "Sign up" opens a URL with
`prompt=consent create`. With false, no browser opens and the explanation shows.

- [x] T062 [P] [US5] Extend `test/auth/AuthorizationRequestTest.kt` (`SignUp` → `prompt=consent create`) and `test/welcome/WelcomeViewModelTest.kt` (data-model.md `InstanceConfiguration.registrationEnabled`: false → `RegistrationClosed`, no browser).
- [x] T063 [P] [US5] Extend `androidTest/welcome/WelcomeScreenTest.kt`: the registration-closed message is shown and announced, in French too.
- [x] T064 [US5] Implement the `SignUp` intent in `main/auth/SignInManager.kt` and `main/auth/AuthorizationRequest.kt`, plus the "Sign up" handling and the `RegistrationClosed` state in `main/welcome/WelcomeViewModel.kt` and `main/welcome/WelcomeScreen.kt`, with their strings in English and French.
- [x] T065 [US5] Extend `androidTest/auth/SignInEndToEndTest.kt` with a sign-up run (callback after `prompt=create`).

---

## Phase 9: User Story 6 - See and end my phone sessions from the web (Priority: P3)

**Goal**: Entirely delivered by the Monica PR (T002, contracts/monica-server.md §7). Here we only
check that the app reacts correctly.

- [ ] T066 [US6] Run quickstart.md §3 step 8 with two emulators against the Monica carrying T002: ending one phone's session from the web signs out only that phone, which shows "Your session was ended".

---

## Phase 10: Polish & cross-cutting concerns

- [x] T067 [P] Check that no `Log.` call, `println` or HTTP logging touches tokens, codes, verifiers or states (`grep -rn "Log\.\|println" app/src/main`), and that `Session`, `PendingSignIn`, `PendingRevocation` and the token DTOs hide secrets in `toString()` (FR-033, research R27).
- [x] T068 [P] Update `docs/architecture.md`:
  - `auth`, `network` and `home` packages;
  - `SessionManager` as the only token owner;
  - HTTPS-only release with the debug network configuration;
  - how to run the app against a local Monica.

  Update `docs/testing.md` with the MockWebServer Monica and the callback-intent end-to-end pattern.
- [x] T069 [P] Update `README.md` (debug-only HTTP and user CAs for local development) and add the feature to `docs/FEATURES.md` with the `feature-documentator` skill.
- [x] T070 Run the `accessibility-reviewer` agent and the `rules-reviewer` agent on the change and fix their findings. Done with `accessibility-reviewer` (there is no `rules-reviewer` agent): screen titles for TalkBack, one live region per screen, repeated errors announced again, 3:1 borders on Home, a snackbar that stays until dismissed, dialog titles as headings. Still to check by hand with TalkBack: what is announced on screen swaps and returning from the Custom Tab.
- [x] T071 Run the definition of done: `./gradlew spotlessApply lintDebug testDebugUnitTest koverVerifyDebug assembleDebug` and `./gradlew connectedDebugAndroidTest`. Then run quickstart.md §2 and §3 in full. Gradle checks and the 84 instrumented tests pass on an emulator; quickstart §2 and §3 against a real Monica are left with T060 and T066.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**:
  - T001 comes first.
  - T002 (the Monica PR) runs in parallel with Phases 2 to 6, but is required by T060 and T066.
- **Foundational (Phase 2)**: needs T001 and T003. It blocks every story.
- **US2 check (Phase 3)**: needs Phase 2. It blocks US1, which needs the discovered configuration.
- **US1 (Phase 4)**: needs Phase 3. This is the MVP.
- **US3 (Phase 5)** and **US4 (Phase 6)**: both need US1, and can run in parallel. Both touch `SessionManager.kt`, so merge carefully or do them in sequence.
- **US2 end to end (Phase 7)**: needs US1 and T002.
- **US5 (Phase 8)**: needs US1.
- **US6 (Phase 9)**: needs T002 and US3, for the session-ended message.
- **Polish (Phase 10)**: needs every story.

### Within each story

Tests first, and they must fail. Then data types, then managers and clients, then ViewModels, then
screens, then strings, then the end-to-end test.

## Parallel examples

```text
Phase 2 tests:   T006, T007, T008, T009 together
Phase 2 code:    T010, T012, T013, T015, T016 together; then T011, T014, T017
US1 tests:       T027, T028, T029, T030, T031, T032 together
US3 + US4 tests: T045, T046, T047, T052, T053, T054, T055 together
```

## Implementation Strategy

### MVP

Phases 1 and 2, then Phase 3 (the instance check), then Phase 4 (log in). Validate against a
MockWebServer Monica, and against a real one once T002 is merged. This is enough to demo signing in
to any instance.

### Incremental delivery

1. MVP.
2. US3, reliable sessions.
3. US4, logout and switching.
4. US2 end to end on real self-hosted instances.
5. US5, sign up.
6. US6, checked once the Monica PR is merged.

Commit after each task or logical group, on this branch, following the `commits` skill.
