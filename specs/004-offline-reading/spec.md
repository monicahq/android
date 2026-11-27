# Feature Specification: Offline Reading

**Feature Branch**: `2026-11-27-offline-reading`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Lecture hors ligne : la personne peut consulter ses données Monica sans
connexion. Les données lues depuis l'API sont gardées sur le téléphone dans une base locale chiffrée
(Room), les écrans affichent d'abord cette copie puis la mettent à jour quand l'instance répond. Le
serveur reste la source de vérité ; la copie locale est en lecture seule (aucune écriture hors
ligne). La copie est isolée par instance et par compte, effacée à la déconnexion et au changement
d'instance, exclue des sauvegardes. Premier périmètre : les coffres (la liste des coffres et un
coffre), avec une mécanique conçue pour être reprise par chaque ressource à venir (contacts,
rappels...). L'écran indique quand les données affichées ne sont pas à jour et depuis quand."

**Builds on**: `specs/003-vaults-list` (pull request #7, not merged yet), which adds the vault list,
the vault screen and the vault creation form. This feature changes how those screens get their
data; it cannot be implemented before #7 is merged.

**Constitution**: this feature builds the mechanism that Principle VIII "Readable Offline"
(constitution 1.4.0) requires of every later feature. The requirements below are the detailed form
of that principle; a change to one changes the other. The server stays the source of truth: the
phone only keeps a copy of what the instance already answered.

## Clarifications

### Session 2026-11-27

- Q: If the phone never reaches the instance again (lost phone, session ended from the web while
  the phone is offline), should the copy expire on its own? → A: Yes. After 30 days without any
  successful contact with the instance, the copy is erased the next time the app opens (FR-013).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See my vaults without a connection (Priority: P1)

A person on a plane, in the metro or abroad without data opens the app. Instead of an error, they
see their vaults as they were the last time the app reached the instance, with a discreet line
saying the list is not up to date and when it was last updated ("Last updated 3 hours ago"). When
the connection comes back, the list updates by itself and the line disappears.

**Why this priority**: It is the reason for the feature. Without it, the app is useless whenever the
network is.

**Independent Test**: Sign in and open the vault list once online. Turn on airplane mode, close the
app completely, reopen it: the same vaults appear, with the "not up to date" line and the right
time. Turn airplane mode off: the list refreshes and the line goes.

**Acceptance Scenarios**:

1. **Given** the vault list was loaded at least once on this phone for this account, **When** the
   app opens without a connection, **Then** the vault list shows the copy, with the same vaults,
   order, names, colours, contact counts and default badge as when it was last loaded.
2. **Given** the copy is shown because the instance cannot be reached, **When** the screen is
   displayed, **Then** it says the information is not up to date and since when, in words and a
   relative time in the person's language ("Last updated 3 hours ago" / "Mis à jour il y a
   3 heures"), not by colour alone.
3. **Given** the copy is shown offline, **When** the connection comes back and the screen is shown
   again or pulled down, **Then** the list is updated from the instance and the "not up to date"
   line disappears.
4. **Given** the app has never loaded the vault list for this account on this phone (first sign-in,
   or the copy was erased), **When** it opens without a connection, **Then** the screen shows the
   connection error with "Try again", as it does today.
5. **Given** the copy is shown offline, **When** the person pulls the list down, **Then** the copy
   stays and the screen says the vaults could not be updated, as it does today.

---

### User Story 2 - Instant screens when online (Priority: P1)

A person with a connection opens the app. Their vaults appear at once, from the copy, then are
replaced by the instance's answer a moment later, without a loading spinner in between and without
the list jumping or flashing.

**Why this priority**: Every person benefits from it, every day, not only offline. It also proves the
copy is used consistently before the offline case is relied on.

**Independent Test**: With a slow connection (or an instance that answers after 3 seconds), open the
app: the list shows immediately. Rename a vault on the web, reopen the app: the old name shows, then
the new one replaces it without any action.

**Acceptance Scenarios**:

1. **Given** a copy exists and the instance answers, **When** the app opens, **Then** the list
   appears from the copy before the instance answers, with no loading indicator covering it.
2. **Given** a vault was renamed, recoloured, added or deleted on the web, **When** the instance
   answers, **Then** the list shows exactly what the instance answered: renamed and recoloured
   vaults change, new ones appear, deleted ones disappear, in the instance's order.
3. **Given** the instance answers while the copy is shown, **When** the list updates, **Then** the
   person's scroll position and the vault they were about to tap stay in place where those vaults
   still exist.
4. **Given** no copy exists yet, **When** the app opens online, **Then** the screen shows loading,
   then the list, as today.

---

### User Story 3 - The copy never outlives the session, never leaves the phone (Priority: P1)

The copy holds what a person knows about the people they love. It exists only for the session that
loaded it: it disappears when the person signs out, switches instance, or when the instance ends the
session, and it disappears on its own after 30 days without reaching the instance. It is never
included in backups or phone-to-phone transfers, and it cannot be read by other apps or by someone
copying the app's files.

**Why this priority**: Keeping personal data on the phone is acceptable only with these guarantees.
A copy that survives a sign-out, or shows one instance's data on another, would be a serious privacy
failure.

**Independent Test**: For each way a session ends (sign out, instance switch, session ended from the
web, refused renewal), check that the next launch shows no data from that session, even offline. Move
the phone's clock 31 days ahead with no network: the copy is gone. Inspect a device backup and the
app's files: no vault name appears in readable form.

**Acceptance Scenarios**:

1. **Given** a copy exists, **When** the person signs out (confirmed), **Then** the copy is erased
   before the welcome screen appears, whether the instance could be told or not.
2. **Given** a copy exists for instance A, **When** the person switches to instance B, **Then**
   nothing from A is ever shown while signed in to B, even offline, even for a moment.
3. **Given** a copy exists, **When** the instance ends the session (ended from the web, or renewal
   refused), **Then** the copy is erased as soon as the app learns it, and the welcome screen says
   why, as today.
4. **Given** the person signs out and another account signs in on the same instance, **When** the
   list loads, **Then** nothing from the first account appears.
5. **Given** the app has not reached the instance successfully for more than 30 days, **When** the
   app opens, **Then** the copy is erased before anything is shown, and the screens behave as if no
   copy had ever existed (online: loading then the list; offline: the connection error).
6. **Given** the phone is backed up to the cloud or transferred to a new phone, **When** the backup
   or transfer is restored, **Then** it contains no copy; the restored app shows no data until it
   signs in and loads again.
7. **Given** someone reads the app's files directly (rooted phone, forensic copy), **When** they look
   for the copy, **Then** its contents are not readable without the phone's protected key.

---

### User Story 4 - Open a vault without a connection (Priority: P2)

Offline, the person taps a vault in the list and sees its screen (name, colour, description) from
the copy, with the same "not up to date" line.

**Why this priority**: Opening a vault is the next step after the list; the vault screen is still a
placeholder, so the value is smaller today but grows with every later feature.

**Independent Test**: Load the list online, go offline, tap any vault (including one never opened
before): its screen shows its name, colour and description, with the "not up to date" line.

**Acceptance Scenarios**:

1. **Given** a vault is in the copy of the list, **When** the person opens it offline, **Then** its
   screen shows its name, colour and description from the copy, even if it was never opened online.
2. **Given** a vault is shown from the copy, **When** the instance answers that it no longer exists,
   **Then** the screen says "This vault no longer exists." and the vault disappears from the copy and
   the list.
3. **Given** the vault screen shows the copy, **When** the instance answers with changes, **Then**
   the screen updates in place.

---

### User Story 5 - Every later resource reads offline the same way (Priority: P3)

When contacts, reminders and the other resources arrive, each one is kept and shown offline under
exactly the rules of this feature, without anyone having to redesign them: same encryption, same
erasure, same expiry, same "not up to date" line.

**Why this priority**: It is not visible today, but it decides whether offline reading stays safe as
the app grows. A resource that forgets to erase its copy on sign-out would undo Story 3.

**Independent Test**: Review: the rules of Stories 1 to 4 are stated once, for any resource, and the
erasure on sign-out covers every resource kept, not a list that must be updated by hand.

**Acceptance Scenarios**:

1. **Given** a new resource keeps a copy, **When** the session ends or 30 days pass, **Then** its copy
   is erased with the others, without that resource doing anything specific.
2. **Given** a new resource keeps a copy, **When** it is shown offline, **Then** it shows the same "not
   up to date" line with the same wording.

---

### Edge Cases

- **Never online since sign-in**: no copy; offline launch shows the connection error (as today).
- **Copy cannot be read** (protected key lost after a phone reset or security change, file damaged,
  app updated with a different storage format): the copy is discarded without an error message and
  the screens behave as with no copy.
- **Phone storage full**: the app keeps working online without a copy; nothing is shown as broken.
- **Session ended from the web while the phone is offline**: the app cannot know; the copy stays
  readable until the app reaches the instance or 30 days pass (accepted risk, see Clarifications).
- **Clock moved**: the 30-day limit counts from the last successful contact, by the phone's clock. A
  clock moved forwards erases early; a clock set before the last successful contact also erases,
  since the limit can no longer be trusted (both on the safe side).
- **Instance reachable but failing** (server error, timeout): same as offline: copy shown with the "not
  up to date" line.
- **Session ended while a screen shows the copy**: the copy is erased and the app goes to the welcome
  screen; nothing of the copy stays on screen.
- **Creating a vault offline**: not possible (read only). The form behaves as today: it keeps what was
  typed and says the instance cannot be reached.
- **A vault created online**: appears in the copy at the next update of the list.
- **Two vaults lists in quick succession** (pull to refresh while the automatic update runs): the
  copy ends up equal to the latest answer, never a mix of two answers.
- **Largest font, TalkBack, dark theme**: the "not up to date" line wraps, is read by TalkBack when the
  screen opens and when it appears or disappears, and meets contrast in both themes.

## Requirements *(mandatory)*

### Functional Requirements

**Showing the copy**

- **FR-001**: Each time the vault list or a vault is received from the instance, the app MUST keep it
  as the copy for the current session, replacing the previous copy of the same data entirely (a vault
  absent from a new list is removed from the copy).
- **FR-002**: When a screen opens and a copy exists, the app MUST show the copy at once, then ask the
  instance and replace what is shown with its answer.
- **FR-003**: When the instance cannot be reached or fails, and a copy exists, the screen MUST keep
  showing the copy and MUST say it is not up to date, with the time of the last successful update as
  a relative time in the person's language.
- **FR-004**: When no copy exists, screens MUST behave exactly as they do without this feature
  (loading, error with "Try again").
- **FR-005**: The "not up to date" line MUST disappear as soon as an update succeeds.
- **FR-006**: The copy is read only: no action offered by the app MUST change it without the instance
  answering first. Actions that need the instance (creating a vault) keep their current behaviour
  offline.
- **FR-007**: A vault the instance reports as no longer existing MUST be removed from the copy.

**Isolation and erasure**

- **FR-008**: The copy MUST belong to one instance and one account. Data kept for one instance or
  account MUST never be shown while signed in to another.
- **FR-009**: The copy MUST be erased whenever a session ends, whatever the reason: sign-out
  (including when the instance could not be told), instance switch, session ended by the instance,
  renewal refused.
- **FR-010**: Erasure MUST be complete before the welcome screen is shown, and MUST cover every kind
  of data kept, including kinds added by later features.
- **FR-011**: The copy MUST be excluded from cloud backups and from device-to-device transfers.
- **FR-012**: The copy MUST be stored encrypted with a key that only this app on this phone can use
  and that cannot be extracted from the phone; if the key is unavailable, the copy is discarded.
- **FR-013**: When more than 30 days have passed since the last successful contact with the instance,
  or when the phone's clock is earlier than that last contact, the copy MUST be erased the next time
  the app opens, before anything is shown.
- **FR-014**: The copy MUST NOT be readable by other apps, shared, exported, or included in logs and
  crash reports.

**For every resource**

- **FR-015**: The rules FR-001 to FR-014 MUST apply to every resource that later keeps a copy
  (contacts, reminders...), and the erasure MUST not depend on each resource remembering to erase
  itself.

**Language and accessibility**

- **FR-016**: Every new text (the "not up to date" line and its relative times) MUST exist in English
  and French, with times formatted for the person's locale.
- **FR-017**: The "not up to date" state MUST be conveyed in words (never by colour alone), be read by
  TalkBack, be announced when it appears or disappears, meet the contrast rules in light and dark, and
  stay readable at the largest font size.

### Key Entities *(include if feature involves data)*

- **Local copy**: what the instance last answered for the current session, per kind of data (today:
  the vault list and each vault). Belongs to one instance and one account. Read only.
- **Last successful update**: per kind of data, when the instance last answered; used for the "not up
  to date" line.
- **Last contact with the instance**: when any request last succeeded; used for the 30-day expiry.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With a copy present, the vault list is visible within 1 second of opening the app, with
  or without a connection, on a mid-range phone.
- **SC-002**: In airplane mode, a person who loaded their vaults at least once can see the list and
  open any vault in 100% of attempts, until a session end or the 30-day limit.
- **SC-003**: After any of the four ways a session ends, 0 items of the previous session's data can be
  seen in the app, even offline, and 0 remain in the app's storage.
- **SC-004**: A cloud backup or device transfer of the app contains 0 items of vault data.
- **SC-005**: Read directly from the app's files, the copy reveals 0 readable vault names or
  descriptions.
- **SC-006**: After an update succeeds, the screen shows exactly the instance's answer in 100% of
  cases (no vault left over, none missing, same order).
- **SC-007**: Every state of the affected screens, including "not up to date", passes the automated
  accessibility checks in light and dark, and is understood with TalkBack alone.

## Assumptions

- Read only, as decided before this spec: writing offline (creating or editing while disconnected,
  then sending later) is out of scope, and would need an idempotency key in the Monica API first.
- The scope of this feature is the vault list and the vault screen; contacts, reminders and the rest
  follow FR-015 when their own features arrive.
- The phone's own screen lock protects access to the unlocked app; the app adds no lock of its own
  (a possible later feature).
- The 30-day limit counts from the last successful request of any kind to the instance.
- Nothing about the session (tokens) changes: it stays where and how `specs/002-multi-instance-auth`
  stores it. The copy is separate and erased with it.
- No new Monica API endpoint is needed: the copy holds exactly what the existing endpoints answer.
- The constitution names Room as the preferred local storage; encrypting it will need a justified
  library choice in the plan (Complexity Tracking).
