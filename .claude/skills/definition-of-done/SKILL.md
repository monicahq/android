---
name: definition-of-done
description: The checklist a change passes before it is committed or put in a pull request. Formatting, Android Lint, unit tests, UI tests, builds, documentation and the features list. Use before every commit, before every pull request, and when asked whether a change is finished.
---

# Definition of done

CI runs every check below on each pull request. A change that passes its own tests alone can still
land a red pull request, so run the whole list.

Run this before every commit and before every pull request. Work through the items in order. When a
check fails, fix it, then start again from that check.

## 1. Find what changed

```bash
git diff --name-only main...HEAD
git status --short
```

## 2. Unit tests

Every changed ViewModel, repository, mapper or non-UI class has a JVM test in `app/src/test`, in the
same package, following the `tests` skill and `docs/testing.md`. A changed class with no test fails
this step.

```bash
./gradlew testDebugUnitTest
```

## 3. UI tests

Every changed screen has Compose UI tests for each of its states and its main interactions in
`app/src/androidTest`. Run them on an emulator or a device:

```bash
./gradlew connectedDebugAndroidTest
```

When no emulator is available, say so explicitly in the summary of the change: CI runs them, but
the change is not verified locally.

Skip this step only when the change touches no Kotlin under `app/src/main` and no resource.

## 4. Formatting

```bash
./gradlew spotlessApply
```

Commit the formatting changes with the change itself.

## 5. Android Lint

```bash
./gradlew lintDebug
```

No error. A new warning is fixed rather than suppressed; a suppression carries a comment saying why.

## 6. Builds

```bash
./gradlew assembleDebug assembleRelease
```

The release build runs R8: a class reached by reflection that it strips shows up here, not in debug.

## 7. Strings and accessibility

Every user-visible text is in `app/src/main/res/values/strings.xml`. Every meaningful image or icon
has a content description, every clickable element a touch target of at least 48dp.

## 8. Documentation

- `docs/FEATURES.md` has a line for each feature or improvement a person will notice, following the
  `feature-documentator` skill.
- `README.md` and `docs/` are updated when the change alters how the app is built, configured or
  used. Public classes and non-obvious functions have KDoc saying *why*.

## 9. Constitution

The change respects `.specify/memory/constitution.md`: simplest solution, official Android
architecture, unit and UI tests, native only. A deviation is justified in the feature's `plan.md`.

## 10. Commit

Follow the `commits` skill: Conventional Commits with a scope, on a dated branch, and no mention of
Claude anywhere.
