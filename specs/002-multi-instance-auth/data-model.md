# Data Model: Secure Multi-Instance Sign In

## App (Android)

### Instance (extended from 001-welcome-screen)

| Field | Type | Rules |
|---|---|---|
| `url` | String | Normalised base address (`normalizeInstanceUrl`): `https` only in release, `http` allowed in debug; lowercase scheme and host; no trailing slash; no user info, query or fragment; port and path kept. It is the instance's identity for isolation. Stored in the existing `settings` DataStore (`instance_url`). Default `https://app.monicahq.com`. |

### InstanceConfiguration (result of discovery, memory + `settings` DataStore cache)

| Field | Source | Rules |
|---|---|---|
| `issuer` | metadata `issuer` | MUST equal `Instance.url` (an `mcp.authorization_server` set elsewhere is refused) |
| `authorizationEndpoint` | metadata | same origin as `issuer`, path under the base path |
| `tokenEndpoint` | metadata | same |
| `revocationEndpoint` | metadata | same; nullable (logout then only clears locally) |
| `codeChallengeMethods` | metadata | MUST contain `S256` |
| `clientId` | status `mobile.client_id` | non-empty |
| `scope` | status `mobile.scope` | `api` |
| `registrationEnabled` | status `mobile.registration_enabled` | drives "Sign up" |
| `apiVersion` | status `mobile.api_version` | MUST be ≥ 1 |
| `minimumAndroidVersion` | status `mobile.minimum_android_version` | nullable; app refuses the instance when its own version is lower |
| `monicaVersion`, `instanceName` | status `version`, `instance` | shown, not trusted for anything |

Rediscovered at app start (in the background, the cache is used meanwhile) and before every
sign-in. Cleared on instance change.

### PendingSignIn (encrypted, `auth` DataStore, key `pending`)

| Field | Rules |
|---|---|
| `issuer`, `clientId`, `redirectUri`, `tokenEndpoint` | copied from the configuration at start; the callback is only exchanged with this `tokenEndpoint` |
| `state` | 32 random bytes, base64url, `SecureRandom` |
| `codeVerifier` | 64 random bytes base64url (86 chars, within RFC 7636's 43–128) |
| `createdAt` | wall clock ms; expires after 10 minutes |
| `intent` | `LogIn` or `SignUp` (`prompt=create`) |

State transitions: `none → pending` (Log in / Sign up) → `none` on callback accepted, callback
rejected, cancel (resume without callback), expiry, instance change. At most one.

### Session (encrypted, `auth` DataStore, key `session`, one value)

| Field | Rules |
|---|---|
| `issuer` | instance URL that issued it; tokens are only sent to this origin |
| `tokenEndpoint`, `revocationEndpoint`, `clientId` | from the configuration used at sign-in |
| `accessToken`, `accessTokenExpiresAt` | `expiresAt = receivedAt + expires_in − 60 s` |
| `refreshToken`, `refreshTokenObtainedAt` | `obtainedAt` used to tell "expired" (≥ 30 days) from "revoked" |
| `user` | `id`, `name`, `email` from `GET /api/v1/me` |

`toString()` hides tokens. Replaced as a whole on renewal.

### SessionState (in memory, `SessionManager.state`)

```
SignedOut(reason: null | Expired | Revoked | LoggedOut)
   │ sign-in completed (+ profile loaded)
   ▼
SignedIn(session)
   │ renewal ok → SignedIn(new session)        (generation unchanged)
   │ invalid_grant → SignedOut(Expired|Revoked) (generation + 1)
   │ logout / instance change → SignedOut(LoggedOut) (generation + 1)
```

Network errors never change the state.

### PendingRevocation (encrypted, `auth` DataStore, key `revocation`)

`revocationEndpoint`, `clientId`, `refreshToken`, `createdAt`. Retried at app start, dropped after
success, a non-network answer, or 30 days.

## Server (Monica)

### oauth_clients: the mobile client (row, no schema change)

| Column | Value |
|---|---|
| `id` | fixed UUID constant in Monica (`MobileClient::ID`) |
| `name` | `Monica for mobile` |
| `secret` | null (public client) |
| `redirect_uris` | `["com.monicahq.android:/oauth/callback"]` |
| `grant_types` | `["authorization_code","refresh_token"]` |
| `revoked` | false |

Inserted by a migration with insert-if-missing on `id`.

### mobile_sessions (new table)

| Column | Type | Rules |
|---|---|---|
| `id` | ULID, primary | used by the web settings to end a session |
| `user_id` | FK users, cascade | |
| `oauth_client_id` | FK oauth_clients | the mobile client |
| `device_name` | string(255), nullable | sanitised `device_name` from the code exchange |
| `access_token_id` | string(80), indexed | `jti` of the current access token |
| `refresh_token_hash` | char(64), unique | SHA-256 hex of the current refresh token |
| `previous_refresh_token_hash` | char(64), nullable, indexed | replay detection |
| `authorization_code_hash` | char(64), indexed | replay detection |
| `last_used_at` | datetime | set at creation and at every renewal |
| `revoked_at` | datetime, nullable | set when ended |
| `created_at`, `updated_at` | | |

Ending a session: revoke the access token `access_token_id` and its refresh token, set `revoked_at`.
Active list = `revoked_at is null` and `last_used_at` within the refresh token lifetime (30 days).
Rows ended or older than 30 days are pruned by the existing scheduled pruning (`model:prune`).

### UserActionEnum (2 new cases)

`MobileSessionCreation` (device name), `MobileSessionEnd` (device name, who ended it: phone or web).
