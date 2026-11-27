# Quickstart: checking the SonarQube analysis

How to prove the feature works. Nothing here runs on a developer's machine except scenario 0: the
analysis lives in CI.

> **Changed during implementation (2026-11-28)**: the analysis runs through the SonarScanner Gradle
> plugin (`org.sonarqube` 7.6.0.18749, research R1's alternative) instead of the scan action, with its
> settings in the `sonar { }` block of `app/build.gradle.kts` instead of `sonar-project.properties`,
> and signs in with `SonarSource/sonarqube-auth-action` (the job's GitHub OIDC token exchanged for a
> short-lived token) instead of the `SONAR_TOKEN` secret. Forks and Dependabot are skipped by the
> origin of the pull request (job variable `ANALYSE`), since they get no OIDC token. Everything else
> below stands.

## Prerequisites

- The maintainer step 1 of [research.md](research.md) R10 is done (project created, Automatic
  Analysis off). The `SONAR_TOKEN` organisation secret already reaches the repository. Before
  step 1, only scenarios 0 and 6 can run.
- The project page: `https://sonarcloud.io/project/overview?id=monicahq_android`.

## 0. Local: the inputs exist

```sh
./gradlew testDebugUnitTest koverVerifyDebug koverHtmlReportDebug koverXmlReportDebug
ls app/build/reports/kover/*.xml app/build/test-results/testDebugUnitTest/*.xml
```

Expected: one coverage XML and one XML per test class. If the coverage file is not named
`reportDebug.xml`, put its real name in `sonar-project.properties` and in the artifact upload
(research R7).

## 1. A pull request is analysed (US1, FR-001, FR-003)

Push to the pull request of this feature. If the service refuses a pull request while `main` was
never analysed, use the fallback order of research R10 and run this scenario on the next pull
request.

Expected: the `Analysis` check is green, and the pull request shows the SonarQube Cloud summary
with a link to the detail. Push another commit: the summary is replaced.

## 2. A failing gate blocks (US1, FR-009, SC-007)

On a throwaway branch, add to a non-UI file a function with an obvious bug-level problem and no
test, at least 30 new lines so the coverage condition applies (research R6), and open a pull
request.

Expected: `Analysis` is red, the summary names the failed conditions, the other five checks keep
their own result (FR-008). Once `Analysis` is required by the ruleset, the merge button is
disabled. Remove the problem and push: `Analysis` turns green. Close the pull request without
merging.

## 3. `main` is analysed (US2, FR-004)

After merging: open the project page signed out.

Expected: an analysis of the merged commit, readable without an account, within 30 minutes
(SC-003). CI on `main` is green whatever the gate says.

## 4. Coverage matches the Kover gate (US3, FR-006, FR-012, SC-004, SC-008)

On the project page, open Measures → Coverage.

Expected:

- coverage is not 0% for files Kover covers (the XML was matched to the sources);
- no `*Screen.kt`, `*Sheet.kt`, `ui/theme` file, `MainActivity.kt`, `MonicaApplication.kt` or
  `SignedInApp.kt` is listed as uncovered;
- the number of unit tests is shown (research R8; if not, apply its fallback).

Then open a pull request that only adds a composable of at least 30 lines to a `*Screen.kt` file
(more than the size under which the gate ignores coverage, research R6).

Expected: the gate does not fail on coverage. Close the pull request without merging.

## 5. Failing tests publish nothing (FR-006)

On a throwaway branch, break one unit test and open a pull request.

Expected: `Unit tests` is red, `Analysis` is skipped, no new analysis for that pull request on the
project page.

## 6. No token, no failure (FR-007, SC-005)

Open the checks of a Dependabot pull request (or of a pull request from a fork).

Expected: `Analysis` is green in a few seconds, its download and scan steps are marked skipped, no
summary on the pull request.

## 7. Documentation (FR-011, FR-013, SC-006)

Expected: the README has a "Code analysis" section with the project link, the coverage exclusions
and the maintainer steps; the constitution lists the analysis among the checks required before
merging, at version 1.4.0.
