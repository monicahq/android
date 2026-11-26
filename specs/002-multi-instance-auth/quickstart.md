# Quickstart: validating Secure Multi-Instance Sign In

How to prove the feature works. Details live in [contracts/](contracts/) and
[data-model.md](data-model.md).

## Prerequisites

- Monica checked out in `../monica` on the branch carrying the server half
  ([contracts/monica-server.md](contracts/monica-server.md)), served over HTTPS (for example
  `https://monica.test` with a locally trusted certificate) and over HTTP for debug-only checks.
- The welcome screen branch merged into this one.
- An emulator (API 28+) with a browser that supports Custom Tabs.

## 1. Server checks (in `../monica`)

```sh
php artisan migrate && php artisan migrate     # second run: still exactly one mobile client
php artisan test --filter=Mobile               # provisioning, scopes, sessions, revoke, replay
php artisan test                               # web, API keys and MCP unchanged (SC-009)
curl -s https://monica.test/api/v1/status | jq .mobile
curl -s https://monica.test/.well-known/oauth-authorization-server | jq .
```

Expected: `mobile.client_id` present; metadata lists `S256`, `revocation_endpoint`, `api` and
`mcp:use`.

## 2. App checks (in this repo)

```sh
./gradlew spotlessApply lintDebug testDebugUnitTest koverVerifyDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

JVM tests play Monica with MockWebServer: discovery accepted/refused cases, PKCE vector from
RFC 7636 Appendix B, wrong state, expired pending sign-in, code exchange, 20 concurrent `401` → 1
renewal, `invalid_grant` → signed out, offline → still signed in, instance switch during a request and
during a renewal → instance B's server records nothing from A, `302` to another host → no
`Authorization` header sent, untrusted certificate → `Certificate` error.

Instrumented end-to-end tests drive `MainActivity` against a MockWebServer Monica and deliver the
browser's return as an intent (the browser itself is not automated), including after the activity
was recreated.

## 3. Manual run against a real Monica

1. Install the debug app, open the instance sheet, enter `monica.test`: "Checking…" then saved.
2. Enter `example.com`: "This is not a Monica instance", previous instance kept.
3. Enter `http://monica.test` in a **release** build: "This instance must use HTTPS".
4. "Log in" → Custom Tab on Monica's login → password + two-factor → consent page naming Monica for
   Android → back in the app, home shows your name and `monica.test`.
5. Kill the app (`adb shell am kill com.monicahq.android`) during step 4 in the browser → finishing
   still signs you in.
6. Reopen the app after a reboot → still signed in, no browser.
7. Airplane mode, wait over an hour (or set Monica's token lifetime to 1 minute) → still signed in,
   offline message; network back → requests work.
8. Web: Settings → Security → Mobile apps lists the phone; "End session" → the app shows "Your
   session was ended" at its next request. A second phone stays signed in.
9. "Log out" in airplane mode → welcome screen at once; network back and app restarted → the web
   list no longer shows the phone.
10. Instance sheet → another instance → warning → after confirming, signed out of the first one and
    asked to log in to the second.
11. "Sign up" on an instance with `MONICA_REGISTRATION_ENABLED=false` → explanation, no browser.
