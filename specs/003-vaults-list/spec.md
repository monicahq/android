# Feature Specification: Vault List

**Feature Branch**: `2026-11-27-vaults-list`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "implemente maintenant la page quand on arrive sur la liste des vaults,
qui apparait quand on se connecte (ou cree un compte). j'imagine que tous les call d'api doivent
etre centraliser quelque part. j'aime dans laravel la structure par Actions, le fait que tout soit
clean avec des controllers, etc... a ton la meme chose pour android? on a aussi besoin du dark mode.
voici le design: `Vaults Android.dc.html` (Claude Design project c63853f6)."

**Builds on**: `specs/002-multi-instance-auth` (merged), which signs the person in and shows a
placeholder home. This feature replaces that placeholder with the vault list.

**Design**: `Vaults Android.dc.html` in the Claude Design project, two Android frames: "With
vaults" and "Empty state". The iOS frames in the same file are out of scope (the iOS app is a
separate project).

**Web app reference**: `resources/views/app/vaults/index.blade.php` (the list),
`resources/views/app/vaults/form.blade.php` (creating a vault), `app/ViewModels/Vaults/VaultsViewModel.php`,
API `GET /api/v1/vaults` and `POST /api/v1/vaults` (`resources/docs/api/05-vaults.php`).

## Clarifications

### Session 2026-11-27

- Q: The design shows when a vault was last opened, faces of recent contacts, open tasks and dates
  this month, which the vault API does not return. How is that handled? → A: Ship with what the API
  offers today. Cards show the colour, initial, name, "Default" badge and the number of contacts.
  The rest arrives in a later feature, once the Monica API exposes it.
- Q: What does the "New vault" button do? → A: It opens a simple form to create a vault (name,
  optional description, colour), which the API already supports.
- Q: What does "dark mode" mean here? → A: Follow the system's light or dark setting, as the app
  already does, with a correct dark version of every new screen. No setting inside the app.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See my vaults when I arrive (Priority: P1)

A person signs in, or creates their account and comes back to the app. Instead of a placeholder,
they land on "All your vaults": one card per vault, in the order they arranged them on the web.
Each card shows the vault's colour with its initial, its name, how many contacts it holds, and a
"Default" badge on the vault where new records land. At the top, a round button with their initials
opens their account, and the Monica logo sits in the middle.

**Why this priority**: Every other screen of the app starts from a vault. Without this list, a
signed-in person has nothing to do.

**Independent Test**: Sign in with an account holding three vaults, one of them default. The list
shows the three in the web order, with the right names, colours, contact counts and a single
"Default" badge. Close and reopen the app: the list shows again without signing in.

**Acceptance Scenarios**:

1. **Given** a person just signed in (or just created an account), **When** sign-in completes,
   **Then** the app shows the vault list, not the previous placeholder.
2. **Given** an account with several vaults, **When** the list is shown, **Then** the vaults appear
   in the order the account arranged them (the order the web uses when switching vaults), each with
   its colour, its initial, its name and its number of contacts.
3. **Given** one vault is the default, **When** the list is shown, **Then** that vault, and only
   that one, carries a "Default" badge.
4. **Given** the list is loading, **When** it has not answered yet, **Then** the screen shows that
   it is loading, without an empty state or an error flashing first.
5. **Given** the list is shown, **When** the person pulls it down, **Then** it is loaded again from
   the instance and shows any vault added, renamed or removed on the web since.
6. **Given** the person taps a vault, **When** the card is activated, **Then** a screen for that
   vault opens with its name, colour and description, and back returns to the list at the same
   place. (The full vault screen is a later feature; this one only proves the navigation.)
7. **Given** the person scrolls the list, **When** content passes under the top bar, **Then** a thin
   line appears under the bar, as in the design.

---

### User Story 2 - Understand an empty account and start the first vault (Priority: P1)

A person whose account holds no vault (every account starts this way) sees a title, an icon, "No
vault yet", a sentence explaining what a vault is for, and a "New vault" button.

**Why this priority**: A new account always has no vault, so this is the first thing every new
person sees after creating their account. A blank screen would look broken.

**Independent Test**: Sign in with an account holding no vault. The empty state appears with its
explanation and a "New vault" button that opens the creation form.

**Acceptance Scenarios**:

1. **Given** an account with no vault, **When** the list has loaded, **Then** the screen shows the
   empty state: the vault icon, "No vault yet", the explanation and a "New vault" button.
2. **Given** the empty state, **When** the person taps "New vault", **Then** the vault creation
   form opens (User Story 3).
3. **Given** the empty state, **When** the person creates their first vault, **Then** they come back
   to a list holding that vault, marked "Default".

---

### User Story 3 - Create a vault (Priority: P2)

From the list ("New vault" floating button) or from the empty state, a person opens a short form:
a name, an optional description, and one of Monica's eight vault colours. They tap "Create vault"
and come back to the list, where the new vault appears at the end. If the instance refuses (for
example because the free plan already holds its number of vaults), they read the instance's own
explanation and keep what they typed.

**Why this priority**: The design puts "New vault" on both screens, and a new account cannot do
anything without a vault. Creating one in the browser would break the flow, but the list itself is
useful without it, so it comes after Stories 1 and 2.

**Independent Test**: Open the form, type a name, pick a colour, create. The list shows the new vault
at the end with that name and colour. Repeat on an account at its plan's vault ceiling: the
instance's message is shown and nothing is lost.

**Acceptance Scenarios**:

1. **Given** the form is open, **When** the name is empty or only spaces, **Then** "Create vault"
   cannot be used.
2. **Given** a name is typed, **When** the person taps "Create vault", **Then** the vault is created
   on the instance with that name, the description if any, and the chosen colour (the first colour
   when none was picked), and the list shows it at the end.
3. **Given** the account has other vaults, **When** the person ticks "Make this the default vault"
   before creating, **Then** the new vault becomes the default and the badge moves to it. **Given**
   it is the account's first vault, **Then** that option is not offered, since the first vault is
   always the default.
4. **Given** the instance refuses the vault (plan ceiling, invalid name, ...), **When** it answers,
   **Then** the form shows the instance's message next to the field it concerns, and keeps every
   value typed.
5. **Given** the connection fails while creating, **When** the error comes back, **Then** the form
   says the instance could not be reached, keeps the values, and creating can be tried again.
6. **Given** the person typed something, **When** they go back or tap "Cancel", **Then** the app asks
   before discarding what they typed. With nothing typed, it simply closes.
7. **Given** the person taps "Create vault" once, **When** creation is in progress, **Then** the
   button cannot be activated again, so no duplicate vault is created.

---

### User Story 4 - Reach my account from the list (Priority: P2)

The round button with the person's initials opens their account: who is signed in, on which
instance, a way to change instance and a way to sign out (with the confirmation that already
exists). These are the actions the placeholder home offered; they move here.

**Why this priority**: Without it, the list would remove the only way to sign out or change
instance. It reuses behaviour that already exists.

**Independent Test**: Tap the initials, check the name and instance shown, sign out after
confirming: the app returns to the welcome screen.

**Acceptance Scenarios**:

1. **Given** the list, **When** the person taps the initials button (announced as "Your account"),
   **Then** a panel shows their name, the instance address, "Instance settings" and "Sign out".
2. **Given** the panel, **When** they choose "Sign out" and confirm, **Then** they are signed out
   and back on the welcome screen.
3. **Given** a person whose name gives no initials (empty or only symbols), **When** the button is
   shown, **Then** it shows a generic person icon instead, with the same name.

---

### User Story 5 - Light and dark (Priority: P1)

Every screen of this feature follows the phone's light or dark setting, using the app's existing
palettes. Vault colours stay recognisable and readable on both backgrounds.

**Why this priority**: Asked explicitly, and an unreadable screen in dark mode breaks the
accessibility rule for half the people who use it.

**Independent Test**: Show each screen (list, empty, loading, error, form, account panel) in light
and dark: every text and control meets the contrast rule, the vault initials stay readable on their
tinted squares, and switching the phone's setting updates the open screen.

**Acceptance Scenarios**:

1. **Given** the phone is in dark mode, **When** any screen of this feature is shown, **Then** it
   uses the dark palette, with no light surface left over.
2. **Given** any of the eight vault colours, **When** its initial is drawn on its tinted square,
   **Then** the initial meets 4.5:1 contrast in light and in dark.
3. **Given** the setting changes while a screen is open, **When** the phone switches, **Then** the
   screen follows without losing what was shown or typed.

---

### Edge Cases

- **More vaults than one page**: the API answers in pages. The list shows every vault, whatever the
  number, in order.
- **Loading fails** (offline, instance down, timeout): an error with a "Try again" action replaces
  the list; the person stays signed in. If a list was already shown (pull to refresh), it stays
  and a message says it could not be updated.
- **The instance ended the session** while loading or creating: the app returns to the welcome
  screen, as it already does everywhere.
- **The person signs out or switches instance while the list loads**: the late answer is discarded;
  nothing of one instance appears on another.
- **Very long vault names**: wrap on several lines; nothing is cut off at any font size.
- **A colour the app does not know** (a future server colour): the card uses a neutral grey and
  stays readable.
- **Zero contacts**: the card says "No contacts" rather than "0 contacts"; one contact uses the
  singular.
- **A vault deleted on the web** and then tapped from a stale list: the vault screen says it no
  longer exists, and going back reloads the list.
- **Largest font and display size**: cards grow, text wraps, the list scrolls; the floating button
  never hides the last card.
- **Animations off in the system**: loading and transitions appear without motion.

## Requirements *(mandatory)*

### Functional Requirements

**Vault list**

- **FR-001**: After sign-in (including after creating an account), and whenever the app opens with
  a session, the app MUST show the vault list as the signed-in home, replacing the placeholder.
- **FR-002**: The list MUST show every vault of the account, in the order the instance answers
  (the account's arranged order), loading every page.
- **FR-003**: Each vault card MUST show the vault's initial on a square tinted with its colour, its
  name, its number of contacts (with plural forms) and, for the default vault only, a "Default"
  badge.
- **FR-004**: The whole card MUST be one control, announced with the vault's name, its number of
  contacts and whether it is the default, and opening the vault screen when activated.
- **FR-005**: The screen MUST have distinct loading, list, empty and error states. The error state
  MUST offer "Try again".
- **FR-006**: The person MUST be able to reload the list by pulling it down and, as an alternative to
  the gesture, through an accessible action.
- **FR-007**: The top bar MUST show the account button (the person's initials, named "Your account")
  and the Monica logo; a separator line MUST appear under the bar once the list is scrolled.
- **FR-008**: A "New vault" button MUST stay reachable on the list (floating, as designed) without
  covering the last card.
- **FR-009**: The empty state MUST show the vault icon, "No vault yet", a sentence explaining what a
  vault holds, and a "New vault" button.
- **FR-010**: The vault screen opened from a card MUST show the vault's name, colour and description
  (when there is one) and a way back; it is a placeholder for the later vault feature.
- **FR-011**: The card data that the API does not provide today (last opened, contact faces, open
  tasks, dates this month) MUST NOT be shown or invented. Adding them requires the Monica API to
  expose them first.

**Creating a vault**

- **FR-012**: The creation form MUST offer a name (required, up to 255 characters), a description
  (optional) and a choice among the eight vault colours, with the first colour selected by default.
  Each colour option MUST have a spoken name and show which one is selected by more than colour.
- **FR-013**: The form MUST offer "Make this the default vault" only when the account already has
  at least one vault.
- **FR-014**: "Create vault" MUST be unavailable while the name is blank and while a creation is in
  progress.
- **FR-015**: A refusal from the instance MUST be shown with the instance's own message, attached
  to the field it concerns, keeping every value typed. A network failure MUST be shown as such, with
  the values kept.
- **FR-016**: Leaving a form that holds typed text MUST ask for confirmation first.
- **FR-017**: After a successful creation, the person MUST return to the list, which shows the new
  vault.

**Account**

- **FR-018**: The account button MUST open a panel with the person's name, the instance address,
  "Instance settings" (the existing instance sheet) and "Sign out" (with the existing confirmation).

**Light and dark, language, accessibility**

- **FR-019**: Every screen and state of this feature MUST follow the system's light or dark
  setting using the app's palettes, and MUST meet 4.5:1 contrast for text and 3:1 for controls in
  both.
- **FR-020**: Vault colours MUST be adjusted for each palette so the initial on its tinted square
  meets 4.5:1 in light and in dark; colour MUST never be the only way to tell vaults apart.
- **FR-021**: Every text, including spoken labels, MUST exist in English and French, reusing the
  web app's wording and translations when the same text exists there.
- **FR-022**: Every screen MUST meet the project's accessibility rules: headings marked, decorative
  images hidden, 48 by 48 dp touch targets, readable at the largest font size, usable with TalkBack,
  Switch Access and a keyboard, and without motion when animations are off.

**Data and the instance**

- **FR-023**: All the information shown MUST come from the instance's API (vault list, vault
  creation, signed-in person). No vault data is stored on the phone beyond what the screen shows
  while open.
- **FR-024**: Calls to the instance MUST go through the existing session, so a session ended by the
  instance returns to the welcome screen, and answers arriving after a sign-out or an instance
  switch are discarded.

### Key Entities *(include if feature involves data)*

- **Vault**: a compartment of the account. What the app shows: id, name, description (optional),
  colour (one of eight), position in the arranged order, whether it is the default, number of
  contacts, its web address.
- **New vault**: what the person types to create one: name, description, colour, whether it
  becomes the default.
- **Signed-in person**: name (for the initials and the account panel) and instance address, already
  known from the session.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After signing in on a normal mobile connection, the person sees their vaults (or the
  empty state) within 2 seconds in 95% of cases.
- **SC-002**: A person with no vault can create their first one, from the empty state, in under one
  minute.
- **SC-003**: 100% of the vaults of an account are listed, in the same order as on the web, for
  accounts of 1, 25, 26 and 150 vaults.
- **SC-004**: Every state of every screen of this feature passes the automated accessibility checks
  in both light and dark, and at the largest font size nothing is cut off.
- **SC-005**: Every screen of this feature can be completed with TalkBack alone: reach a vault, create
  a vault, sign out.
- **SC-006**: No text of this feature appears in English when the phone is in French.

## Assumptions

- The order is the account's arranged order, as answered by the API. The web list orders by last
  visit, but the API does not expose visits; this changes when it does.
- The screen title is "All your vaults", as in the design (the web says "Your vaults"). The same
  title is used in the empty state, where the design's frame said "Vaults". French follows the web's
  "coffre" vocabulary.
- The empty state's heading reuses the web's "No vault yet" (already translated), with the design's
  explanatory sentence.
- The creation form has no design of its own. It follows the web form's fields and wording, minus
  the template choice: a new vault starts from the account's default contact template, as the API
  does when none is given.
- The plan ceiling of the free offer is not checked by the app: the instance refuses and explains,
  and the form shows that message (the instance is the source of truth).
- Tapping a vault opens a minimal vault screen; the real vault screen (`Vault Android.dc.html` in the
  same design project) is the next feature.
- Dark mode uses the app's existing dark palette (night blue), not the design system's zinc, so the
  app stays consistent with the welcome screen.
- How API calls are organised in the code (the question of a central place, like Laravel's
  controllers and Actions) is decided in `/speckit-plan`, within the constitution's simplicity rule.
- Renaming, recolouring, reordering, deleting vaults and switching the default from the list are out
  of scope.
