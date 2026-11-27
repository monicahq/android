# Implementation Plan: Secure Multi-Instance Sign In

**Branch**: `2026-11-26-multi-instance-auth` | **Date**: 2026-11-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-multi-instance-auth/spec.md`

## Summary

People sign in to the official Monica or to their own instance through Monica's own web sign-in, in
a Custom Tab, with OAuth 2.0 authorization code + PKCE S256, returning to
`com.monicahq.android:/oauth/callback`. Monica already runs a Passport authorization server for its
MCP assistants. This feature opens it to the app instead of adding a new authentication system: an
`api` scope bound to a first-party public client provisioned by migration on every instance,
discovery through RFC 8414 metadata plus Monica's `/api/v1/status`, forced consent, one-hour access
tokens with rotating refresh tokens, an RFC 7009 revocation endpoint, and per-phone sessions that can
be ended from the web.

The app gains:
- an instance checker that validates the address before saving it;
- a sign-in manager whose pending state survives process death;
- one session manager with encrypted storage, single-flight renewal and generation-based isolation;
- an HTTP layer that never follows redirects and only sends credentials to the issuing origin;
- HTTPS-only release builds.

The work is delivered in two pull requests: Monica first (server half, specified with Monica's Spec
Kit from [contracts/monica-server.md](contracts/monica-server.md)), then this app.

## Technical Context

**Language/Version**: Kotlin 2.4 (built into AGP 9.5), JDK 21 (app); PHP 8.4 / Laravel 13.8 with
Passport 13 (server, existing). Principles VI and VII come from constitution 1.3.1 on the welcome screen branch.

**Primary Dependencies**: existing Compose, Material 3, Lifecycle, DataStore Preferences. New:
- OkHttp, the HTTP client;
- kotlinx-serialization-json and the Kotlin serialization plugin;
- `androidx.browser`, for Custom Tabs;
- `mockwebserver3`, tests only.

Exact versions are pinned in `gradle/libs.versions.toml` at implementation time, choosing the
latest stable release. Server: no new package.

**Storage**: DataStore Preferences `settings` (instance address, discovery cache, existing); new
DataStore `auth` holding the session, pending sign-in and pending revocation, each encrypted with an
Android Keystore AES-GCM key and excluded from backups. Server: new `mobile_sessions` table and one
`oauth_clients` row.

**Testing**: JUnit 4 + `kotlinx-coroutines-test` + MockWebServer on the JVM; Compose UI tests with
accessibility checks; instrumented end-to-end tests against a MockWebServer Monica, plus the Keystore
cipher on a device. Server: PHPUnit feature tests (`tests/Feature`).

**Target Platform**: Android 9 (API 28) to Android 17 (API 37); Monica 5.x servers

**Project Type**: mobile-app (single `:app` module) + changes to the Monica web service

**Performance Goals**: instance check under 3 s on a normal connection; from the browser's return
to the signed-in home under 2 s (SC-001 is dominated by the person's own typing)

**Constraints**:
- HTTPS only in release builds.
- No secrets in logs or backups.
- Credentials only ever go to the issuing origin.
- Offline never signs the person out.
- WCAG 2.2 AA; English and French.

**Scale/Scope**: 3 screens touched (welcome, instance sheet, new home placeholder), ~40 strings,
~15 app classes; server: 4 endpoints added or changed, 1 table, 1 settings section

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan complies |
| --- | --- | --- |
| Product context | PASS | The server stays the source of truth. The missing API capabilities are added to Monica first (R16). The app uses only public endpoints: status, OAuth metadata, authorize, token, revoke, `/api/v1/me`. Wording reuses the web app's: "Log in", "Log out", "Sign up", "instance". |
| I. Simplicity | PASS (with justified libraries) | No Hilt, no Navigation, no Retrofit, no WorkManager, no AppAuth (R17, R18, R25, R26). One interface (`TokenCipher`), required to test without a Keystore. New libraries are listed in Complexity Tracking. |
| II. Android practices | PASS | UDF ViewModels with one `StateFlow<UiState>` each; repositories and managers own their data; injected dispatchers; no `!!`; strings in resources; Custom Tabs, which the platform recommends for OAuth. |
| III. Unit tests | PASS | Every non-UI class has JVM tests with fakes and MockWebServer, covering every case in the spec's testing scope. The Kover gate stays at 80%. |
| IV. UI tests | PASS | Every new screen state has Compose UI tests. Each user story has an end-to-end instrumented test that delivers the callback intent. |
| V. Native only | PASS | Custom Tabs open the system browser; no WebView. |
| VI. Multilingual | PASS | Every message in `values/` and `values-fr/`; the parity test guards it. |
| VII. Accessibility | PASS | Progress and errors announced (live regions), focus moved to errors, 48 dp targets, the confirmation dialog follows Material semantics, ATF checks in light and dark. |
| Security standard | PASS | Tokens stored only through Keystore-backed encryption (R20); no secrets in the repository; no trust-all certificate manager; cleartext traffic allowed in debug builds only (R23). |

Post-design re-check (after Phase 1): unchanged, all PASS.

## Project Structure

### Documentation (this feature)

```text
specs/002-multi-instance-auth/
├── spec.md
├── audit.md                 # What existed before (both repositories)
├── plan.md                  # This file
├── research.md              # Decisions R0–R28
├── data-model.md            # App and server entities, state transitions
├── quickstart.md            # How to validate
├── contracts/
│   ├── monica-server.md     # What Monica must provide (server PR)
│   └── android-auth.md      # Session/sign-in surface, manifest, screen states
├── checklists/requirements.md
└── tasks.md                 # /speckit-tasks
```

### Source Code

App (this repository, on top of the welcome screen):

```text
app/src/main/
├── AndroidManifest.xml                      # singleTask, callback intent filter, network config, <queries>
├── res/xml/network_security_config.xml      # cleartext off, system CAs only
├── res/xml/data_extraction_rules.xml        # + exclude auth DataStore
├── res/xml/backup_rules.xml                 # + exclude auth DataStore
├── res/values{,-fr}/strings.xml
└── java/com/monicahq/android/
    ├── MainActivity.kt                      # callback intent, Custom Tab launch, root switch
    ├── MonicaApplication.kt                 # + SessionManager, SignInManager, InstanceChecker, HttpClients
    ├── instance/
    │   ├── Instance.kt                      # normalizeInstanceUrl(input, allowHttp), origin helpers
    │   ├── InstanceRepository.kt            # + configuration cache
    │   ├── InstanceConfiguration.kt
    │   └── InstanceChecker.kt               # status + metadata, validation → CheckResult
    ├── network/
    │   ├── HttpClients.kt                   # OkHttp builders: no redirects, timeouts
    │   └── ApiClient.kt                     # bound to instance + generation; auth interceptor/authenticator
    ├── auth/
    │   ├── Pkce.kt                          # verifier, S256 challenge, state
    │   ├── AuthorizationRequest.kt          # URL building, callback parsing
    │   ├── SignInManager.kt
    │   ├── SessionManager.kt                # state, mutex, generation, refresh, logout
    │   ├── TokenEndpointClient.kt           # token + revoke calls, error mapping
    │   ├── AuthStore.kt                     # encrypted DataStore: session, pending, revocation
    │   ├── TokenCipher.kt                   # interface + KeystoreTokenCipher
    │   └── AuthError.kt
    ├── welcome/                             # WelcomeViewModel/Screen/InstanceSheet extended
    └── home/
        ├── HomeScreen.kt
        └── HomeViewModel.kt
app/src/debug/res/xml/network_security_config.xml   # cleartext + user CAs, debug only
app/src/test/java/com/monicahq/android/{instance,auth,network,welcome,home}/   # JVM tests + fakes
app/src/androidTest/java/com/monicahq/android/{welcome,home,auth}/             # UI, E2E, Keystore
```

Server (`../monica`, separate PR, listed here for the dependency):

```text
app/Http/Middleware/EnsureApiTokenIsAccepted.php     # Passport token needs `api`
app/Http/Middleware/BindOAuthScopesToClients.php     # api ↔ mobile client, forced consent, prompt=create
app/Http/Middleware/TrackMobileSessions.php          # on passport.token: create/rotate/replay
app/Http/Controllers/OAuth/RevokeController.php      # RFC 7009
app/Http/Controllers/OAuth/AuthorizationServerMetadataController.php
app/Models/MobileSession.php, app/Actions/EndMobileSession.php
app/Support/MobileClient.php                         # fixed client id, redirect URI
database/migrations/…_create_mobile_sessions_table.php
database/migrations/…_provision_mobile_oauth_client.php
routes/api.php (auth:sanctum,api), routes/ai.php (metadata before Mcp::oauthRoutes), routes/web.php
resources/views/app/auth/oauth-authorize… (mobile wording), settings security section
lang/*.json, resources/docs/api (status, OAuth), changelog
tests/Feature/…                                      # PHPUnit
```

**Structure Decision**: The app stays a single `:app` module, organised by feature package:
`instance`, `auth`, `network`, `home`. The server half follows Monica's existing layout and is
planned in detail by Monica's own Spec Kit run.

## Delivery order

1. **Monica PR** (blocking): R1–R15, from `contracts/monica-server.md`.
2. **App, foundations**:
   - HTTPS-only addresses (R23);
   - network clients (R22);
   - instance checker (R10) wired into the sheet (User Story 2: check before save).
   This step can be built and tested against MockWebServer before the Monica PR is merged.
3. **App, sign-in**:
   - PKCE and pending sign-in (R19);
   - encrypted storage (R20);
   - session manager (R21);
   - home placeholder (User Stories 1 and 2).
4. **App, lifecycle**:
   - renewal and error classification (User Story 3);
   - logout, deferred revocation and instance switch (User Story 4).
5. **App, sign up** (User Story 5). User Story 6 is entirely in the Monica PR.

## Complexity Tracking

| Addition | Why needed | Simpler alternative rejected because |
|-----------|------------|-------------------------------------|
| OkHttp | Turning redirects off, origin-checked auth headers, single-flight renewal on `401`, typed TLS errors and timeouts (FR-027 to FR-029, FR-035) | `HttpURLConnection` follows redirects by default, makes per-request auth hooks awkward and has no test server |
| mockwebserver3 (tests) | Playing a whole Monica in JVM tests, including untrusted TLS, redirects and concurrent `401`s | Hand-written fakes cannot exercise the real HTTP and TLS stack, which is where credential leaks happen |
| kotlinx-serialization (+ plugin) | Parsing the status, metadata, token and `/me` JSON; serialising the encrypted session | `org.json` is stubbed in JVM tests; hand parsing is error-prone |
| androidx.browser | Custom Tabs, the RFC 8252 recommended browser for in-app OAuth | Plain `ACTION_VIEW` stays as the fallback only: it leaves the app's task, so returning feels broken |
| `TokenCipher` interface | Lets JVM tests run without the Keystore, which only exists on a device | A concrete class cannot be tested on the JVM |
| Server: `mobile_sessions` table + token middleware | Gives each phone a stable session, which per-device revocation and replay detection need (FR-019, FR-022, FR-023a) | Passport's tables give each token a new row at every renewal, and Passport's events don't link a renewal to its session |
