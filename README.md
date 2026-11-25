# Monica for Android

Native Android client for [Monica](https://github.com/monicahq/monica), written in Kotlin with Jetpack Compose.

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

## Principles

The project rules are in the [constitution](.specify/memory/constitution.md). In short: simple code,
official Android best practices, everything unit tested, every screen UI tested, native Android only.

- [Architecture](docs/architecture.md): how screens, ViewModels and repositories fit together
- [Testing](docs/testing.md): what to test and how

## Spec-driven development

This repo uses [Spec Kit](https://github.com/github/spec-kit). With Claude Code:

1. `/speckit-constitution`: project principles (once)
2. `/speckit-specify`: describe a feature
3. `/speckit-clarify` (optional), then `/speckit-plan`
4. `/speckit-tasks`, then `/speckit-implement`

Specs live in `specs/`, the constitution in `.specify/memory/constitution.md`.
