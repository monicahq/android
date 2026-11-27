# Monica Android Constitution

## Product Context

- This app is the **Android version of the Monica web application**
  ([monicahq/monica](https://github.com/monicahq/monica)). The goal is to offer, over time, every
  feature of the web app, plus what only makes sense on a phone (notifications, sharing, the
  device's contacts, widgets, offline reading, camera).
- The Monica server is the source of truth for data and business rules. The app talks to it
  **only through its public JSON API** (`/api/v1`, Sanctum bearer tokens, OpenAPI document at
  `/docs/api/v1/openapi`). It MUST NOT re-implement rules the server already enforces.
- The app uses the **same concepts and the same words** as the web app (account, vault, contact,
  reminder, template, ...). A screen that exists on the web behaves the same way on Android unless
  the spec states a mobile-specific reason to differ.
- When a feature needs data or an action the API does not expose yet, the API is extended in the
  Monica repository first. The app MUST NOT work around a missing endpoint (scraping, private
  routes, duplicated logic).
- Every spec starts by reading how the web app does it: its screens, its API endpoints
  (`routes/api.php`, `resources/docs/api` in the Monica repository) and its wording.

## Core Principles

### I. Simplicity First

- Code MUST be the simplest thing that correctly solves today's requirement (KISS, YAGNI).
- An abstraction (interface, base class, generic helper, module, use case) MUST NOT be introduced
  until it has at least two real consumers or is required to make code testable.
- A new library or Gradle module MUST be justified in the feature's `plan.md` (Complexity Tracking)
  by naming the concrete problem it solves and the simpler alternative that was rejected.
- Prefer deleting code over adding configuration. Readable code beats clever code.

Rationale: a small, obvious codebase is cheaper to test, review, and hand over to contributors.

### II. Official Android Best Practices

The [Guide to app architecture](https://developer.android.com/topic/architecture) is the reference.
When in doubt, do what it recommends.

- **UI layer**: Jetpack Compose and Material 3. Screens follow Unidirectional Data Flow: a
  `ViewModel` exposes a single immutable `StateFlow<UiState>` and receives events through plain
  functions. Composables collect state with `collectAsStateWithLifecycle()`.
- Each screen is split into a stateful entry composable (gets the `ViewModel`) and a **stateless**
  screen composable that takes `UiState` plus lambdas. State is hoisted; composables hold no
  business logic.
- **Data layer**: repositories are the single source of truth for each data type and expose
  `Flow` or `suspend` functions. A domain layer (use cases) is added only when logic is shared by
  several ViewModels (see Principle I).
- **Concurrency**: Kotlin coroutines and Flow only. Dispatchers are injected, never hard-coded,
  so tests can control them. No `GlobalScope`, no blocking calls on the main thread.
- **Kotlin**: idiomatic Kotlin (immutability, `data`/`sealed` types, null safety). The `!!`
  operator MUST NOT be used in production code.
- **Resources**: all user-facing text lives in string resources (see Principle VI).
  Accessibility rules are in Principle VII.

Rationale: following the platform's documented patterns makes the code predictable for any
Android developer and keeps it compatible with Jetpack tooling.

### III. Unit Tested by Default (NON-NEGOTIABLE)

- Every ViewModel, repository, mapper, and piece of non-UI logic MUST have JVM unit tests in
  `src/test`, written in the same change as the code they cover.
- ViewModel tests MUST cover every `UiState` the ViewModel can emit (loading, success, empty,
  error) and every event it handles.
- Tests use fakes (hand-written in-memory implementations) over mocking frameworks, and
  `kotlinx-coroutines-test` for coroutines.
- A bug fix MUST start with a failing test that reproduces the bug.
- Non-UI code MUST keep at least 80% line coverage.

Rationale: unit tests are fast and cheap; they are what lets us change code with confidence.

### IV. Tested UI (NON-NEGOTIABLE)

- Every screen MUST have Compose UI tests (`createComposeRule` / `createAndroidComposeRule`)
  targeting the stateless screen composable, covering each visible state and the main user
  interactions (clicks, input, navigation callbacks).
- UI tests find nodes by user-visible text or semantics, not implementation details. `testTag`
  is allowed only when no accessible alternative exists.
- Each user journey defined in a spec MUST have at least one end-to-end instrumented test
  running on the CI emulator.

Rationale: users experience the app through the UI; a screen that is not tested is not done.

### V. Native Android Only

- The app is 100% native: Kotlin, Jetpack libraries, Android SDK APIs.
- Kotlin Multiplatform, Compose Multiplatform, Flutter, React Native, and WebView-based UIs MUST
  NOT be used. The iOS app is a separate, independent project.
- Platform features (notifications, sharing, contacts, widgets, dynamic color) SHOULD use the
  native Android APIs directly.

Rationale: a native app gives the best user experience and lets us adopt new Android features
the day they ship.

### VI. Multilingual From the Start

- Every user-facing text, including accessibility labels, lives in string resources
  (`values/strings.xml` for English, `values-<lang>/strings.xml` for each translation). No text is
  written directly in code.
- English is the default language and the fallback for every missing translation. French is
  provided from the first screen. The long-term goal is parity with the languages of the web app.
- A change that adds or modifies a string MUST update every supported language in the same pull
  request.
- Wording reuses the web app's English text and its existing translations (`lang/*.json` in the
  Monica repository) whenever the same text exists there.
- The app declares its languages to Android (per-app language setting). Plurals use plural
  resources, and dates and numbers are formatted for the person's locale, never by hand.

Rationale: retrofitting translations is far more expensive than starting with them, and Monica's
users already read the web app in their own language.

### VII. Accessible to Everybody (NON-NEGOTIABLE)

WCAG 2.2 AA, applied to mobile, is a delivery condition for every screen, not a pass at the end of a
project. Every screen a change touches MUST:

- be fully usable with TalkBack: every control has a name, a role and a state, headings are marked
  as headings, decorative images are hidden and meaningful ones described, and the reading order
  follows the visual order;
- be usable with Switch Access and a hardware keyboard: every action is reachable and the focus is
  always visible;
- never carry information by colour alone;
- meet contrast of 4.5:1 for text and 3:1 for controls and focus indicators, in light and dark;
- stay readable at the largest system font size and display size: text wraps, nothing is cut or
  overlapping, content scrolls instead of shrinking;
- give every touchable element a target of at least 48 by 48 dp;
- respect the system setting that removes animations;
- offer an alternative to any gesture-only action (swipe, long press, drag).

Compose UI tests of a screen MUST run Android's automated accessibility checks, and a pull request
that changes a screen MUST state how it was checked with TalkBack. See `docs/accessibility.md`.

Rationale: Monica holds what people know about the people they love. Nobody should be locked out
of their own memories because of how they use their phone.

### VIII. Readable Offline

Applies to every feature specified after `specs/004-offline-reading`, which builds the mechanism
and brings the vault screens in line.

- Every screen that shows data read from the instance MUST stay readable without a connection: it
  shows the last copy the instance answered and says, in words, that it is not up to date and since
  when. With no copy yet, it shows the connection error as before.
- Online, a screen MUST show its copy at once, then replace it with the instance's answer. The
  server stays the source of truth; the copy only holds what the instance already answered.
- The copy is read only. Every action that changes data MUST reach the instance first; offline, it
  keeps what the person typed and says the instance cannot be reached. Writing offline and sending
  later is out of scope until the constitution says otherwise.
- The copy MUST be encrypted with a key that cannot leave the phone, isolated per instance and
  account, excluded from cloud backups and device transfers, and kept out of logs and crash
  reports.
- The copy MUST be erased whenever a session ends (sign-out, instance switch, session ended by the
  instance, renewal refused) before the welcome screen shows, and after 30 days without a successful
  contact with the instance (or when the phone's clock is earlier than that contact). Erasure is
  done in one place and covers every kind of data kept: no feature erases its own copy.
- Exceptions, which a spec MUST name explicitly when it uses them: searches answered by the server,
  billing, instance administration, and any action that writes.
- Tests cover, for each such screen, the copy shown offline, the "not up to date" state, the update
  when the instance answers, and the erasure of its data when the session ends.

Rationale: a phone is often without a network, and Monica is where people look up what they know
about someone right before they see them. Keeping that data on the phone is acceptable only if it
never outlives the session that loaded it.

## Technology Standards

- **Language & build**: Kotlin (built into AGP 9), Gradle Kotlin DSL, JDK 21. All dependency
  versions live in `gradle/libs.versions.toml`; no hard-coded versions in build scripts.
- **Platform**: minSdk 28, compileSdk/targetSdk follow the latest stable Android release.
- **Preferred Jetpack libraries** (added only when a feature needs them): Navigation Compose
  (type-safe routes), Hilt for dependency injection, Room for local storage (the offline copy of
  Principle VIII), DataStore for preferences, WorkManager for background work.
- **Formatting**: ktlint via Spotless (`./gradlew spotlessApply`). Android Lint MUST pass with
  no errors.
- **Security**: no secrets, API tokens, or keystores in the repository. User credentials are
  stored only with Android-provided secure storage.

## Development Workflow & Quality Gates

- Every feature follows Spec Kit: `/speckit-specify` → (`/speckit-clarify`) → `/speckit-plan` →
  `/speckit-tasks` → `/speckit-implement`. Specs live in `specs/`.
- `tasks.md` MUST list unit test and UI test tasks for every user story; a story is not done
  until its tests pass.
- A screen that shows instance data is not done until it reads offline under Principle VIII, or
  its spec names the exception it falls under.
- Work happens on branches and lands on `main` only through pull requests. Nothing is ever
  committed or pushed directly to `main`, without exception.
- CI (`.github/workflows/ci.yml`) MUST be green before merging: Spotless check, Android Lint,
  unit tests, debug and release builds, and instrumented tests on an emulator.
- Coverage gate: Kover (`./gradlew koverVerifyDebug`, run by the CI "Unit tests" job) fails the
  build when non-UI line coverage drops below 80%. Composables, `*Screen.kt` files, previews,
  `MainActivity` and the theme are excluded: the UI tests cover them.
- Documentation is part of done: public classes and non-obvious functions have KDoc explaining
  *why*, and `README.md` / `docs/` are updated when a feature changes how the app is built,
  configured, or used.

## Governance

- This constitution overrides any other convention in the repository. `CLAUDE.md` holds day-to-day
  runtime guidance for agents and MUST stay consistent with it.
- Every pull request review checks compliance with the Core Principles. Any deviation MUST be
  recorded and justified in the feature's `plan.md` Complexity Tracking table.
- Amendments are made through a pull request that updates this file with `/speckit-constitution`,
  states the reason, and lists any code that must migrate.
- Versioning follows semantic versioning: MAJOR for removing or redefining a principle, MINOR for
  adding a principle or materially expanding guidance, PATCH for wording and clarifications.

**Version**: 1.4.0 | **Ratified**: 2026-11-25 | **Last Amended**: 2026-11-27
