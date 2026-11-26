# Feature Specification: Secure Multi-Instance Sign In

**Feature Branch**: `2026-11-26-multi-instance-auth`

**Created**: 2026-11-26

**Status**: Draft

**Input**: User description: "Implement secure multi-instance authentication for Monica Android: the
official Monica instance by default and any compatible self-hosted instance, OAuth 2.0 authorization
code with PKCE in the system browser, automatic discovery and client provisioning on each instance,
a central session manager with refresh, strict isolation between instances, and clear errors."
(Full description with nine phases kept in the conversation that created this spec.)

**Builds on**: `specs/001-welcome-screen` (branch `2026-11-25-welcome-screen`, not merged yet), which
adds the welcome screen, its "Log in" and "Sign up" buttons and the instance sheet.

**Audit**: what exists today in Monica and in the app is recorded in [audit.md](audit.md). In short:
Monica already runs a standard authorization server (used only by assistants reaching the MCP
server), with PKCE, refresh tokens, discovery metadata and the full web sign-in (password,
two-factor, security keys, SSO). The JSON API used by the app does not accept those tokens yet, and
nothing on an instance identifies a mobile app. No new authentication system is introduced: the
existing one is opened to the app.

## Clarifications

### Session 2026-11-26

- Q: How does the browser return to the app? → A: A private reverse-domain address
  (`com.monicahq.android:/oauth/callback`) protected by PKCE and a consent page at every sign-in; no
  App Links (FR-005).
- Q: What do release builds do with `http://` addresses? → A: Refuse them; HTTPS only. Debug builds
  only may allow `http://` (FR-031).
- Q: Is the web list of phone sessions part of this feature? → A: Yes, each phone listed and ended
  separately (User Story 6, FR-023a).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Log in to the official Monica instance (Priority: P1)

A person who has an account on the official Monica instance opens the app and taps "Log in". Their
browser opens on Monica's own sign-in page, where they sign in the way they always do (password,
two-factor code, security key, single sign-on). When they are done, they come back to the app,
already signed in, and see who they are signed in as. They never type their password into the app.

**Why this priority**: Without signing in, the app can do nothing. The official instance is the
default and serves the majority of people.

**Independent Test**: With a test account on an instance configured as the default, tap "Log in",
complete sign-in in the browser (with and without two-factor), and check the app shows the signed-in
person's name and stays signed in after being closed and reopened.

**Acceptance Scenarios**:

1. **Given** the app is on the welcome screen with the default instance, **When** the person taps
   "Log in", **Then** the system browser (not a screen inside the app) opens the sign-in page of
   `app.monicahq.com`.
2. **Given** the person signs in in the browser, including a two-factor step if their account has
   one, **When** Monica sends them back, **Then** the app opens, shows a short "Signing in…" state,
   then the signed-in home showing the person's name and the instance they are connected to.
3. **Given** the person is already signed in on Monica in that browser, **When** they tap "Log in",
   **Then** they only confirm that the app may access their account and come back signed in.
4. **Given** the person closes the browser or presses back without finishing, **When** they return
   to the app, **Then** the welcome screen says sign-in was cancelled, and "Log in" works again.
5. **Given** the person is signed in, **When** the app is closed, the phone restarted and the app
   reopened, **Then** they are still signed in without seeing the browser.
6. **Given** Android stopped the app while the browser was open, **When** Monica sends the person
   back, **Then** sign-in completes as if the app had never stopped.

---

### User Story 2 - Log in to my self-hosted instance (Priority: P1)

A person who runs their own Monica opens the instance sheet, types their server's address, and the
app checks it before saving: it tells them whether it is a Monica the app can work with. Then
"Log in" works exactly as on the official instance, against their own server. Nobody had to create
anything on the server or copy any code into the app.

**Why this priority**: Monica is open source and self-hostable; an app that only works with the
official instance excludes a core part of the community.

**Independent Test**: Install a fresh Monica, and separately upgrade an older Monica, without any
manual step; enter each address in the app; check it is accepted and that signing in works.

**Acceptance Scenarios**:

1. **Given** the instance sheet is open, **When** the person enters `monica.example.com` and saves,
   **Then** the app checks the instance, shows that it is checking, and saves the address only once
   the instance answered as a compatible Monica.
2. **Given** a freshly installed or freshly upgraded Monica with no manual configuration, **When**
   the person taps "Log in", **Then** sign-in works end to end.
3. **Given** the address points at something that is not Monica, a Monica too old for the app, an
   unreachable server, a server with an invalid certificate or a server that times out, **When**
   the person saves, **Then** the sheet stays open with a message saying which of these it is and
   what to do, and the previous instance (and any session on it) stays untouched.
4. **Given** a Monica served under a path (`https://example.com/monica`), **When** the person enters
   that address, **Then** checking and signing in work the same way.
5. **Given** the welcome screen and the sign-in progress state, **When** the person looks at them,
   **Then** the instance they are connecting to is shown, and they can open the instance sheet to
   correct it at any moment before being signed in.
6. **Given** a self-hosted instance is saved, **When** the person chooses "Use the official
   instance" in the sheet, **Then** the default instance is restored.

---

### User Story 3 - Stay signed in reliably (Priority: P2)

Once signed in, the person stays signed in for as long as they use the app regularly. Short-lived
access is renewed silently in the background. Losing the network, a slow train tunnel or a server
hiccup never signs them out. They are signed out only when their session really ended: they signed
out, it was revoked from the web, or it expired after a long period without use.

**Why this priority**: Being asked to sign in again for no reason is the most frequent complaint
about mobile apps, and signing out on a network error loses the person's trust.

**Independent Test**: Against a test instance with very short token lifetimes, use the app across
several expirations, with many requests in flight at once, offline periods and a revoked session.

**Acceptance Scenarios**:

1. **Given** the person's access has expired, **When** the app makes requests, **Then** access is
   renewed once, the requests succeed, and the person notices nothing.
2. **Given** ten requests fail at the same moment because access expired, **When** they are
   handled, **Then** exactly one renewal happens and every request is retried at most once.
3. **Given** the phone is offline or the server unreachable when access must be renewed, **When**
   the app tries, **Then** the person stays signed in, sees an "offline / can't reach your
   instance" message, and renewal is retried later.
4. **Given** the session was revoked from the web or expired, **When** the app next talks to the
   server, **Then** the person is taken back to the welcome screen with a message saying their
   session ended and they need to log in again, and no personal data from the session stays visible.
5. **Given** the server refuses an action because the person is not allowed to do it, **When** the
   app receives that answer, **Then** it shows a "not allowed" message and does not try to renew
   access or sign the person out.

---

### User Story 4 - Log out and switch instance safely (Priority: P2)

The person can log out. They can also switch to another instance, which signs them out of the first
one. Nothing signed in on one instance (access, personal data, requests in flight) ever reaches
another instance.

**Why this priority**: Logging out is a basic right; isolating instances is a security requirement,
since one person can use instances run by different people.

**Independent Test**: Sign in on instance A, start activity, switch to instance B, and check with a
recording test server that instance B never received anything belonging to A and that A's session
was ended.

**Acceptance Scenarios**:

1. **Given** the person is signed in, **When** they log out, **Then** the app tells the instance to
   end this session (this phone only; other phones and assistants stay signed in), forgets
   everything about the session on the phone, and shows the welcome screen.
2. **Given** the person is offline when they log out, **When** they confirm, **Then** the session is
   forgotten on the phone right away and the app tells the instance as soon as it can.
3. **Given** the person is signed in on instance A, **When** they save a different instance in the
   sheet, **Then** they are warned that this signs them out of A, and after confirming: pending
   sign-in is cancelled, requests to A are stopped, A's session is ended and forgotten, A's data is
   removed from the phone, and the app requires signing in to B.
4. **Given** a request or a renewal for A is in flight when the person switches to B, **When** it
   finishes, **Then** its result is discarded and nothing about A is sent to B or stored.
5. **Given** the new address fails the check, **When** the person tries to save it, **Then** the
   session on A is untouched.

---

### User Story 5 - Sign up from the app (Priority: P3)

A person without an account taps "Sign up". If the instance accepts new accounts, the browser opens
Monica's own registration page, and once the account is created they come back to the app signed in.
If the instance does not accept new accounts, the app says so instead of opening a page that would
fail.

**Why this priority**: New people arrive through the app stores; a dead "Sign up" button loses them.
It comes after signing in because it reuses the whole sign-in flow.

**Independent Test**: Tap "Sign up" on an instance with registration open and on one with
registration closed.

**Acceptance Scenarios**:

1. **Given** registration is open on the instance, **When** the person taps "Sign up", **Then** the
   browser opens the registration page of that instance, and after creating their account (and
   any email confirmation Monica requires) they come back to the app signed in.
2. **Given** registration is closed on the instance, **When** the person looks at the welcome
   screen, **Then** "Sign up" explains that this instance does not accept new accounts and to ask its
   administrator, instead of opening the browser.

---

### User Story 6 - See and end my phone sessions from the web (Priority: P3)

From the security settings of the web app, the person sees each phone signed in to their account
(app name, device name, when it signed in, when it was last used) and can end any one of them, for
example after losing a phone.

**Why this priority**: Revocation from the web is the safety net when a phone is lost or stolen.
It comes last because sign-in works without it.

**Independent Test**: Sign in from two phones, end one session from the web settings, check that
phone is signed out and the other is not.

**Acceptance Scenarios**:

1. **Given** the person is signed in on two phones, **When** they open the web security settings,
   **Then** both phones are listed separately, apart from connected assistants and API keys.
2. **Given** both phones are listed, **When** the person ends one, **Then** that phone is signed out
   at its next exchange with the server, and the other phone, the assistants and the API keys keep
   working.

---

### Edge Cases

- The person taps "Log in" twice, or the browser returns twice to the app with the same answer: only
  one sign-in happens; a returning answer that does not match the sign-in in progress is ignored and
  shown as a failed sign-in.
- A return to the app arrives with no sign-in in progress, for another instance, or after the sign-in
  was cancelled by switching instance: it is ignored, nothing is sent anywhere.
- The person takes longer than 10 minutes in the browser: the pending sign-in expires and they are
  asked to start again.
- The person denies access on Monica's consent page: the app shows "You did not allow access".
- The instance answers with an error page, maintenance mode or a non-Monica answer: "Unexpected answer
  from your instance, try again later", never the raw error.
- The address typed has a trailing slash, uppercase letters, a port, a path or spaces around it: it is
  normalised (as in the welcome screen spec), and two spellings of the same instance are the same
  instance.
- The instance redirects the app's requests to another address (another host or scheme): the app does
  not follow with credentials. If the instance moved for good, the person is told to update the
  address.
- Discovery describes sign-in addresses on another host than the instance: the instance is refused as
  incompatible unless they belong to the same instance origin.
- The person enters an `http://` address in a release build: the sheet refuses it and explains that
  the instance must use HTTPS. An `http://` address saved by an earlier version of the app is treated
  the same way at the next launch: the person is asked to correct it.
- The device clock is wrong: sessions still work, since expiry is driven by the server's answers.
- The person is signed in and the instance gets upgraded or its mobile client is reprovisioned: the
  existing session keeps working.
- The app is restored from a backup or moved to a new phone: the instance address may come along, the
  session never does; the person signs in again.
- Security keys and passkeys: they work in the browser like on the web, since sign-in happens there.
- The instance is under maintenance during renewal: treated like a network failure, not a sign-out.

## Requirements *(mandatory)*

### Functional Requirements

**Sign-in flow (app)**

- **FR-001**: The app MUST sign people in only through the instance's own web sign-in page, opened in
  the system browser or a browser tab provided by the system. It MUST NOT show the sign-in page inside
  the app, ask for a password, or store one.
- **FR-002**: Sign-in MUST use the OAuth 2.0 authorization code flow for public clients with PKCE
  (S256 only), following RFC 7636, RFC 8252 and RFC 9700. The app MUST NOT hold any client secret and
  MUST NOT use the password or implicit grants.
- **FR-003**: Each sign-in attempt MUST use a new random verifier and a new random state, generated by
  a cryptographically secure source, and MUST be bound to the instance, the client identifier and the
  return address it started with.
- **FR-004**: When the browser returns to the app, the app MUST accept the answer only if a sign-in is
  in progress, the state matches exactly, the answer has not been used yet, and (when the instance
  states its identity in the answer) it comes from the instance the sign-in started with. Anything
  else MUST be rejected without contacting any server.
- **FR-005**: The app MUST return from the browser through the private, reverse-domain address
  `com.monicahq.android:/oauth/callback` (RFC 8252 §7.1), which only the app opens and which works on
  every instance without any outside party. The return address MUST match exactly what the instance
  has registered for the app. Verified web links (App Links) on a Monica domain were rejected: every
  self-hosted instance would have to accept a monicahq.com address, and when verification fails on a
  phone the sign-in code would pass through Monica's server. The residual risk of another app claiming
  the same private address is covered by PKCE (FR-002, FR-003) and by the consent page shown at every
  sign-in (FR-018).
- **FR-006**: The pending sign-in MUST survive Android stopping the app while the browser is open, for
  up to 10 minutes, and MUST be cleared once used, cancelled, expired or when the instance changes.
- **FR-007**: After receiving tokens, the app MUST load the signed-in person's profile through the
  existing API and only then show the signed-in app.
- **FR-008**: The app MUST request only the access it needs to use the Monica API on the person's
  behalf, and MUST NOT request access to the MCP server.

**Instance discovery and compatibility**

- **FR-009**: The official instance (`https://app.monicahq.com`) MUST remain the default, and the
  person MUST be able to restore it from the instance sheet.
- **FR-010**: Before saving a new address, the app MUST check the instance and learn from it, through
  standard discovery documents wherever one exists: its identity, the API version and address it
  serves, that it supports app sign-in, its sign-in, token and session-ending addresses, the client
  identifier for the app, the PKCE methods supported, whether registration is open, and the oldest app
  version it supports when it sets one.
- **FR-011**: The app MUST refuse an instance whose discovered addresses are not on the same origin as
  the instance address (after following none of its redirects), that does not support S256, or that
  does not describe app sign-in, with a message naming the reason in plain words.
- **FR-012**: The app MUST tell the person when the instance needs a newer app, or when the app needs
  a newer Monica, with the next step for each.
- **FR-013**: Addresses served under a path (`https://example.com/monica`), behind reverse proxies, on
  custom domains and with or without trailing slashes MUST work. Sign-in and API addresses MUST come
  from discovery, not be built by the app from fixed paths, except the discovery address itself.
- **FR-014**: The address being checked MUST NOT replace the saved one, nor affect the current
  session, until the check succeeded and the person confirmed.

**Server side (Monica)**

- **FR-015**: Every Monica instance MUST provide, without any manual step from its administrator, a
  first-party public client for the Monica mobile apps, created on install and on upgrade. Creating it
  MUST be idempotent: running install or upgrade again MUST NOT create a second client, change its
  identifier, or end existing sessions.
- **FR-016**: The Monica JSON API MUST accept the tokens issued to the mobile client, limited to a
  dedicated API scope, in addition to the existing API keys. Existing API keys, the existing
  email/password API login, web sign-in, and assistant (MCP) access MUST keep working unchanged, and
  mobile tokens MUST NOT give access to the MCP server.
- **FR-017**: App sign-in MUST NOT depend on the MCP feature being switched on for the account.
- **FR-018**: The consent page shown during app sign-in MUST name the app and the instance in the
  person's language, and MUST be shown at every sign-in of the app (no silent approval), since a
  public client's return address cannot fully prove which app is asking (RFC 8252 §8.6).
- **FR-019**: The instance MUST let the app end its own session (access and renewal together) on
  logout, without affecting the person's other phones, assistants or API keys.
- **FR-020**: Access tokens given to the app MUST be short-lived (one hour at most) and renewable with
  a refresh token that is replaced at every renewal; an old refresh token MUST stop working once used,
  and refresh tokens MUST expire after 30 days without use.
- **FR-021**: Discovery, sign-in, token, renewal and session-ending addresses MUST be rate limited per
  client address, without blocking normal use (several sign-ins and renewals per minute).
- **FR-022**: The server MUST reject reuse of an authorization code, a wrong verifier, a return address
  that does not match exactly, and an expired code. Reusing a code MUST also revoke what that code
  issued (RFC 9700).
- **FR-023a**: The web security settings MUST list each phone session separately from connected
  assistants and API keys (app name, device name, signed-in date, last-used date), and let the person
  end any one of them without affecting the others (User Story 6). Ending one from the web MUST end
  both its access and its renewal.
- **FR-023**: All of the above MUST be documented in Monica's API documentation, and the server MUST
  log app sign-ins and session endings in the account log like other security events.

**Session management (app)**

- **FR-024**: One central session manager MUST own the session: it stores it, renews it, ends it and
  tells the rest of the app whether the person is signed in. No other part of the app reads tokens.
- **FR-025**: Tokens MUST be stored encrypted with a key held by the Android Keystore, and MUST be
  excluded from cloud backups and device-to-device transfers.
- **FR-026**: A renewed session MUST replace the old one in a single step: after a crash at any point,
  the stored session is either fully the old one or fully the new one.
- **FR-027**: When several requests find access expired at the same time, exactly one renewal MUST run;
  the others MUST wait for it and be retried once with the new access. A request MUST NOT be retried
  more than once for expired access, and renewal MUST NOT loop.
- **FR-028**: The app MUST tell apart and handle differently: no network or timeout (keep session,
  retry later), expired access (renew), renewal refused because the session is revoked or expired (end
  the session locally and say so), not allowed (show it, keep session), and server errors (show a
  generic message, keep session). Only an explicit "not authenticated" answer from the instance the
  session belongs to may trigger a renewal.
- **FR-029**: The session MUST record the instance that issued it. Access and renewal tokens MUST only
  ever be sent to that instance's origin, over HTTPS, and MUST NOT be sent after a redirect to another
  origin.
- **FR-030**: Switching instance MUST cancel any pending sign-in, stop requests and renewals for the
  previous instance, end the previous session on its instance when reachable, remove the session and
  all data cached for the previous instance from the phone, and use a fresh connection set up for the
  new instance. Results of requests started for the previous instance MUST be discarded.

**Security**

- **FR-031**: Release builds MUST only talk to instances over HTTPS with the system's normal
  certificate checks; no build may disable certificate validation or trust every certificate.
  Release builds MUST refuse `http://` addresses entirely, with a message explaining that the instance
  needs HTTPS. This replaces the welcome screen rule that accepted `http://` (welcome screen spec,
  FR-015 and edge cases), which MUST be updated accordingly. Only debug builds MAY allow `http://` and
  the developer's own certificate authorities, through build configuration that cannot reach a
  release build.
- **FR-032**: Addresses with a scheme other than `https` (and `http` in debug builds), with
  credentials in them, without a host, or malformed MUST be refused with a clear message.
- **FR-033**: Tokens, authorization codes, verifiers, states and passwords MUST never appear in logs,
  crash reports, analytics, screenshots of the recent-apps screen during sign-in, or error messages.
- **FR-034**: No sign-in answer (code or state) may be sent to any party other than the instance it
  belongs to.

**Errors and wording**

- **FR-035**: The app MUST show a clear message, with a next step, for each of: invalid address,
  unreachable instance, invalid certificate, timeout, Monica too old, app too old, instance without app
  sign-in, sign-in cancelled, access denied, sign-in failed, session expired, session revoked, not
  allowed, and unexpected server error. It MUST NOT show raw errors or technical details.
- **FR-036**: Every text MUST exist in English and French, reuse the web app's wording where it exists
  ("Log in", "Sign up", "instance", "Log out"), and the new screens MUST meet the accessibility
  requirements of the constitution (spoken progress and errors, focus moved to errors, 48dp targets).

### Key Entities

- **Instance**: a Monica server the person chose. Its normalised address (the identity used for
  isolation) and what discovery said about it (API address, sign-in addresses, client identifier,
  registration open or closed, version compatibility). Exactly one is current at a time.
- **Pending sign-in**: one sign-in attempt in progress: instance, client identifier, return address,
  state, verifier, start time. Lives at most 10 minutes; used once.
- **Session**: the signed-in state on one phone: the instance that issued it, access token and its
  expiry, refresh token, the signed-in person's identity. One at most.
- **Mobile client** (server): the first-party public client that represents the Monica apps on an
  instance, with its allowed return address and the API scope.
- **Phone session** (server): what the instance knows of one phone's sign-in: person, client, device
  name, created and last used dates; can be ended on its own.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A person with an existing account goes from tapping "Log in" to the signed-in app in
  under 60 seconds (excluding the time to read their two-factor code), on both the official and a
  self-hosted instance.
- **SC-002**: A fresh install and an upgrade of Monica both accept app sign-in with zero manual steps
  from the administrator; running install or upgrade again leaves exactly one mobile client.
- **SC-003**: Over 30 days of daily use, a person is never asked to sign in again unless they logged
  out, their session was revoked, or they did not open the app for 30 days.
- **SC-004**: Network loss of any length never signs a person out.
- **SC-005**: In automated tests covering every scenario of this spec, a recording server for
  instance B receives zero credentials, codes or personal data belonging to instance A, including
  during switches with requests and renewals in flight.
- **SC-006**: With 20 requests hitting expired access at once, exactly 1 renewal reaches the server.
- **SC-007**: Ending a session from the phone or from the web signs out that phone at its next
  exchange with the server and no other device.
- **SC-008**: Every error listed in FR-035 shows a message in the person's language, with no raw
  technical text, in 100% of the tested cases.
- **SC-009**: Every existing Monica test for web sign-in, API keys and assistant (MCP) access still
  passes unchanged.

## Testing scope

Covered by automated tests in each repository, following their conventions (Monica: feature tests;
app: JVM tests with fakes, Compose UI tests, an end-to-end instrumented test per user story):
official and self-hosted sign-in; instance under a path; client provisioning on install and upgrade,
twice; invalid, incompatible, too old and too new instances; successful PKCE sign-in; wrong state;
wrong verifier; code reuse; renewal; concurrent renewal; expired and revoked refresh tokens; restart
with a session; app stopped during sign-in; logout and revocation on one device only; instance switch,
including during requests and during renewal; no credential leak between instances; redirect to
another origin; network loss; invalid certificate; existing API keys, web sign-in and MCP access
unchanged.

## Assumptions

- One instance and one signed-in person at a time on the phone. Signing in to several instances or
  accounts side by side is out of scope; switching instance signs out of the previous one and clears
  its data.
- The existing authorization server of Monica (used today by assistants) is extended rather than
  replaced; no new authentication dependency is added on either side (see audit.md).
- Dynamic client registration is not used by the app: one first-party client per instance is
  provisioned automatically, which is simpler and avoids creating a client per phone. The client
  identifier is learned from discovery, never hard-coded in the app, so it can differ between
  instances.
- The existing email/password API login stays for other API consumers; the app does not use it.
- Mobile access tokens can have a lifetime different from assistants' (one day today); the plan
  decides how.
- Two-factor authentication, security keys, passkeys, magic links and single sign-on work unchanged
  because they happen on the web sign-in page.
- Whether a Monica served under a path works at all is verified during planning; if Monica does not
  support it, the app still handles such addresses correctly and the limit is documented.
- "Log out" lives on the signed-in home placeholder for now; the full signed-in app comes in later
  features.
- The app has no analytics or crash reporting today; FR-033 applies to any added later.
- This feature depends on the welcome screen branch being merged first.
