---
description: "Task list for the SonarQube analysis feature"
---

# Tasks: SonarQube Analysis

**Input**: Design documents from `specs/004-sonarqube-analysis/`

**Prerequisites**: plan.md, spec.md, research.md, contracts/ci-analysis.md, quickstart.md

**Tests**: None to write. The feature adds no Kotlin and no screen (plan.md, Complexity Tracking).
Each story is verified on real pull requests with the scenarios of quickstart.md, listed in the
Rollout phase.

**Organization**: Tasks are grouped by user story. Paths are relative to the repository root.
Three stories edit `.github/workflows/ci.yml`, so those tasks run one after the other.

> **Changed during implementation (2026-11-28)**: the analysis runs through the SonarScanner Gradle
> plugin (`org.sonarqube` 7.6.0.18749, research R1's alternative) instead of the scan action, with its
> settings in the `sonar { }` block of `app/build.gradle.kts` instead of `sonar-project.properties`,
> and signs in with `SonarSource/sonarqube-auth-action` (the job's GitHub OIDC token exchanged for a
> short-lived token) instead of the `SONAR_TOKEN` secret. Forks and Dependabot are skipped by the
> origin of the pull request (job variable `ANALYSE`), since they get no OIDC token. Everything else
> below stands.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story the task belongs to (US1…US3)

---

## Phase 1: Setup

**Purpose**: The settings file every analysis reads.

- [X] T001 Create `sonar-project.properties` at the repository root with `sonar.organization=monicahq`, `sonar.projectKey=monicahq_android`, `sonar.sources=app/src/main`, `sonar.tests=app/src/test,app/src/androidTest`, and a first comment line saying the file configures the Analysis job of `.github/workflows/ci.yml` (plan.md "Design", research.md R2). No token, no host URL.

---

## Phase 2: Foundational (Blocking Prerequisites)

No task. T001 is the only prerequisite shared by the stories.

---

## Phase 3: User Story 1 - See the analysis of a pull request (Priority: P1) 🎯 MVP

**Goal**: Each pull request that can read the token is analysed, the result shows on it, and a
failed quality gate fails the `Analysis` check.

**Independent Test**: quickstart.md scenarios 1, 2 and 6 (needs the Rollout step T015). The token
already reaches the repository, so the first push of this job runs a real scan.

### Implementation for User Story 1

- [X] T002 [US1] Add the job `analysis` with `name: Analysis` at the end of `.github/workflows/ci.yml`: `needs: unit-tests`, `runs-on: ubuntu-latest`, `timeout-minutes: 15`, job-level `env: SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}`. Steps: (1) "Checkout sources" with the same pinned `actions/checkout` commit as the other jobs plus `fetch-depth: 0`; (2) "SonarQube analysis" with `if: env.SONAR_TOKEN != ''`, `uses: SonarSource/sonarqube-scan-action@70bad232163b09d998ea0707ee80c0ec80798c16 # v8.5.1`, and `with: args: ${{ github.event_name == 'pull_request' && '-Dsonar.qualitygate.wait=true' || '' }}`. Add comments saying why: the steps, not the job, are skipped without a token so the required check still reports (research.md R5, R9); the gate is waited for on pull requests only (R5); the job name is referenced by the `main` ruleset (contracts/ci-analysis.md). Do not add permissions beyond the workflow's `contents: read`.
- [X] T003 [US1] Check `.github/workflows/ci.yml`: it parses (`python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`), every `uses:` of an external action is pinned to a 40-character commit with its version as a comment, and the job names `Format`, `Lint`, `Unit tests`, `Build`, `Instrumented tests` are unchanged (contracts/ci-analysis.md).

**Checkpoint**: The job is written. Do not push it before T015: an organisation secret named
`SONAR_TOKEN` already reaches the repository (research.md R10), so the first push runs a real scan
and fails while the project does not exist.

---

## Phase 4: User Story 2 - Follow the health of `main` (Priority: P2)

**Goal**: Each commit on `main` is analysed and anybody can read the result.

**Independent Test**: quickstart.md scenario 3, after the merge.

### Implementation for User Story 2

- [X] T004 [US2] Confirm in `.github/workflows/ci.yml` that the `analysis` job has no event or branch condition, so it also runs on `push` to `main` through the workflow's existing triggers, and that its `args` expression is empty outside `pull_request` (research.md R4, R5). Fix T002's job if not.
- [X] T005 [US2] Add a "Code analysis" section to `README.md`, after "Common commands": the link `https://sonarcloud.io/project/overview?id=monicahq_android`, one sentence saying every pull request and every commit on `main` is analysed and that a failed quality gate blocks the merge, one sentence saying pull requests from forks and from Dependabot are not analysed until merged, and a "Maintainer setup" list with the five steps of research.md R10 in their order, including why the ruleset entry comes last (FR-011).

**Checkpoint**: The README tells a newcomer where the results are and a maintainer what to set up.

---

## Phase 5: User Story 3 - See test coverage in the analysis (Priority: P3)

**Goal**: The analysis shows the coverage and results of the unit tests, with UI files left out as
in the Kover gate, so the gate's coverage condition never refuses UI code.

**Independent Test**: quickstart.md scenarios 0, 4 and 5.

### Implementation for User Story 3

- [X] T006 [US3] In the `unit-tests` job of `.github/workflows/ci.yml`, add `koverXmlReportDebug` to the Gradle command of "Unit tests and coverage gate", and add after it a step "Upload analysis inputs" using the same pinned `actions/upload-artifact` commit as the other uploads, with `name: analysis-inputs`, `path:` the two lines `app/build/reports/kover/reportDebug.xml` and `app/build/test-results/testDebugUnitTest/`, and `if-no-files-found: error`. No `if: always()`: nothing is uploaded when the tests fail (research.md R3, FR-006).
- [X] T007 [US3] In the `analysis` job of `.github/workflows/ci.yml`, add between the checkout and the scan a step "Download analysis inputs" with `if: env.SONAR_TOKEN != ''`, `uses: actions/download-artifact@9000827ccba6bdab643e8b6fd33ac0654aef8333 # v8.0.2`, `name: analysis-inputs`, `path: app/build` (the artifact's root is the common parent of its two paths; contracts/ci-analysis.md).
- [X] T008 [US3] Add to `sonar-project.properties`: `sonar.coverage.jacoco.xmlReportPaths=app/build/reports/kover/reportDebug.xml`, `sonar.junit.reportPaths=app/build/test-results/testDebugUnitTest`, and `sonar.coverage.exclusions=**/*Screen.kt,**/*Sheet.kt,**/ui/theme/**,**/MainActivity.kt,**/MonicaApplication.kt,**/SignedInApp.kt`, with a comment saying the list mirrors the `kover { }` block of `app/build.gradle.kts` and that a new file holding composables is named `*Screen.kt` or `*Sheet.kt` or added here (research.md R7).
- [X] T009 [P] [US3] In `app/build.gradle.kts`, add one comment line above the `excludes { }` of the `kover { }` block: the same files are listed in `sonar.coverage.exclusions` of `sonar-project.properties`, keep both in step.
- [X] T010 [US3] Add to the "Code analysis" section of `README.md` a short "Coverage" paragraph: coverage comes from the unit tests, screens, sheets, the theme, `MainActivity.kt`, `MonicaApplication.kt` and `SignedInApp.kt` are left out as in the Kover gate, the one known difference (Kover excludes by annotation, the analysis by file, so the non-composable function of `SignedInApp.kt` is measured by Kover only), and the naming rule of T008 (FR-006).
- [X] T011 [US3] Run quickstart.md scenario 0 (`./gradlew testDebugUnitTest koverVerifyDebug koverHtmlReportDebug koverXmlReportDebug`, needs the Android SDK) and confirm the coverage file is `app/build/reports/kover/reportDebug.xml`. If the SDK is not available, read the file name in the `coverage-report` artifact of the first CI run instead. If the name differs, fix it in `sonar-project.properties` (T008) and `.github/workflows/ci.yml` (T006), then repeat T003. Done 2026-11-28 without the SDK: the Kover 0.9.11 plugin builds the path as `reports/kover/report` + variant + `.xml` (read from its `PathsKt` class), so the name is `reportDebug.xml`.

**Checkpoint**: The Unit tests job uploads `analysis-inputs`; the Analysis job would find the
reports where the properties file says.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: The rules of the project say what the pull request now enforces.

- [X] T012 Amend `.specify/memory/constitution.md` with `/speckit-constitution` (FR-013, research.md R11): in "Development Workflow & Quality Gates", the list of checks that must be green before merging gains the SonarQube analysis and its quality gate, skipped for pull requests that cannot read the token; version 1.3.1 → 1.4.0, "Last Amended" set to the day of the change.
- [X] T013 [P] In `CLAUDE.md`, extend the sentence "CI (`.github/workflows/ci.yml`) runs all of these plus instrumented tests on an emulator." to name the SonarQube analysis and its blocking quality gate, so it stays consistent with the constitution.
- [ ] T014 Run the definition of done on the whole change: T003 again, then `./gradlew spotlessApply lintDebug testDebugUnitTest koverVerifyDebug assembleDebug assembleRelease` (needs the Android SDK; if it is not available, say so in the pull request summary and rely on CI). No line in `docs/FEATURES.md`: nothing changes for a person using the app.

---

## Phase 7: Rollout (maintainer, outside the repository)

**Purpose**: The steps of research.md R10 and the checks of quickstart.md. They need rights on the
`monicahq` organisation at SonarQube Cloud and on the GitHub repository, and they run in this
order. They are not done by `/speckit-implement`.

- [ ] T015 In SonarQube Cloud, create the public project `monicahq_android` for `monicahq/android`, check that the GitHub application can see the repository, and turn Automatic Analysis off (research.md R10 step 1).
- [ ] T016 Check in the first run of the `Analysis` job that the organisation secret `SONAR_TOKEN` is accepted for the project `monicahq_android`: the scan step runs (not skipped) and is not refused for missing rights (research.md R10 step 2). If it is refused, give the token's account the right to analyse the project.
- [ ] T017 Push to the pull request and run quickstart.md scenarios 1 and 6. Apply the fallbacks of research.md where a "to verify" fails: R1 (scan needs compiled classes) and R10 (no pull request analysis before `main`), editing `sonar-project.properties` and `.github/workflows/ci.yml` as each fallback says. Coverage cannot be judged here: this pull request adds no Kotlin and `main` has no analysis yet.
- [ ] T018 Merge, then run quickstart.md scenarios 3 and 4 on `main`. Coverage must not be 0% for the files Kover covers: a wrong report name uploads nothing for coverage without failing the job (`if-no-files-found` only fires when every path is missing). Apply the fallbacks of research.md R7 (coverage not matched to sources) and R8 (test results not imported) in a follow-up pull request if needed.
- [ ] T019 Only once scenario 4 passes: add `Analysis` to the required status checks of the `main` ruleset on GitHub (research.md R5), then run quickstart.md scenarios 2 and 5 on throwaway pull requests. Requiring the check earlier would block every Kotlin pull request on a coverage of 0%.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: first; every scan reads the file.
- **US1 (T002–T003)**: needs T001.
- **US2 (T004–T005)**: needs T002. T005 only needs the decisions of research.md.
- **US3 (T006–T011)**: needs T001 and T002. T006 → T007 (same file, and the download needs the
  upload). T008 after T001 (same file). T010 after T005 (same section).
- **Polish (T012–T014)**: after all stories; T014 last.
- **Rollout (T015–T019)**: strictly in order. T015 comes before the first push that contains the
  Analysis job (T002).

### User Story Dependencies

- **US1**: independent once T001 is done. On its own the gate's coverage condition would refuse
  pull requests, so US1 is not released without US3.
- **US2**: no code beyond US1's job; adds the documentation.
- **US3**: extends US1's job and the properties file.

### Parallel Opportunities

`.github/workflows/ci.yml` is edited by T002, T004, T006 and T007, and `sonar-project.properties`
by T001 and T008, so little runs in parallel:

- T005 (README) alongside T002–T004.
- T009 (`app/build.gradle.kts`) alongside T006–T008.
- T013 (`CLAUDE.md`) alongside T012.

## Parallel Example: User Story 3

```text
T006 → T007   .github/workflows/ci.yml
T008          sonar-project.properties
T009          app/build.gradle.kts        (parallel with the three above)
```

---

## Implementation Strategy

### MVP

US1 and US3 together: the job, its inputs and the exclusions (T001–T003, T006–T011). US1 alone
would block every pull request on coverage as soon as the token exists.

### Incremental Delivery

1. T015 first: the token already reaches the repository, so the project must exist before the
   Analysis job is pushed.
2. T001–T014 in one pull request, then T016–T017 on it: the first real analysis, which settles R1
   and R10.
3. Merge (T018): the first analysis of `main` settles coverage and test results (R7, R8).
4. Make the check required (T019), last and only with coverage working.
