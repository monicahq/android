# Monica Android Constitution

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
- **Resources**: all user-facing text lives in `strings.xml`. Interactive and meaningful visual
  elements MUST have accessibility semantics (content descriptions, touch targets ≥ 48dp).

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

## Technology Standards

- **Language & build**: Kotlin (built into AGP 9), Gradle Kotlin DSL, JDK 21. All dependency
  versions live in `gradle/libs.versions.toml`; no hard-coded versions in build scripts.
- **Platform**: minSdk 28, compileSdk/targetSdk follow the latest stable Android release.
- **Preferred Jetpack libraries** (added only when a feature needs them): Navigation Compose
  (type-safe routes), Hilt for dependency injection, Room for local storage, DataStore for
  preferences, WorkManager for background work.
- **Formatting**: ktlint via Spotless (`./gradlew spotlessApply`). Android Lint MUST pass with
  no errors.
- **Security**: no secrets, API tokens, or keystores in the repository. User credentials are
  stored only with Android-provided secure storage.

## Development Workflow & Quality Gates

- Every feature follows Spec Kit: `/speckit-specify` → (`/speckit-clarify`) → `/speckit-plan` →
  `/speckit-tasks` → `/speckit-implement`. Specs live in `specs/`.
- `tasks.md` MUST list unit test and UI test tasks for every user story; a story is not done
  until its tests pass.
- Work happens on branches and lands on `main` through pull requests. Direct pushes to `main`
  are not allowed once the repository is published.
- CI (`.github/workflows/ci.yml`) MUST be green before merging: Spotless check, Android Lint,
  unit tests, debug and release builds, and instrumented tests on an emulator.
- Coverage gate: Kover reports MUST be produced in CI and fail the build when non-UI coverage
  drops below 80% (TODO(COVERAGE_GATE): configure Kover with the first feature).
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

**Version**: 1.0.0 | **Ratified**: 2026-11-25 | **Last Amended**: 2026-11-25
