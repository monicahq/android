# Research: Secure Multi-Instance Sign In

Decisions taken while planning. Each one: decision, rationale, alternatives rejected. Facts about
today's code are in [audit.md](audit.md); the extra checks made for this plan are listed in R0.

## R0. Extra facts verified in Monica for this plan

- Passport's `AuthorizationController` approves **without showing consent** when the client
  `skipsAuthorization()` (false by default) **or** the person already holds a valid token for that
  client and scopes (`hasGrantedScopes`), unless the request carries `prompt=consent`.
- Passport sets one lifetime per grant (`Passport::tokensExpireIn()` for authorization code and
  refresh grants), not per client.
- `oauth_clients` has no `scopes` column in Monica, so today **any client may request any scope**
  registered with `Passport::tokensCan`.
- league/oauth2-server: PKCE is required for public clients by default; refresh tokens rotate
  (`revokeRefreshTokens = true`); a reused authorization code is refused but the tokens it issued are
  **not** revoked. Passport has no RFC 7009 revocation endpoint usable by a public client.
- `oauth_access_tokens` has no device name nor last-used date, and every renewal creates a new row,
  so there is no stable "session of one phone" today.
- API controllers grant `sensitive-fields` / `administration` only to a `PersonalAccessToken`
  (`instanceof` checks). Any other token gets neither.
- `api` rate limiter: 60/min per user or IP. `/oauth/token` uses Passport's default `throttle`;
  `/oauth/authorize` has none.
- Web registration does not redirect to the "intended" URL, so a sign-up started from an
  authorization request would not come back to it.

## Server (Monica)

### R1. Reuse Passport, open it to the app

- **Decision**: extend the Passport authorization server Monica already runs for assistants. Add an
  `api` scope; let `/api/v1` accept Passport tokens carrying `api` (`auth:sanctum,api` plus a
  middleware that refuses a Passport token without `api`, mirroring `EnsureMcpConnectionIsAccepted`).
- **Rationale**: it already provides authorization code + PKCE S256, refresh rotation, the web
  sign-in with two-factor, passkeys and SSO, keys generated at setup. Nothing new to install.
- **Alternatives**: a custom flow ending in a Sanctum key (non-standard, re-implements OAuth, keys
  never expire); the existing password login `POST /api/v1/login` (password grant in disguise,
  forbidden by the spec and RFC 9700); a new OpenID Connect server package (no need for ID tokens:
  `/api/v1/me` gives the profile).

### R2. Scopes are bound to clients

- **Decision**: a middleware on `passport.authorizations.authorize` and `passport.token` refuses
  (`invalid_scope`) the `api` scope for any client other than the mobile client, and `mcp:use` for the
  mobile client. The mobile client asks for `api` only.
- **Rationale**: without it, an assistant registered dynamically could ask for `api` and reach the
  whole JSON API, and the app could reach MCP. The rule lives in one place.
- **Alternatives**: adding a `scopes` column to clients and patching Passport's scope validation
  (more code inside vendor behaviour); trusting consent alone (an assistant should never get `api`).

### R3. Mobile tokens get the base API access

- **Decision**: a token with `api` has the same rights as an API key **without** extra scopes: no
  `sensitive-fields`, no `administration`. Controllers stay unchanged.
- **Rationale**: simplest; the controllers' `instanceof PersonalAccessToken` checks already deny those
  scopes to other tokens. `sensitive-fields` is described in Monica as "leave this off unless a device
  needs them". A later feature that needs them adds a scope to the mobile client then.
- **Alternatives**: mapping `api` to every scope (touches ten controllers now, for no screen that
  needs it yet).

### R4. One provisioned first-party client per instance

- **Decision**: a migration inserts the mobile client with a **fixed id** defined in Monica's code
  (one constant UUID, the same on every instance), name "Monica for mobile", no secret (public),
  grants `authorization_code` + `refresh_token`, redirect URIs `["com.monicahq.android:/oauth/callback"]`.
  Insert-if-missing (`insertOrIgnore` on the id), so re-running is harmless; migrations run on install
  and on every upgrade through `monica:setup`. Discovery publishes the id; the app never hard-codes it.
- **Rationale**: zero manual step, idempotent by construction, no client per phone in the database.
  A fixed id makes support and tests predictable; it is not a secret.
- **Alternatives**: dynamic client registration per phone (already exists for MCP, but creates one
  client per install, needs redirect-scheme configuration on every instance, and leaves orphans); a
  random id per instance (works too, but needs a lookup marker for idempotency and gains nothing);
  an Artisan command (manual step, forbidden by FR-015).

### R5. Return address: private reverse-domain scheme

- **Decision**: `com.monicahq.android:/oauth/callback` (clarification Q1). Exact match.
- **Rationale**: RFC 8252 §7.1; works on every instance without any third party. Interception by
  another app claiming the scheme is neutralised by PKCE (the interceptor lacks the verifier) and by
  forcing consent (R6).
- **Alternatives**: App Links on a Monica domain (rejected in clarification: third-party domain on
  self-hosted instances, code may transit through Monica's server); loopback redirect (not
  available to Android apps in practice).

### R6. Consent at every sign-in of the app

- **Decision**: the scope middleware (R2) adds `prompt=consent` to authorization requests of the mobile
  client. The authorization view allows the mobile client whatever the `mcp-server` flag, and shows
  wording for the app ("Monica for Android wants to access your account on {instance}"), translated.
  MCP clients keep today's flag check and wording.
- **Rationale**: RFC 8252 §8.6: no silent approval for a public client with a custom scheme. Passport
  would otherwise auto-approve via `hasGrantedScopes`.

### R7. Short access tokens for everyone

- **Decision**: `Passport::tokensExpireIn(1 hour)` (was 1 day). Refresh tokens stay 30 days and
  rotate.
- **Rationale**: Passport has one lifetime per grant. Assistants already renew with their refresh
  token, so one hour costs them one renewal per hour of use and strengthens revocation for them too.
- **Alternatives**: a per-client lifetime (custom grant or token repository: more code inside
  Passport internals for no real gain).

### R8. Phone sessions (`mobile_sessions`)

- **Decision**: new table `mobile_sessions`, one row per phone sign-in, maintained by a middleware on
  `passport.token` that only acts for the mobile client:
  - code exchange succeeds → create the row: user, client, device name (`device_name` request
    parameter, sanitised, optional), current access token id (the `jti` of the JWT just issued),
    SHA-256 of the refresh token, SHA-256 of the authorization code;
  - refresh succeeds → find the row by the hash of the presented refresh token, replace access token
    id and refresh hash, keep the previous hash, set `last_used_at`;
  - presented authorization code whose hash is already on a row → **revoke that session** (code
    replay, RFC 9700 §4.2.4), then let Passport refuse the code;
  - presented refresh token whose hash is a row's *previous* hash → **revoke that session** (refresh
    token replay, RFC 9700 §4.14.2).
  Ending a session = revoking its current access token and refresh token through Passport models and
  setting `revoked_at`. Only hashes are stored, never tokens or codes.
- **Rationale**: a stable handle per phone, needed for per-device revocation (FR-019, FR-023a) and
  replay detection, without modifying Passport. "Last used" = last renewal, accurate to the hour.
- **Alternatives**: Passport events (`AccessTokenCreated` does not say which session a renewal
  belongs to); decrypting league payloads to read ids (couples Monica to league's crypto); updating
  `last_used_at` on every API request (a write per request).

### R9. Revocation: RFC 7009 endpoint

- **Decision**: `POST /oauth/revoke` (`token`, `token_type_hint`, `client_id`), public client, no
  authentication. Accepts the refresh token: ends the matching mobile session (access + refresh).
  Answers `200` for unknown or already revoked tokens (RFC 7009 §2.2). Throttled. Published as
  `revocation_endpoint`. `DELETE /api/v1/logout` is fixed to end the mobile session when called with a
  Passport token (today it assumes a Sanctum key).
- **Rationale**: standard; works while the access token is expired (the app revokes with the refresh
  token), so logout and deferred revocation do not need a renewal first. Ends this phone only.
- **Alternatives**: only `DELETE /api/v1/logout` (needs a valid access token: logout while expired
  would first need a renewal).

### R10. Discovery: RFC 8414 + Monica's status

- **Decision**: the app reads two documents under the instance address it was given:
  1. `GET {base}/api/v1/status` (exists): extended with a `mobile` object: `api_version` (1),
     `client_id`, `scope` (`api`), `registration_enabled`, `minimum_android_version` (nullable). Its
     absence means "this Monica is too old for the app".
  2. `GET {base}/.well-known/oauth-authorization-server`: Monica defines this route itself, before
     `Mcp::oauthRoutes()` (which then skips its own), with the standard members plus
     `revocation_endpoint`, `revocation_endpoint_auth_methods_supported: ["none"]`,
     `scopes_supported: ["mcp:use"]` (`api` left out, since an assistant asking for every scope
     listed would be refused; the app reads the scope from the status), `prompt_values_supported`
     including `create`. MCP clients keep reading the same document with every member they rely on.
  The app requires `issuer == base` and every endpoint on the same origin and under the base path.
  Monica sets `issuer` from `mcp.authorization_server` when an administrator configures it; such an
  instance is refused as `NoAppSignIn`, a documented limit.
- **Rationale**: RFC 8414 for OAuth endpoints; Monica's existing status endpoint for what OAuth
  metadata cannot say. Under a subdirectory (`https://example.com/monica`), Laravel builds every URL
  from `APP_URL`, so `issuer` and endpoints carry `/monica`; the app reads the metadata under the
  base rather than at the host root (RFC 8414 §3 would put it at the root, which a Monica in a
  subdirectory cannot serve). Documented as Monica's discovery location.
- **Alternatives**: a single proprietary `/.well-known/monica` document (less standard); RFC 9728
  protected resource metadata (adds a third request for nothing the app needs).

### R11. Mix-up attacks

- **Decision**: one pending sign-in at a time, bound to issuer, client id, redirect URI, state and
  verifier; the code is only ever sent to the token endpoint discovered from that same issuer; any
  instance change clears the pending sign-in. The app checks `iss` in the callback when present
  (RFC 9207) but Monica does not need to send it in this feature.
- **Rationale**: mix-up needs a client talking to several authorization servers at once; the app
  never has two pending. Adding `iss` to Passport's redirect would mean overriding its response.

### R12. Sign up through the same flow

- **Decision**: "Sign up" opens the authorization request with `prompt=create` (OpenID Connect Prompt
  Create). On Monica, a guest reaching the authorization endpoint with `prompt=create` is sent to the
  registration page instead of the login page; registration and email verification redirect to the
  intended URL, so the person lands on consent and the app gets its code. When
  `registration_enabled` is false, the app does not open the browser and explains why.
- **Rationale**: one flow, no new endpoint, standard parameter.

### R13. Rate limits

- **Decision**: `throttle:30,1` per IP on `passport.authorizations.authorize` and `oauth/revoke`;
  `/oauth/token` keeps Passport's throttle; discovery keeps 60/min (`status`) and 10/min (metadata,
  from `routes/ai.php`): Monica's own metadata route gets 60/min, since the app reads it at each check.

### R14. Account log

- **Decision**: two new `UserActionEnum` cases, `MobileSessionCreation` and `MobileSessionEnd`, with
  the device name, written like `AssistantDisconnection`.

### R15. Web: phone sessions in security settings

- **Decision**: a "Mobile apps" section in Settings → Account → Security (next to connected assistants
  and API keys) listing active `mobile_sessions` (device name or "Unnamed device", signed in, last
  used) with an "End session" action (`EndMobileSession`). The connected-assistants list excludes
  the mobile client, so `DisconnectAssistant` can never sign out every phone at once.

### R16. Monica work is specified in Monica

- **Decision**: the server part (R1–R15) is delivered as its own pull request in `monicahq/monica`,
  specified there with Monica's Spec Kit (`monica:speckit-specify`) from
  [contracts/monica-server.md](contracts/monica-server.md), and merged before the app part. Monica's
  definition of done (API docs, changelog, translations) applies to it.
- **Rationale**: CLAUDE.md: a missing API capability is added to Monica first; each repository keeps
  its own conventions and CI.

## App (Android)

### R17. HTTP: OkHttp + kotlinx.serialization, no Retrofit

- **Decision**: add OkHttp (client), `mockwebserver3` (tests), kotlinx-serialization-json with the
  Kotlin serialization compiler plugin. No Retrofit for now.
- **Rationale**: OkHttp gives what the security rules need: redirects off, per-client
  interceptors, typed TLS errors, timeouts; MockWebServer lets JVM tests play a whole Monica,
  including TLS with an untrusted certificate. kotlinx.serialization is the Kotlin-native JSON
  library and works in JVM tests (`org.json` is stubbed there). Four endpoints do not justify
  Retrofit.
- **Alternatives**: `HttpURLConnection` (follows redirects across hosts by default, poor testing
  story); Ktor client (multiplatform-oriented, heavier); Retrofit (comes with the first real
  feature screen if useful).

### R18. Browser: Custom Tabs (androidx.browser), no AppAuth

- **Decision**: open the authorization URL in a Custom Tab (`androidx.browser`), falling back to the
  default browser through `ACTION_VIEW` when no browser supports Custom Tabs. PKCE, state, URL
  building and callback checks are written in the app (small, pure Kotlin, unit tested).
- **Rationale**: Custom Tabs are the RFC 8252 recommended UA on Android and keep the person in the
  app's task. The OAuth logic is ~200 lines that must also enforce Monica-specific discovery rules.
- **Alternatives**: AppAuth-Android (generic discovery that does not know Monica's status document,
  own state persistence and activity, low maintenance activity; would still need the same checks
  around it); a WebView (forbidden).

### R19. Callback handling and process death

- **Decision**: `MainActivity` becomes `singleTask` with an intent filter for scheme
  `com.monicahq.android`, path `/oauth/callback`. The pending sign-in (issuer, client id, redirect
  URI, state, verifier, created-at) is stored **encrypted** before the browser opens, so a callback
  received by a recreated process completes normally. Callback parsing returns `Code`,
  `Denied` (`error=access_denied`), `Error`, or `Rejected` (no pending, state mismatch, expired after
  1 hour, already used). On `onResume` without a callback, the welcome screen says the sign-in was
  cancelled (or, after Sign up, how to finish it), but the pending sign-in is kept: the person may
  have come back to wait for the confirmation email, whose link brings the answer later.
- **Rationale**: `singleTask` brings the existing activity back and closes the Custom Tab;
  persistence survives process death (FR-006).

### R20. Secure storage: Keystore AES-GCM + DataStore

- **Decision**: one non-exportable AES-256-GCM key in the Android Keystore; the session (and the
  pending sign-in) are serialised to JSON, encrypted with a fresh IV, and stored as **one** value in a
  dedicated DataStore file `auth.preferences_pb`. That file is excluded from cloud backup and device
  transfer. A value that cannot be decrypted (restored backup, key lost) is deleted and treated as
  "signed out".
- **Rationale**: one value per write makes replacement atomic (DataStore writes the whole file
  atomically, FR-026). Jetpack Security's `EncryptedSharedPreferences` is deprecated; Tink is a large
  dependency for one key.
- **Testing**: the cipher sits behind a small `TokenCipher` interface (needed for JVM tests: real
  Keystore exists only on a device), with an instrumented test for the Keystore implementation.

### R21. Session manager and single-flight renewal

- **Decision**: one `SessionManager` (held by `MonicaApplication`) owns the session `StateFlow`
  (`SignedOut`, `SignedIn(session)`), a `Mutex` and a **generation** number incremented at every
  sign-in, sign-out and instance switch.
  - Renewal: `refresh(staleAccessToken)` takes the mutex; if the stored access token already differs
    from the stale one, it returns the current one (someone else renewed); otherwise it calls the
    token endpoint once, and stores the result only if the generation did not change meanwhile.
  - Expiry: renew before a request when the stored expiry (from `expires_in`, minus 60 s) has passed;
    and react to `401` from the session's own origin on a request that carried the current token,
    once per request (OkHttp `Authenticator`, `priorResponse == null`).
  - Outcomes: network error/timeout → keep the session, report "offline"; `invalid_grant` → end the
    session locally, reason `Expired` if the refresh token's 30 days have passed, else `Revoked`;
    `403` → "not allowed", no renewal; `5xx` → "server error", keep the session.
- **Rationale**: FR-024 to FR-028 with one lock and one counter; testable on the JVM with
  `runTest` and MockWebServer.

### R22. One HTTP client per instance; credentials stay at home

- **Decision**: an `ApiClient` is built for one instance and one generation. Its interceptor adds
  `Authorization` only when the request's origin (scheme, host, port) equals the session issuer's
  origin, and only for the generation it was built for. `followRedirects(false)` and
  `followSslRedirects(false)` on every client: a `3xx` is an error ("your instance moved"). Switching
  instance drops the old `ApiClient`, cancels its calls (`dispatcher.cancelAll()`), and results
  carrying an old generation are discarded.
- **Rationale**: FR-029, FR-030, SC-005, enforced at the lowest layer, so no caller can leak.

### R23. HTTPS only in release, development escape hatch in debug only

- **Decision**: `normalizeInstanceUrl(input, allowHttp)` accepts `http` only when `allowHttp` is true,
  passed as `BuildConfig.DEBUG`. `res/xml/network_security_config.xml` (main) forbids cleartext and
  trusts system CAs only; `src/debug/res/xml/network_security_config.xml` overrides it to allow
  cleartext and user-installed CAs. No custom `TrustManager` anywhere. A saved `http://` address in a
  release build is shown as invalid with the "needs HTTPS" message and must be corrected.
- **Rationale**: FR-031 (clarification Q2). Build-type source sets cannot leak into release.

### R24. Error classification

- **Decision**: one sealed `AuthError` type mapped from exceptions and answers:
  `InvalidAddress`, `HttpsRequired`, `Unreachable` (`UnknownHostException`, `ConnectException`),
  `Timeout` (`SocketTimeoutException`, `InterruptedIOException`), `Certificate`
  (`SSLHandshakeException`, `SSLPeerUnverifiedException`), `Moved` (3xx), `NotMonica` (no/invalid
  status JSON), `MonicaTooOld` (no `mobile` object or `api_version` < 1), `AppTooOld`,
  `NoAppSignIn` (missing S256, endpoints off-origin, issuer mismatch), `Cancelled`, `Denied`,
  `SignInFailed`, `SessionExpired`, `SessionRevoked`, `NotAllowed`, `ServerError`. Each maps to one
  string resource; exception messages are never shown nor logged.

### R25. Navigation without a library

- **Decision**: a root composable switches on the session state: welcome (signed out, with sign-in
  progress and errors) or a placeholder home (signed in: name, instance, "Log out"). No Navigation
  Compose yet.
- **Rationale**: two destinations driven by state; Navigation comes with the first real feature.

### R26. Deferred revocation

- **Decision**: logout (or switch) clears the session immediately, then calls the revocation
  endpoint. If that fails for network reasons, the refresh token and the revocation endpoint are kept
  encrypted as a "pending revocation" and retried at the next app start; given up after 30 days (the
  token is dead by then).
- **Rationale**: FR-019 / User Story 4 scenario 2 without WorkManager.
- **Alternatives**: WorkManager with a network constraint (a new library for a best-effort call).

### R27. No secrets in logs

- **Decision**: no HTTP logging interceptor in any build; no `Log` call with request or response
  bodies; `Session`, `PendingSignIn` and token responses override `toString()` to hide secrets.
  The app has no analytics or crash reporter; their future addition must respect FR-033.

### R28. Device name sent to Monica

- **Decision**: the code exchange sends `device_name` = `Settings.Global.DEVICE_NAME`, falling back to
  `Build.MODEL`, so the web list can tell phones apart.
