---
description: "Task list for the welcome screen"
---

# Tasks: Welcome Screen

**Input**: Design documents from `specs/001-welcome-screen/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/welcome-screen.md](contracts/welcome-screen.md)

**Tests**: required by the constitution (principles III, IV and VII): unit tests for non-UI code,
Compose UI tests with automated accessibility checks for the screen.

**Organization**: tasks are grouped by user story so each story can be built and tested on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: the user story the task belongs to (US1, US2, US3)

## Phase 1: Setup

**Purpose**: build configuration shared by every story.

- [x] T001 Add the Kover plugin (0.9.11) and `androidx-compose-ui-test-junit4-accessibility` (version from the BOM) to `gradle/libs.versions.toml`, and declare the Kover plugin `apply false` in `build.gradle.kts`
- [x] T002 Apply Kover in `app/build.gradle.kts` with the exclusions and 80% line bound of research R10, add the accessibility test dependency to `androidTestImplementation`, and enable `androidResources { generateLocaleConfig = true }`
- [x] T003 [P] Create `app/src/main/res/resources.properties` with `unqualifiedResLocale=en-US` (research R6)
- [x] T004 [P] Run `testDebugUnitTest koverVerifyDebug` and upload the Kover HTML report in the "Unit tests" job of `.github/workflows/ci.yml`

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: theme, assets and the instance helper every story uses.

- [x] T005 [P] Write the Monica light and dark tokens of research R2 in `app/src/main/java/com/monicahq/android/ui/theme/Color.kt`
- [x] T006 [P] Write the type scale of research R3 in `app/src/main/java/com/monicahq/android/ui/theme/Type.kt`
- [x] T007 Rewrite `app/src/main/java/com/monicahq/android/ui/theme/Theme.kt` to use the Monica colour schemes and typography, without dynamic colour (depends on T005, T006)
- [x] T008 [P] Convert `assets/logo.svg` of the design into `app/src/main/res/drawable/ic_logo.xml` (colours kept)
- [x] T009 [P] Draw the settings and instance glyphs of the design in `app/src/main/res/drawable/ic_settings.xml` and `app/src/main/res/drawable/ic_instance.xml`
- [x] T010 [P] Fetch `assets/panda-welcome.png` from the design and save it as WebP (quality 90) in `app/src/main/res/drawable-nodpi/panda_welcome.webp`
- [x] T011 [P] Write `InstanceTest` covering the host derivation of `data-model.md` `Instance.host` (scheme removed, trailing slash removed, port and path kept) in `app/src/test/java/com/monicahq/android/instance/InstanceTest.kt`
- [x] T012 Implement `DEFAULT_INSTANCE_URL` (`data-model.md` `Instance.url`) and `instanceHost()` (`data-model.md` `Instance.host`) in `app/src/main/java/com/monicahq/android/instance/Instance.kt` until T011 passes

**Checkpoint**: theme, assets and instance helper ready.

---

## Phase 3: User Story 1 - See the welcome screen (Priority: P1) 🎯 MVP

**Goal**: the app opens on the welcome screen of the design, in light and dark, with no-op controls.

**Independent test**: launch the app; every element of the design is visible, taps do nothing.

### Tests for User Story 1

- [x] T013 [P] [US1] Write UI tests for every element, each callback (settings button and instance line call `onInstanceSettings`, buttons call `onSignUp` / `onLogIn`) and the dark theme in `app/src/androidTest/java/com/monicahq/android/welcome/WelcomeScreenTest.kt`
- [x] T014 [P] [US1] Update `app/src/androidTest/java/com/monicahq/android/MainActivityTest.kt` so the end-to-end test checks the app opens on the welcome screen and a tap on "Log in" keeps it there

### Implementation for User Story 1

- [x] T015 [US1] Add the English strings of `data-model.md` (Strings table) and remove the old `welcome` string in `app/src/main/res/values/strings.xml`
- [x] T016 [US1] Implement the stateless `WelcomeScreen` of `contracts/welcome-screen.md` with the layout of research R4 and R5, plus light and dark previews, in `app/src/main/java/com/monicahq/android/welcome/WelcomeScreen.kt`
- [x] T017 [US1] Show `WelcomeScreen(instanceHost(DEFAULT_INSTANCE_URL), {}, {}, {})` from `app/src/main/java/com/monicahq/android/MainActivity.kt`

**Checkpoint**: User Story 1 is usable and tested.

---

## Phase 4: User Story 2 - Read the screen in my language (Priority: P2)

**Goal**: French on French devices, English everywhere else, both listed in the per-app language setting.

**Independent test**: switch the app language to French; every text is in French.

### Tests for User Story 2

- [x] T018 [P] [US2] Write `StringResourcesParityTest` (same keys and same placeholders in every `values*/strings.xml`, research R7) in `app/src/test/java/com/monicahq/android/StringResourcesParityTest.kt`
- [x] T019 [P] [US2] Add a French UI test (research R8) to `app/src/androidTest/java/com/monicahq/android/welcome/WelcomeScreenTest.kt`

### Implementation for User Story 2

- [x] T020 [US2] Add the French strings of `data-model.md` (Strings table, non-breaking space before the colon) in `app/src/main/res/values-fr/strings.xml`

**Checkpoint**: User Stories 1 and 2 work independently.

---

## Phase 5: User Story 3 - Use the screen with accessibility settings (Priority: P3)

**Goal**: usable with TalkBack, at the largest font size and on small screens.

**Independent test**: with TalkBack and the largest font, every control is reachable, named and nothing is cut.

### Tests for User Story 3

- [x] T021 [P] [US3] Add automated accessibility checks in light and dark, the heading and decorative-logo semantics, 48 dp targets and a 200% font-scale scroll test to `app/src/androidTest/java/com/monicahq/android/welcome/WelcomeScreenTest.kt`

### Implementation for User Story 3

- [x] T022 [US3] Fix in `app/src/main/java/com/monicahq/android/welcome/WelcomeScreen.kt` whatever T021 reveals (heading semantics, merged instance line, target sizes)

**Checkpoint**: all user stories work and are tested.

---

## Phase 6: User Story 4 - Choose the Monica instance (Priority: P4)

**Goal**: the settings button and the instance line open the instance sheet; the saved address survives restarts.

**Independent test**: open the sheet, save `monica.example.com`, restart the app: the instance line shows `monica.example.com`.

### Tests for User Story 4

- [x] T028 [P] [US4] Test `normalizeInstanceUrl()` against the rules of `data-model.md` `Instance.url` (empty, no scheme, trailing slash, port and path, http, invalid scheme, spaces, no host) in `app/src/test/java/com/monicahq/android/instance/InstanceTest.kt`
- [x] T029 [P] [US4] Test `InstanceRepository` (default when nothing is saved, saved address read back) with a DataStore in a temporary folder in `app/src/test/java/com/monicahq/android/instance/InstanceRepositoryTest.kt`
- [x] T030 [P] [US4] Test every state and event of `WelcomeViewModel` (`data-model.md` Welcome UI state: `sheet.draft`, `sheet.isError`) with a fake repository in `app/src/test/java/com/monicahq/android/welcome/WelcomeViewModelTest.kt`
- [x] T031 [P] [US4] Add sheet UI tests (opens from both controls, every element, typing, reset, cancel, save, error, French, accessibility checks, large font) to `app/src/androidTest/java/com/monicahq/android/welcome/WelcomeScreenTest.kt`
- [x] T032 [P] [US4] Add an end-to-end test (save an address, recreate the activity, the line shows it) in `app/src/androidTest/java/com/monicahq/android/MainActivityTest.kt`

### Implementation for User Story 4

- [x] T033 [US4] Add `datastore-preferences`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose` and `kotlinx-coroutines-test` to `gradle/libs.versions.toml` and `app/build.gradle.kts`; exclude `*SheetKt` from Kover
- [x] T034 [US4] Implement `normalizeInstanceUrl()` in `app/src/main/java/com/monicahq/android/instance/Instance.kt` and `InstanceRepository` in `app/src/main/java/com/monicahq/android/instance/InstanceRepository.kt`
- [x] T035 [US4] Create `MonicaApplication` in `app/src/main/java/com/monicahq/android/MonicaApplication.kt` and declare it in `app/src/main/AndroidManifest.xml`
- [x] T036 [US4] Implement `WelcomeViewModel`, `WelcomeUiState` and `InstanceSheetState` in `app/src/main/java/com/monicahq/android/welcome/WelcomeViewModel.kt`
- [x] T037 [US4] Add the sheet strings of `data-model.md` (Strings table) to `app/src/main/res/values/strings.xml` and `app/src/main/res/values-fr/strings.xml`
- [x] T038 [US4] Implement the sheet of `contracts/welcome-screen.md` in `app/src/main/java/com/monicahq/android/welcome/InstanceSheet.kt`, and `WelcomeRoute` plus the new `WelcomeScreen` signature in `app/src/main/java/com/monicahq/android/welcome/WelcomeScreen.kt`; show `WelcomeRoute` from `MainActivity.kt`
- [x] T039 [US4] Update `docs/architecture.md`, `docs/FEATURES.md` and `README.md` if needed; run the definition of done and the `accessibility-reviewer` agent

**Checkpoint**: all four user stories work and are tested.

---

## Phase 7: Polish & cross-cutting concerns

- [x] T023 [P] Resolve `TODO(COVERAGE_GATE)` in `.specify/memory/constitution.md` and describe Kover in `docs/testing.md`
- [x] T024 [P] Document languages (per-app language, parity test) in `docs/architecture.md` and the welcome screen in `docs/FEATURES.md`
- [x] T025 Run the `definition-of-done` checks: `./gradlew spotlessApply lintDebug testDebugUnitTest koverVerifyDebug assembleDebug assembleRelease`
- [x] T026 Run `./gradlew connectedDebugAndroidTest` on an emulator and the manual checks of `quickstart.md`
- [x] T027 Review the change with the `accessibility-reviewer` agent and fix its findings

---

## Dependencies & execution order

- Setup (Phase 1) → Foundational (Phase 2) → User stories → Polish.
- US1 depends on Phase 2. US2, US3 and US4 depend on US1 (they test and extend the same screen) but not on each other.
- Within a story, tests are written first and fail before the implementation.

### Parallel opportunities

- T003 and T004 in parallel after T002.
- T005, T006, T008, T009, T010 and T011 in parallel.
- T013 and T014 in parallel; T018 and T019 in parallel; T023 and T024 in parallel.

```text
# Phase 2 in parallel
T005 Color.kt   T006 Type.kt   T008 ic_logo.xml   T009 glyphs   T010 panda   T011 InstanceTest
```

## Implementation strategy

1. MVP: Phases 1–3, then validate User Story 1 on an emulator.
2. Add US2 (French), then US3 (accessibility checks), each validated on its own.
3. Polish, definition of done, pull request.
