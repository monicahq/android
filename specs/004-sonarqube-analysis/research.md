# Research: SonarQube Analysis

Each entry: what was decided, why, and what else was considered. "Checked" says where the fact
comes from; "To verify" marks what could not be confirmed before a real analysis runs, with the
fallback if it turns out wrong.

> **Changed during implementation (2026-11-28)**: the analysis runs through the SonarScanner Gradle
> plugin (`org.sonarqube` 7.6.0.18749, research R1's alternative) instead of the scan action, with its
> settings in the `sonar { }` block of `app/build.gradle.kts` instead of `sonar-project.properties`,
> and signs in with `SonarSource/sonarqube-auth-action` (the job's GitHub OIDC token exchanged for a
> short-lived token) instead of the `SONAR_TOKEN` secret. Forks and Dependabot are skipped by the
> origin of the pull request (job variable `ANALYSE`), since they get no OIDC token. Everything else
> below stands.

## R1. How the analysis is run

**Decision**: the `SonarSource/sonarqube-scan-action` GitHub action, pinned to the same commit as
the web app (`70bad232163b09d998ea0707ee80c0ec80798c16`, v8.5.1), reading a
`sonar-project.properties` file at the repository root.

**Rationale**: no new Gradle plugin and no change to the build (constitution, principle I). It is
what the web app does (`../monica/.github/workflows/tests.yml`, "SonarCloud Scan"), so one team
maintains one way of doing it. The app has no Java sources (checked), so the analysis job is
expected to need neither the JDK nor Gradle.

**To verify on the first analysis**: the scan of Kotlin sources completes without compiled classes
or a classpath. Fallback: the Gradle plugin below.

**Alternatives considered**:

- *SonarScanner for Gradle (`org.sonarqube`)*: supports AGP 9 since 7.3.0 (2026-04-30, SonarSource
  release notes). It finds source sets and the compile classpath by itself, which makes a few
  Kotlin rules more precise. Rejected for now: a new build plugin, and the analysis job would have
  to configure and compile the project again. Revisit if rules that need the classpath prove
  noisy or silent.
- *Automatic analysis by SonarQube Cloud (no CI)*: no configuration at all, but it cannot import
  coverage, so the gate's coverage condition (R6) could never pass. Rejected.

## R2. Where the configuration lives

**Decision**: `sonar-project.properties` at the repository root holds everything that is not a
secret: organisation, project key, sources, tests, report paths, coverage exclusions.

**Rationale**: one place, read by the scanner without arguments. The SonarQube editor and assistant
tooling also resolve the project key from this file. The web app passes everything as `-D`
arguments in its workflow, which is harder to read and to reuse.

**Confirmed on 2026-11-27**: organisation `monicahq`, project key `monicahq_android`. The key follows
the other projects of the organisation (`monicahq_laradb`, `monicahq_rachel`, ...); the project
does not exist yet (checked: five projects listed, none for this repository). The key is written
only in this file.

## R3. A separate job, fed by the unit test run

**Decision**: a new job named **Analysis** in `.github/workflows/ci.yml`, with `needs: unit-tests`.
The Unit tests job also produces the coverage XML and uploads it with the test result XML as one
artifact; the Analysis job downloads it and scans.

**Rationale**:

- FR-008: a failed analysis must not turn the Unit tests check red, so it cannot be a step of that
  job.
- US3 scenario 1: coverage comes from the same test run as the existing report, so the tests are
  not run a second time.
- FR-006: when the unit tests fail, the Analysis job is skipped, so no coverage is published.

**Checked**: today the Unit tests job produces only the HTML coverage report
(`koverHtmlReportDebug`) and uploads HTML only. Both inputs are new: `koverXmlReportDebug` added
to the Gradle command, and an upload of the XML report and of
`app/build/test-results/testDebugUnitTest/`.

**Alternative considered**: let the Analysis job run the tests itself. Simpler YAML, but a second
test run per pull request and a coverage figure from another run. Rejected.

## R4. Pull requests and `main`, with full history

**Decision**: the job runs on the workflow's existing triggers (`pull_request`, `push` to `main`).
The checkout uses `fetch-depth: 0`.

**Rationale**: the scanner reads the pull request number and branches from the GitHub environment,
so nothing is passed by hand. The service needs the history to tell new code from old (FR-002) and
to attribute lines; SonarQube Cloud's GitHub Actions page asks for a full clone. The web app
analyses pull requests only; `main` is added here because FR-004 asks for it and the workflow
already runs there.

## R5. How merging is blocked

**Decision**: on pull requests the scanner waits for the quality gate
(`-Dsonar.qualitygate.wait=true`) and fails the Analysis job when the gate fails. The repository
ruleset for `main` then requires the **Analysis** check. On `main` the scanner does not wait.

**Rationale**:

- The ruleset (id 28114134) requires checks by job name: `Format`, `Lint`, `Unit tests`, `Build`,
  `Instrumented tests` (checked through the GitHub API). A sixth name fits what is there.
- FR-007 rules out requiring SonarQube Cloud's own check on the pull request: it never reports
  when the analysis is skipped, and a required check that never reports blocks the pull request
  forever. A job whose steps are skipped still reports success (same reasoning as the comment at
  the top of the web app's `tests.yml`).
- Not waiting on `main` follows the edge case "`main` itself fails the quality gate": it is shown
  on the project's page, and CI on `main` stays about the build.

**Alternative considered**: put the wait in `sonar-project.properties`. One line less in the
workflow, but a red `main` for a state no pull request can fix. Rejected.

## R6. The quality gate that applies

**Decision**: the organisation's default gate, "Monica way", unchanged.

**Checked** (SonarQube Cloud, organisation `monicahq`). It fails a pull request when, on new code:

| Condition | Fails when |
|---|---|
| Security, reliability, maintainability rating | worse than A |
| Coverage | below 65% |
| Duplicated lines | above 10% |
| Security hotspots reviewed | below 100% |

**Consequences to know**:

- The coverage condition counts lines and branches of the *new* code only. The existing Kover gate
  counts lines of *all* non-UI code at 80%. A pull request can pass Kover and still be refused
  here when the non-UI code it adds is under 65% covered. FR-012 does not forbid that; it forbids
  refusing UI code (R7).
- To verify on the first pull requests: the service is expected to ignore the coverage and
  duplication conditions on small changes (under about 20 new lines). The quickstart scenarios
  that test coverage therefore add more lines than that.
- A security hotspot in new code blocks the pull request until somebody with rights on the project
  marks it reviewed in SonarQube Cloud. An outside contributor cannot do it alone.

## R7. Coverage: same exclusions as the Kover gate

**Decision**: coverage is imported from the Kover XML report (JaCoCo format) with
`sonar.coverage.jacoco.xmlReportPaths`. `sonar.coverage.exclusions` repeats, as file patterns, what
`app/build.gradle.kts` excludes from Kover:

| Kover exclusion | File pattern for the analysis |
|---|---|
| `*ScreenKt`, `*SheetKt` | `**/*Screen.kt`, `**/*Sheet.kt` |
| package `ui.theme` | `**/ui/theme/**` |
| `MainActivity`, `MonicaApplication` | `**/MainActivity.kt`, `**/MonicaApplication.kt` |
| annotated `@Composable` / `@Preview` | no equivalent: `**/SignedInApp.kt` listed by name |
| `R`, `BuildConfig`, `ComposableSingletons` | generated, outside the analysed sources |

**Rationale**: a file missing from the Kover report is expected to count as uncovered (not
confirmed in this session); the gate's coverage condition would then refuse a pull request that
only adds a screen (FR-012, SC-008). The patterns remove the doubt either way.

**Known difference (to document, FR-006)**: Kover excludes by annotation, the service by file.
Checked: outside `*Screen.kt`, `*Sheet.kt` and the theme, only `MainActivity.kt` and
`SignedInApp.kt` hold composables. `SignedInApp.kt` also holds one small non-composable function
that Kover measures and the analysis will not. The rule for contributors: a file that holds
composables is named `*Screen.kt` or `*Sheet.kt`, or is added to the list.

**To verify on the first analysis**:

- The XML file name. Expected `app/build/reports/kover/reportDebug.xml`; the Kover documentation
  confirms the task `koverXmlReportDebug` and that report filters apply to XML, not the file name.
  Fallback: read the name from the first local run and put it in the properties file.
- Kotlin files of the report are matched to the sources (coverage is not 0% everywhere). Fallback:
  point `sonar.sources` at `app/src/main/java` so package paths resolve.

## R8. Unit test results

**Decision**: `sonar.junit.reportPaths=app/build/test-results/testDebugUnitTest`.

**To verify on the first analysis**: the test count shows on the project's page. This import is
handled by the service's JVM analyser and may ignore Kotlin test files. Fallback: drop the line and
the upload of test results; coverage, which the gate uses, does not depend on it. FR-006's "results"
would then be reported as not available from this scanner.

## R9. No token, no analysis

**Decision**: the job sets `SONAR_TOKEN` from the repository secret, and its download and scan
steps run only `if: env.SONAR_TOKEN != ''`.

**Rationale**: pull requests from forks and from Dependabot get no secrets, so the steps are
skipped and the job reports success (FR-007, SC-005). Same guard as the web app. The user confirmed
that these pull requests merge without analysis.

**Design constraint** (FR-010): the token is passed as an environment variable only, never in a
command line or a file; GitHub masks secrets in logs.

## R10. What a maintainer does, and in which order

A required check that never reports stalls every open pull request, so the order matters:

1. In SonarQube Cloud, organisation `monicahq`: create the public project for
   `monicahq/android` with the key of R2, check that the GitHub application can see the
   repository, and turn Automatic Analysis off (CI analysis and automatic analysis refuse to
   coexist).
2. Nothing to do for the token: an organisation secret named `SONAR_TOKEN` already reaches this
   repository (confirmed by the maintainer on 2026-11-27). The analysis therefore starts with the
   first push that contains the Analysis job, so step 1 comes before that push.
3. Push to the pull request of this feature: it is the first analysed pull request and the test of
   the whole chain (quickstart).
4. Merge. The run on `main` creates the reference analysis.
5. Last: add `Analysis` to the required checks of the `main` ruleset.

Because the token is already there, a push of the Analysis job before step 1 runs a real scan
against a project that does not exist, and the check fails. Pull requests from forks and from
Dependabot still get no secret and are skipped.

**To verify**: step 3 assumes the service analyses a pull request before `main` was ever analysed.
`main` cannot be analysed earlier, since it has no Analysis job until the merge. The job never
skips on this repository (the organisation secret is there). Fallback: `Analysis` is not required
until step 5, so merge with it red; the push to `main` gives the first analysis, and pull request
results are checked on the next pull request.

## R11. Constitution and documentation

**Decision**: amend the constitution with `/speckit-constitution` in the same pull request
(FR-013): the list of checks that must be green before merging gains "SonarQube analysis (quality
gate), skipped for pull requests that cannot read the token". Version 1.3.1 → 1.4.0 (MINOR: a new
required check). `CLAUDE.md` line "CI ... runs all of these" and the README get the matching
sentence; the README gains a "Code analysis" section (FR-011): link to the project page, what is
excluded from coverage and why, the steps of R10.
