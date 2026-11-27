# Contract: what Monica provides to the mobile app

The server half of this feature. It is specified and delivered in `monicahq/monica` (research R16):
merged on 2026-11-26 as monicahq/monica#8053 (`3a4ba252f`), specified there as
`specs/165-mobile-app-sign-in`. This file states what the app relies on, as merged.
`{base}` is the instance's `APP_URL`, which may include a path.

## 1. `GET {base}/api/v1/status` (existing, extended)

Public, `throttle:60,1`. New `mobile` member; existing members unchanged.

```json
{
  "status": "ok",
  "version": "5.3.0",
  "instance": "monica.example.com",
  "mobile": {
    "api_version": 1,
    "client_id": "<MobileSession::CLIENT_ID>",
    "scope": "api",
    "registration_enabled": true,
    "minimum_android_version": null
  }
}
```

- `mobile` absent → the app reports "this Monica is too old".
- `minimum_android_version`: semantic version string or null, from `MONICA_MINIMUM_ANDROID_VERSION`.
- `registration_enabled` mirrors `config('monica.registration_enabled')`.
- `client_id` is the same constant on every instance today. The app still reads it from here and
  never hard-codes it.
- `scope` is where the app learns the scope to request (`api`): it is not in `scopes_supported`.

## 2. `GET {base}/.well-known/oauth-authorization-server` (redefined by Monica)

Public, `throttle:60,1`. Registered before `Mcp::oauthRoutes()`, which then skips its own route.

```json
{
  "issuer": "{base}",
  "authorization_endpoint": "{base}/oauth/authorize",
  "token_endpoint": "{base}/oauth/token",
  "revocation_endpoint": "{base}/oauth/revoke",
  "revocation_endpoint_auth_methods_supported": ["none"],
  "registration_endpoint": "{base}/oauth/register",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code", "refresh_token"],
  "code_challenge_methods_supported": ["S256"],
  "token_endpoint_auth_methods_supported": ["none", "client_secret_basic", "client_secret_post"],
  "scopes_supported": ["mcp:use"],
  "prompt_values_supported": ["none", "login", "consent", "create"]
}
```

Every member the MCP document had keeps its value (assistants keep working). The
`/.well-known/oauth-authorization-server/{path}` variant answers the same document.

- `api` is left out of `scopes_supported` on purpose: an assistant asking for every scope listed
  would be refused. The app takes the scope from the status (§1).
- `issuer` is `config('mcp.authorization_server')` when an administrator sets it, `{base}`
  otherwise. The app keeps requiring `issuer == base`: an instance whose administrator points it
  elsewhere is refused as `NoAppSignIn` (documented limit, not worked around).

## 3. `GET {base}/oauth/authorize` (Passport, wrapped)

Request from the app:

```
response_type=code
client_id=<client_id>
redirect_uri=com.monicahq.android:/oauth/callback
scope=api
state=<random>
code_challenge=<BASE64URL(SHA256(verifier))>
code_challenge_method=S256
prompt=consent            (Log in)   |   prompt=consent create   (Sign up)
```

Rules added by Monica (`throttle:30,1` per IP):

- `api` is refused for any client other than the mobile client; `mcp:use` is refused for the mobile
  client: redirect with `error=invalid_scope`.
- For the mobile client, `consent` is always added to `prompt` (consent at every sign-in).
- For the mobile client, the authorization page does not require the `mcp-server` flag and shows:
  title "Allow Monica for Android to access your account?", the instance name, the signed-in email,
  what it can do ("Read and change everything in your account, like the web app"), "Allow" and
  "Deny". Translated like the rest of Monica.
- Guest with `create` in `prompt` → registration page (or the login page with a message when
  registration is closed); registration and email verification continue to the intended URL.
- Success: `com.monicahq.android:/oauth/callback?code=…&state=…`.
  Denied: `…?error=access_denied&state=…`.

## 4. `POST {base}/oauth/token` (Passport, wrapped)

`application/x-www-form-urlencoded`. Passport's own throttle.

Code exchange: `grant_type=authorization_code`, `client_id`, `redirect_uri`, `code`, `code_verifier`,
`device_name` (optional, ≤ 255).
Renewal: `grant_type=refresh_token`, `client_id`, `refresh_token`, `scope=api`.

Success `200`:

```json
{ "token_type": "Bearer", "expires_in": 3600, "access_token": "…", "refresh_token": "…" }
```

Errors (`400`/`401`, RFC 6749 §5.2): `invalid_grant` (wrong verifier, used/expired code, wrong
redirect URI, revoked/expired/rotated refresh token), `invalid_client`, `invalid_request`,
`invalid_scope`.

Monica additions, for the mobile client only:

- Successful code exchange → creates a `mobile_sessions` row, logs `MobileSessionCreation`.
- Successful renewal → updates the row (new access token id, refresh hash, previous hash,
  `last_used_at`). The old refresh token stops working (rotation).
- The client is read from `client_id` in the body, or else from HTTP Basic authentication.
- Code already exchanged → ends the session it created, then `invalid_grant`.
- Previous refresh token presented again more than 30 seconds after its renewal
  (`MobileSession::REPLAY_GRACE_IN_SECONDS`) → ends the session, then `invalid_grant`. Within those
  30 seconds it is taken as a second request sent at once: `invalid_grant`, and the session goes on.
  The app never sends two renewals at once (FR-027), so an `invalid_grant` on renewal always means
  the session is over.
- Access tokens live 1 hour, refresh tokens 30 days (all clients).

## 5. `POST {base}/oauth/revoke` (new, RFC 7009)

`application/x-www-form-urlencoded`: `token` (refresh token), `token_type_hint=refresh_token`,
`client_id`. No authentication (public client). `throttle:30,1` per IP.

- Matching active session of that client → ends it (access + refresh), logs `MobileSessionEnd`.
- Always `200` with an empty body for a well-formed request, including unknown or already revoked
  tokens. `400 invalid_request` when `token` is missing.

## 6. `/api/v1/*` (existing)

- Accepts `Authorization: Bearer <Passport access token>` carrying the `api` scope, in addition to
  Sanctum API keys. A Passport token without `api` (an assistant's) → `403`.
- A mobile token has the rights of an API key without extra scopes (no `sensitive-fields`, no
  `administration`). It cannot create an API key (`403`: a key would outlive the phone), and the
  administration routes answer `404`.
- `401` with `{"message":"Unauthenticated."}` when the token is missing, expired or revoked.
- `DELETE /api/v1/logout` with a mobile token ends its mobile session (`204`). The app logs out
  through `/oauth/revoke` instead, which works with an expired access token (research R9).
- `GET /api/v1/me` returns the signed-in person (existing).

## 7. Web: Settings → Account → Security

- New "Mobile apps" section: one line per active mobile session (device name or "Unnamed device",
  signed in, last used), "Sign out" with confirmation, which ends that session only.
- "Connected assistants" no longer lists the mobile client.
- The same list and action exist in the API (Monica principle X): `GET /api/v1/mobile-sessions`,
  `GET /api/v1/mobile-sessions/{id}` and `DELETE /api/v1/mobile-sessions/{id}`, in the usual
  `type`/`id`/`attributes`/`links` shape with Unix timestamps; `attributes.is_current` marks the
  phone making the request. Monica's `specs/165-mobile-app-sign-in/contracts/` is the reference.
- No feature flag: everything is live once the Monica PR is merged.
- The app does not use `/api/v1/mobile-sessions` in this feature; it is available for a later
  "devices" screen.

## 8. Unchanged (regression tests)

Web sign-in (password, two-factor, passkeys, magic link, SSO), Sanctum API keys and
`POST /api/v1/login`, MCP over Sanctum keys and over OAuth (`mcp:use`), dynamic client registration.
