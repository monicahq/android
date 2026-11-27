# Monica for Android

The Android version of [Monica](https://github.com/monicahq/monica), the open source personal CRM.
It aims to offer every feature of the web app, plus what only makes sense on a phone, and talks to
any Monica instance through its public API. Written in Kotlin with Jetpack Compose.

## Requirements

- JDK 21 (pinned in `mise.toml`, run `mise install`)
- Android SDK with platform `android-37.2` (Android Studio, or the command-line tools in `~/Android/Sdk`)

## Common commands

```sh
./gradlew assembleDebug              # build the debug APK
./gradlew installDebug               # install on a connected device or emulator
./gradlew testDebugUnitTest          # unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests (needs a device or emulator)
./gradlew lintDebug                  # Android Lint
./gradlew spotlessApply              # format Kotlin code (ktlint)
```

## Code analysis

Every pull request and every commit on `main` is analysed at
[SonarQube Cloud](https://sonarcloud.io/project/overview?id=monicahq_android), in the `monicahq`
organisation like the web app. Anybody can read the results there without an account. A pull
request whose changed code fails the quality gate cannot be merged: the `Analysis` check of CI turns
red. Pull requests from forks and from Dependabot get no access to SonarQube Cloud, so they are not
analysed until they are merged.

The analysis runs through the SonarScanner Gradle plugin (`./gradlew sonar`); its settings are in
the `sonar { }` block of [`app/build.gradle.kts`](app/build.gradle.kts). CI signs in to SonarQube
Cloud with the job's GitHub OIDC token: no stored secret.

### Coverage

Coverage comes from the unit tests, through the same Kover run as the 80% coverage gate. Screens,
sheets, the theme, `MainActivity.kt`, `MonicaApplication.kt` and `SignedInApp.kt` are left out, as
they are from the Kover gate: the Compose UI tests cover them on a device. One difference remains:
Kover leaves out code by annotation (`@Composable`), the analysis by file, so the one function of
`SignedInApp.kt` that is not a composable is measured by Kover only. A new file holding composables
is named `*Screen.kt` or `*Sheet.kt`, or added to both lists (the `kover { }` and `sonar { }` blocks
of `app/build.gradle.kts`).

### Maintainer setup

In this order, because a required check that never reports blocks every open pull request:

1. In SonarQube Cloud, create the public project `monicahq_android` in the `monicahq` organisation,
   bound to this GitHub repository (the OIDC sign-in only works for a bound project), and turn
   Automatic Analysis off.
2. Check that the first analysis signs in: the "Authenticate to SonarQube Cloud" step of the
   `Analysis` job succeeds.
3. Merge the pull request that adds the analysis. Its run on `main` is the reference later pull
   requests are compared with.
4. Once coverage shows on the project page, add `Analysis` to the required status checks of the
   `main` ruleset.

## Running against a local Monica

Release builds only accept `https://` instances. Debug builds also accept `http://` and
user-installed certificate authorities, to reach a Monica running on your machine: from the
emulator, enter `http://10.0.2.2:8000` (your Monica's port) as the instance, and set Monica's
`APP_URL` to that same address. See [Architecture](docs/architecture.md#https-and-local-development).

## Principles

The project rules are in the [constitution](.specify/memory/constitution.md). In short: simple code,
official Android best practices, everything unit tested, every screen UI tested, native Android only.

- [Architecture](docs/architecture.md): how screens, ViewModels and repositories fit together
- [Testing](docs/testing.md): what to test and how
- [Accessibility](docs/accessibility.md): mandatory rules and checks for every screen

## Spec-driven development

This repo uses [Spec Kit](https://github.com/github/spec-kit):

1. `/speckit-constitution`: project principles (once)
2. `/speckit-specify`: describe a feature (it first updates `main` and creates a dated branch from it)
3. `/speckit-clarify` (optional), then `/speckit-plan`
4. `/speckit-tasks`, then `/speckit-implement`

Specs live in `specs/`, the constitution in `.specify/memory/constitution.md`.
