# Implementation Plan: Welcome Screen

**Branch**: `2026-11-25-welcome-screen` | **Date**: 2026-11-25 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-welcome-screen/spec.md`

## Summary

Show the welcome screen of the `Welcome Android.dc.html` design as the app's first screen: instance
settings button, Monica logo and name, panda illustration, title, description, "Sign up" and
"Log in" buttons, and the current instance line. Nothing is wired yet: every control is a no-op.
The screen ships in English (default and fallback) and French, declared to Android's per-app
language setting. The current dynamic-colour theme is replaced by the Monica design system
palette (light and dark) mapped onto Material 3 colour roles. Being the first feature, it also
sets up the coverage gate (Kover) that the constitution deferred to "the first feature" and the
automated accessibility checks that principle VII requires in UI tests.

## Technical Context

**Language/Version**: Kotlin 2.4 (built into AGP 9.5), JDK 21

**Primary Dependencies**: Jetpack Compose (BOM 2026.11.01), Material 3, Activity Compose. New:
`androidx.compose.ui:ui-test-junit4-accessibility` (androidTest, from the BOM) and the Kover
Gradle plugin 0.9.11 (build only).

**Storage**: N/A (the instance address is a constant in this feature)

**Testing**: JUnit 4 on the JVM (`app/src/test`), Compose UI tests with the Accessibility Test
Framework on an emulator (`app/src/androidTest`), Kover for coverage

**Target Platform**: Android 9 (API 28) to Android 17 (API 37), phones in portrait first

**Project Type**: mobile-app (single `:app` module)

**Performance Goals**: welcome screen fully drawn in under 1 s on a cold start (SC-001)

**Constraints**: no network, no new runtime library, every text translated, WCAG 2.2 AA

**Scale/Scope**: one screen, 2 languages, ~10 strings, 3 vector drawables, 1 bitmap

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How this plan complies |
| --- | --- | --- |
| Product context | PASS | No server call; wording reuses the web app's (`Sign up` / `Inscription`, `Log in` / `Connexion`, `Instance settings` / `Paramètres de l'instance`); default instance `https://app.monicahq.com`. |
| I. Simplicity | PASS | No ViewModel, repository or navigation library: the screen has no state and no data yet (see research R1). One pure function for the host. No new runtime dependency. |
| II. Android practices | PASS | Stateless `WelcomeScreen` taking its values and callbacks; Material 3 components; strings in resources; edge-to-edge with insets. |
| III. Unit tests | PASS | `instanceHost()` and the string-parity guard are unit tested; Kover gate set up at 80% for non-UI code. |
| IV. UI tests | PASS | Compose UI tests for every element, every control, French, dark theme, large font; one end-to-end test through `MainActivity`. |
| V. Native only | PASS | Compose, vector drawables, no WebView, no web assets at runtime. |
| VI. Multilingual | PASS | `values/` (English) and `values-fr/`; locale config generated; parity test fails the build when a key is missing in a language. |
| VII. Accessibility | PASS | Heading, decorative logo hidden, described illustration, named icon button, 48 dp targets, theme colours only, scrolling content, ATF checks in light and dark. |

Post-design re-check (after Phase 1): unchanged, all PASS. No Complexity Tracking entry needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-welcome-screen/
├── plan.md              # This file
├── research.md          # Phase 0 decisions
├── data-model.md        # Instance and strings
├── quickstart.md        # How to validate the feature
├── contracts/
│   └── welcome-screen.md  # UI contract of the WelcomeScreen composable
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
app/
├── build.gradle.kts                      # generateLocaleConfig, a11y test dep, Kover
└── src/
    ├── main/
    │   ├── java/com/monicahq/android/
    │   │   ├── MainActivity.kt           # shows WelcomeScreen with no-op callbacks
    │   │   ├── instance/
    │   │   │   └── Instance.kt           # DEFAULT_INSTANCE_URL, instanceHost()
    │   │   ├── welcome/
    │   │   │   └── WelcomeScreen.kt      # stateless screen + previews
    │   │   └── ui/theme/
    │   │       ├── Color.kt              # Monica design system tokens
    │   │       ├── Type.kt               # Monica type scale on Material 3 roles
    │   │       └── Theme.kt              # light/dark schemes, no dynamic colour
    │   └── res/
    │       ├── drawable/                 # ic_logo, ic_settings, ic_instance (vectors)
    │       ├── drawable-nodpi/           # panda_welcome.webp
    │       ├── values/strings.xml        # English (default)
    │       ├── values-fr/strings.xml     # French
    │       └── resources.properties      # unqualifiedResLocale=en-US
    ├── test/java/com/monicahq/android/
    │   ├── instance/InstanceTest.kt
    │   └── StringResourcesParityTest.kt
    └── androidTest/java/com/monicahq/android/
        ├── MainActivityTest.kt           # end to end: app opens on the welcome screen
        └── welcome/WelcomeScreenTest.kt  # elements, controls, French, dark, font, a11y
build.gradle.kts                          # Kover plugin declaration
gradle/libs.versions.toml                 # kover, ui-test-junit4-accessibility
.github/workflows/ci.yml                  # Unit tests job also runs koverVerifyDebug
```

**Structure Decision**: single `:app` module, packages by feature (`welcome`, `instance`) as in
`docs/architecture.md`. `instance` holds the instance address now because the instance sheet and
every API call will reuse it.

## Complexity Tracking

No violation to justify.
