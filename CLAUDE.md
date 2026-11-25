# Monica Android

The Android version of the Monica web app ([monicahq/monica](https://github.com/monicahq/monica),
usually checked out next to this repo in `../monica`). Kotlin, Jetpack Compose, Material 3.

- Goal: every feature of the web app over time, plus mobile-specific ones.
- The server is the source of truth. The app uses only its JSON API (`/api/v1`, Sanctum tokens,
  OpenAPI at `/docs/api/v1/openapi`). Missing endpoint? It is added to the Monica API first.
- Same concepts and words as the web app (account, vault, contact, reminder, template...).
- Before specifying a feature, read how the web app does it: screens, `routes/api.php`,
  `resources/docs/api` and wording in the Monica repo.

**The rules live in `.specify/memory/constitution.md`. Read it before planning or writing code.**
Summary of what it demands:

1. **Simple**: simplest correct code. No abstraction, library or module without a concrete need.
2. **Official Android practices**: Compose + `ViewModel` exposing one `StateFlow<UiState>`
   (UDF), stateless screen composables, repositories as single source of truth, coroutines/Flow
   with injected dispatchers, strings in resources, no `!!`. See `docs/architecture.md`.
3. **Unit tested**: every ViewModel, repository and non-UI class has JVM tests in `src/test`,
   using fakes. Bug fixes start with a failing test. See `docs/testing.md`.
4. **UI tested**: every screen has Compose UI tests for each state and main interaction.
5. **Native only**: no KMP, Compose Multiplatform, Flutter, RN or WebView UI. iOS is separate.

## Stack
- AGP 9 with built-in Kotlin (do not add `org.jetbrains.kotlin.android`), Compose compiler plugin
- Dependency versions only in `gradle/libs.versions.toml`
- `com.monicahq.android`, minSdk 28, compileSdk 37.2, targetSdk 37, JDK 21 via `mise.toml`

## Before considering a change done
Follow the `definition-of-done` skill. In short:
```sh
./gradlew spotlessApply lintDebug testDebugUnitTest assembleDebug
./gradlew connectedDebugAndroidTest   # when UI changed and a device/emulator is available
```
CI (`.github/workflows/ci.yml`) runs all of these plus instrumented tests on an emulator.

## Commits
NEVER commit or push to `main`: always a dated branch and a pull request (enforced by a GitHub
ruleset and `.claude/hooks/block-push-to-main.sh`). Follow the `commits` skill: Conventional Commits `type(scope): sentence`, branches
`YYYY-MM-DD-name`, and NEVER mention Claude, AI or an assistant in commits, PRs or comments
(no `Co-Authored-By` trailer).

## Skills
- `definition-of-done`: checklist before every commit and PR
- `commits`: commit messages, branches, pull requests
- `tests`: how to write unit and UI tests
- `feature-documentator`: keep `docs/FEATURES.md` up to date after every feature
- `take-screenshot`: capture the app on an emulator for pull requests
- Spec Kit extensions: `speckit-bug-*` (bug triage), `speckit-assess-*` (evaluate an idea
  before specifying it), `speckit-archive-run` (archive a merged spec), `speckit-companion-*`

## Workflow
Spec Kit: `/speckit-specify` → `/speckit-clarify` → `/speckit-plan` → `/speckit-tasks` →
`/speckit-implement`. Specs go in `specs/`. Update `README.md` / `docs/` when a feature changes
how the app is built, configured or used.
