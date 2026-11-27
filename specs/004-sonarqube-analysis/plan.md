# Implementation Plan: SonarQube Analysis

**Branch**: `2026-11-27-sonarqube-analysis` | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-sonarqube-analysis/spec.md`

> **Changed during implementation (2026-11-28)**: the analysis runs through the SonarScanner Gradle
> plugin (`org.sonarqube` 7.6.0.18749, research R1's alternative) instead of the scan action, with its
> settings in the `sonar { }` block of `app/build.gradle.kts` instead of `sonar-project.properties`,
> and signs in with `SonarSource/sonarqube-auth-action` (the job's GitHub OIDC token exchanged for a
> short-lived token) instead of the `SONAR_TOKEN` secret. Forks and Dependabot are skipped by the
> origin of the pull request (job variable `ANALYSE`), since they get no OIDC token. Everything else
> below stands.

## Summary

Every pull request and every commit on `main` is analysed at SonarQube Cloud, in the `monicahq`
organisation that already hosts the web app. The result shows on the pull request, and a pull
request whose changed code fails the quality gate cannot be merged.

Technically: one new CI job, **Analysis**, runs the same scan action as the web app after the Unit
tests job, using the coverage and test reports that job already computes. Its settings live in a
new `sonar-project.properties`. On pull requests the scanner waits for the quality gate and fails
the job when the gate fails; the `main` ruleset requires that job. No Gradle plugin, no Kotlin
code, no change to the app.

## Technical Context

**Language/Version**: YAML (GitHub Actions) and a Java properties file. The analysed code is
Kotlin 2.4.21 (built into AGP 9.5.3); no Kotlin is written.

**Primary Dependencies**: **new** `SonarSource/sonarqube-scan-action` v8.5.1 and
`actions/download-artifact` v8.0.2, both pinned to the commits the web app uses; existing Kover
0.9.11 (one more report task, no new plugin)

**Storage**: N/A

**Testing**: no JVM or UI test applies (no Kotlin changes). Verified by the scenarios of
[quickstart.md](quickstart.md) on real pull requests; the existing checks keep running unchanged.

**Target Platform**: GitHub Actions (`ubuntu-latest`) and SonarQube Cloud

**Project Type**: mobile-app (single `app` module); this feature touches only its CI and docs

**Performance Goals**: the Analysis job finishes within 5 minutes of the Unit tests job (SC-002);
it downloads one artifact and scans about 40 source files, with no build

**Constraints**: token only in GitHub secrets (FR-010); pull requests without the token skip and
stay green (FR-007); the job name `Analysis` is referenced by the ruleset
([contracts/ci-analysis.md](contracts/ci-analysis.md))

**Scale/Scope**: 1 job, 1 properties file, 2 lines changed in the Unit tests job, 1 README
section, 1 constitution amendment

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Check | Status |
|---|---|---|
| Product context | No app behaviour changes; nothing touches the Monica API. Same service and organisation as the web app. | ✅ |
| I. Simplicity | No Gradle plugin, no module, no script. One job and one properties file, reusing the web app's action (research R1). | ✅ |
| II. Android practices | No app code. | n/a |
| III. Unit tested | No ViewModel, repository or logic added. The Kover gate (80%) is untouched and stays a condition for merging. "Test tasks for every user story" cannot apply: see Complexity Tracking. | ⚠️ justified |
| IV. UI tested | No screen. | n/a |
| V. Native only | No app code. | n/a |
| VI. Multilingual | No user-facing text. | n/a |
| VII. Accessible | No screen. | n/a |
| Tech standards | Actions pinned by commit like the rest of the workflow; no version outside `libs.versions.toml`; no secret in the repository. | ✅ |
| Workflow & gates | The list of checks required before merging changes: amended through `/speckit-constitution` in this pull request, 1.3.1 → 1.4.0 (FR-013, research R11). README updated (FR-011). | ✅ with amendment |

Re-check after design: unchanged. The design added no dependency beyond the two actions above.

## Design

### `sonar-project.properties` (new, repository root)

- `sonar.organization=monicahq`, `sonar.projectKey=monicahq_android` (confirmed, research R2)
- `sonar.sources=app/src/main`, `sonar.tests=app/src/test,app/src/androidTest`
- `sonar.coverage.jacoco.xmlReportPaths=app/build/reports/kover/reportDebug.xml`
- `sonar.junit.reportPaths=app/build/test-results/testDebugUnitTest`
- `sonar.coverage.exclusions`: the file patterns of research R7, with a comment pointing at the
  `kover { }` block of `app/build.gradle.kts` they mirror

### `.github/workflows/ci.yml`

Unit tests job:

- add `koverXmlReportDebug` to the Gradle command;
- upload one artifact, `analysis-inputs`, with the coverage XML and the test result XML (only when
  the tests pass: no `if: always()`).

New job **Analysis** (`needs: unit-tests`, `SONAR_TOKEN` from the secret at job level):

1. checkout with `fetch-depth: 0`;
2. download `analysis-inputs` into `app/build` (skipped without token);
3. scan action (skipped without token), with `-Dsonar.qualitygate.wait=true` on `pull_request`
   events only.

Why each choice: research R3 (separate job), R4 (history), R5 (blocking), R9 (no token).

### Requirement coverage

| Requirement | Met by |
|---|---|
| FR-001, FR-003 | Analysis job on every `pull_request` run; the service decorates the pull request |
| FR-002 | pull request analysis of the service + full history (R4) |
| FR-004 | same job on `push` to `main`; public project (R10) |
| FR-005 | `sonar.sources` / `sonar.tests`; build output and generated code are outside them |
| FR-006 | artifact from the Unit tests job, exclusions (R7), `needs` skips the job when tests fail |
| FR-007 | step-level `if: env.SONAR_TOKEN != ''` (R9) |
| FR-008 | separate job (R3) |
| FR-009 | gate wait on pull requests + `Analysis` required by the ruleset (R5) |
| FR-010 | secret passed as environment variable only |
| FR-011 | README "Code analysis" section (R11) |
| FR-012 | `sonar.coverage.exclusions` (R7), checked by quickstart scenario 4 |
| FR-013 | `/speckit-constitution` amendment (R11) |

### Outside the repository

A maintainer of the organisation does the five steps of research R10, in that order. The ruleset
entry comes last: a required check that never reports stalls every open pull request.

## Project Structure

### Documentation (this feature)

```text
specs/004-sonarqube-analysis/
├── plan.md
├── research.md
├── quickstart.md
├── contracts/
│   └── ci-analysis.md
└── tasks.md             # /speckit-tasks, not created here
```

No `data-model.md`: the feature stores and models no data (the spec has no Key Entities).

### Source Code (repository root)

```text
sonar-project.properties          # new: analysis settings
.github/workflows/ci.yml          # Unit tests job: XML report + artifact; new Analysis job
README.md                         # new "Code analysis" section
CLAUDE.md                         # CI sentence names the analysis
.specify/memory/constitution.md   # required checks list, v1.4.0
```

**Structure Decision**: no source tree changes. Everything is CI configuration and documentation
at the repository root; `app/` is not touched.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| No unit test and UI test tasks per user story (Development Workflow) | The feature adds no Kotlin and no screen: there is no class or composable to test. | Tests of the YAML or of the properties file would test GitHub and the scanner, not this project. The stories are verified on real pull requests with the scenarios of `quickstart.md`. |
