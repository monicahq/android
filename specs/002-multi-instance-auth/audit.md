# Audit: authentication today (2026-11-26)

What exists in both repositories before this feature, verified against the source. The spec builds
on it; the plan decides how.

## Monica server (`../monica`, commit 731771982)

| Topic | What exists | Where |
|---|---|---|
| Packages | `laravel/passport` ^13.8, `laravel/sanctum` ^4.3, `laravel/mcp` ^1.0, `laravel/socialite`, `pragmarx/google2fa-laravel` | `composer.json` |
| Guards | `web` = session; `api` = **passport** (active users only) | `config/auth.php` |
| JSON API `/api/v1` | Protected by `auth:sanctum` only. Passport tokens are **not** accepted today | `routes/api.php:175` |
| API login | `POST /api/v1/login` takes email + password + optional TOTP code, returns a never-expiring Sanctum key (`expires_at: null`, scopes `*`). `DELETE /api/v1/logout` deletes the current key. Throttled 6/min. Password grant in all but name; the app will not use it but other consumers may | `Api/Auth/LoginController.php` |
| API registration | `POST /api/v1/register`, behind the `registration` middleware (`MONICA_REGISTRATION_ENABLED`) | `routes/api.php` |
| Instance status | `GET /api/v1/status` → `{status, version, instance}` (instance = host of `APP_URL` or `MONICA_INSTANCE`) | `Api/StatusController.php`, `config/monica.php:320` |
| Passport purpose | "exists only so an assistant can reach the MCP server through OAuth". Access tokens 1 day, refresh tokens 30 days, global for every client. Device code grant off. Only scope: `mcp:use` | `AppServiceProvider::configurePassport` |
| OAuth discovery | RFC 8414 `/.well-known/oauth-authorization-server` (and path-suffixed variant) and RFC 9728 `/.well-known/oauth-protected-resource`, registered by `Mcp::oauthRoutes()`, throttled 10/min. Advertises `code`, `S256`, `authorization_code` + `refresh_token`, `token_endpoint_auth_methods: none` …, scopes `[mcp:use]`. No revocation endpoint | `routes/ai.php`, `vendor/laravel/mcp/src/Server/Registrar.php` |
| Client registration | RFC 7591-style `POST /oauth/register` creates a **public** authorization-code client, grants only `mcp:use`. Custom schemes only if listed in `mcp.custom_schemes`; https redirects only for `mcp.redirect_domains` | `vendor/laravel/mcp/.../OAuthRegisterController.php` |
| Authorization screen | Passport authorize page rendered by `app.auth.oauth-authorize`, **404 unless the account has the `mcp-server` flag on**. Wording is about assistants | `AppServiceProvider`, `OAuthAuthorizationViewModel` |
| Revocation | Web settings → security → connected assistants: `DisconnectAssistant` revokes **every** token of a client for the user (would sign out every phone at once if all phones share one client) | `Actions/DisconnectAssistant.php`, `routes/web.php:969` |
| MCP | `/mcp` accepts `auth:sanctum,api`; a Passport token must carry `mcp:use` | `routes/ai.php`, `EnsureMcpConnectionIsAccepted` |
| Web sign in | Password, TOTP two-factor challenge, security keys / passkeys, magic link (optional), SSO via Socialite/OIDC, Turnstile, password reset, email verification. All on the web session, so the OAuth authorize page reuses them as-is | `routes/auth.php` |
| Registration toggle | `MONICA_REGISTRATION_ENABLED` (default true), plus a preview / waiting list | `config/monica.php:67` |
| Setup / upgrade | `SetupApplicationCommand` runs migrations and generates Passport keys once (`passport:keys`), never replaces them. OAuth tables exist since migrations `2026_09_25_*` | `Console/Commands/SetupApplicationCommand.php` |
| HTTPS | `URL::forceScheme('https')` outside local/testing; trusted proxies + Cloudflare ranges | `AppServiceProvider` |
| Subdirectory | No explicit support or documentation found. URLs are built from `APP_URL`, so metadata follows it if the server is served under a path | — |

**Conclusion**: Passport already provides authorization code + PKCE S256, refresh tokens with
rotation, RFC 8414 metadata, public clients and the web sign-in flow (including two-factor). No new
authentication system or dependency is needed. Gaps to close in Monica: `/api/v1` must accept
OAuth tokens with an API scope; a first-party mobile client must be provisioned idempotently on every
instance; discovery must expose it (and what the app needs to know about the instance); the
authorization screen must not depend on the MCP flag and must speak about the app; per-session
revocation; access token lifetime for mobile.

## Monica Android (this repo)

`main` (062f606) holds only the template app. The unmerged branch `2026-11-25-welcome-screen` adds:

| Topic | What exists |
|---|---|
| Architecture | Compose + Material 3, one `ViewModel` with `StateFlow<UiState>`, manual wiring in `MonicaApplication` (no Hilt yet) |
| Navigation / deep links | None. Single `MainActivity`, launcher intent filter only |
| Networking | None (no HTTP client library) |
| Persistence | Preferences DataStore holds `instance_url` (`InstanceRepository`); default `https://app.monicahq.com` |
| Instance address | `normalizeInstanceUrl`: adds `https://` when missing, trims trailing slash, keeps port and path, **accepts plain `http`** |
| Auth / secure storage | None |
| Backups | `allowBackup="true"`; data extraction rules include everything (no exclusions) |
| Tests | JVM tests with fakes and `MainDispatcherRule`; Compose UI tests with accessibility checks |
