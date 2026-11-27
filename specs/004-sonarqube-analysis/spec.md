# Feature Specification: SonarQube Analysis

**Feature Branch**: `2026-11-27-sonarqube-analysis`

**Created**: 2026-11-27

**Status**: Draft

**Input**: User description: "Add sonarqube analysis"

**Web app reference**: the Monica web app is already analysed at SonarQube Cloud, in the `monicahq`
organisation (project `monica`, public). Its analysis runs from `.github/workflows/tests.yml`
("SonarCloud Scan" step) and `docs/DEVELOPER_SETUP.md` explains how its results are read. This
feature gives the Android app the same thing, in the same organisation.

## Clarifications

- Q: Does the analysis only inform, or does it block merging? → A: It blocks. A pull request whose
  changed code fails the quality gate cannot be merged until it passes.
- Q: Pull requests from forks and from the dependency update bot cannot read the analysis token.
  May they merge without analysis? → A: Yes. Their analysis is skipped and they stay mergeable;
  their code is analysed once it is on `main`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See the analysis of a pull request (Priority: P1)

A contributor opens a pull request. Next to the existing checks (formatting, lint, tests, builds),
the pull request shows the result of a code analysis of what they changed: the bugs, the security
problems and the code smells their change adds, and whether the change passes the quality gate. A
reviewer reads that result before reading the code, and opens the detail in one click. A change
that fails the quality gate cannot be merged until it passes.

**Why this priority**: this is where the analysis is useful. A problem shown on the pull request
is fixed before it reaches `main`; a problem found later is rarely fixed.

**Independent Test**: Open a pull request from a branch of the repository that adds an obvious
problem (for example an unused variable or an empty `catch`). The pull request shows an analysis
result that names the problem and links to its detail. When the problem fails the quality gate,
merging is refused. Remove the problem and push: the result updates, no longer lists it, and the
pull request can be merged.

**Acceptance Scenarios**:

1. **Given** a pull request whose checks can read the analysis token, **When** its checks finish,
   **Then** the pull request shows the analysis result for the changed code, with a link to the
   detail.
2. **Given** a pull request whose analysis found a problem, **When** the contributor pushes a
   commit that fixes it, **Then** the result is replaced by the one for the new commit.
3. **Given** a pull request whose changed code fails the quality gate, **When** the result is
   shown, **Then** it is clearly marked as failed, the pull request cannot be merged, and the other
   checks still report on their own.
4. **Given** a pull request that changes no application code (documentation only), **When** its
   checks finish, **Then** the analysis still reports, with nothing to flag, and does not hold the
   merge.
5. **Given** a pull request that failed the quality gate, **When** a new commit makes it pass,
   **Then** the analysis no longer prevents the merge.

---

### User Story 2 - Follow the health of `main` (Priority: P2)

A maintainer wants to know the state of the app's code as a whole: how many open problems, how
they evolve, and what the last merges added. After each merge to `main`, the analysis of `main` is
refreshed, and anybody can read it on the project's public page.

**Why this priority**: the analysis of `main` is the reference a pull request is compared with.
Without it, a pull request cannot tell what is new from what was already there.

**Independent Test**: Merge a pull request. Shortly after, the project's public page shows an
analysis dated from that merge, for the merged commit.

**Acceptance Scenarios**:

1. **Given** a pull request was merged, **When** the checks of `main` finish, **Then** the
   project's page shows the analysis of the merged commit.
2. **Given** a person who is not a member of the organisation, **When** they open the project's
   page, **Then** they can read the results without signing in.
3. **Given** a problem that existed on `main` before a pull request, **When** that pull request is
   analysed, **Then** the problem is not counted against the pull request.

---

### User Story 3 - See test coverage in the analysis (Priority: P3)

The analysis shows, beside the problems, how much of the code the unit tests cover, and how much
of the code a pull request adds is covered. The figure stays close to the coverage report the
project already produces: the files that report leaves out (screens, sheets, the theme) are left out
here too, so UI code tested on a device never shows as untested.

**Why this priority**: coverage is already measured and already gates merging, so this adds no
new safeguard. It matters because the quality gate also judges the coverage of new code: counting
UI code as untested would refuse pull requests that the existing coverage gate accepts.

**Independent Test**: Open the coverage of `main` in the analysis: no screen, sheet or theme file is
listed as uncovered. Open a pull request adding an untested function: the analysis shows the new
code as not covered.

**Acceptance Scenarios**:

1. **Given** the analysis of `main`, **When** a maintainer reads its coverage, **Then** it comes
   from the same unit test run as the existing coverage report for that commit.
2. **Given** a pull request that adds code without tests, **When** it is analysed, **Then** the
   result shows the coverage of the new code.
3. **Given** a file that the existing coverage report leaves out entirely, **When** the analysis
   computes coverage, **Then** that file is left out too.
4. **Given** a pull request that passes the existing coverage gate and adds only UI code that gate
   leaves out, **When** it is analysed, **Then** the quality gate does not fail on coverage.

---

### Edge Cases

- A pull request from a fork, or from the dependency update bot, has no access to the project's
  secrets: the analysis is skipped, the pull request is neither failed nor left waiting because of
  it, and its other checks run as usual. Its code is analysed once it lands on `main`.
- The repository has no analysis token configured (a fork running its own checks, or before a
  maintainer has set it up): the analysis is skipped without failing anything.
- The analysis service is unreachable or answers with an error: the analysis check fails on its
  own, the other checks keep their own result, and running the checks again retries it. Until it
  passes, the pull request cannot be merged.
- `main` itself fails the quality gate (problems that were already there, or that came in through
  a skipped pull request): this is shown on the project's page and does not block other pull
  requests, which are judged on their own changes.
- The unit tests fail: no coverage is published for that commit, since a partial figure would
  mislead.
- The first analysis of `main` lists problems that are already in the code. They are visible on the
  project's page; fixing them is not part of this feature.
- Generated files, build output and third-party code are not analysed and do not count as
  problems or as uncovered code.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Every pull request whose checks can read the analysis token MUST be analysed, and
  the result MUST be shown on the pull request with a link to its detail (FR-007 covers the
  others).
- **FR-002**: The result of a pull request MUST cover the code that pull request changes, and MUST
  NOT count problems that were already on `main`.
- **FR-003**: Each new commit pushed to a pull request MUST replace the previous result.
- **FR-004**: Every commit that lands on `main` MUST be analysed, and the result MUST be readable
  on the project's public page without signing in.
- **FR-005**: The analysis MUST cover the application code and its tests, and MUST leave out
  generated files, build output and third-party code.
- **FR-006**: The analysis MUST include the results and the coverage of the unit tests. Every file
  the project's existing coverage report leaves out entirely MUST be left out of coverage in the
  analysis too; an exclusion the service cannot reproduce MUST be named in the documentation. When
  the unit tests fail, coverage MUST NOT be published for that commit.
- **FR-007**: When the analysis token is not available (forks, the dependency update bot, a
  repository where it is not set), the analysis MUST be skipped, and MUST neither fail the pull
  request nor leave it waiting for a result.
- **FR-008**: A failure of the analysis MUST NOT change the result of the other checks.
- **FR-009**: A pull request whose analysis fails the quality gate, or whose analysis could not
  complete, MUST NOT be mergeable. The existing checks, including the 80% coverage gate, stay
  conditions for merging too.
- **FR-010**: The analysis token MUST be kept out of the repository and out of the logs.
- **FR-011**: The project documentation MUST say where the results are read, and what a maintainer
  has to configure for the analysis to run.
- **FR-012**: The quality gate MUST NOT fail a pull request because of the coverage of code that
  the project's existing coverage gate leaves out.
- **FR-013**: The constitution's list of checks that must pass before merging MUST name the
  analysis, amended in the same pull request as this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of pull requests whose checks can read the analysis token show an analysis
  result before they are reviewed.
- **SC-002**: The analysis adds no more than 5 minutes to the time a pull request waits for all its
  checks.
- **SC-003**: After a merge, the public page shows the analysis of the merged commit within 30
  minutes.
- **SC-004**: No file that the existing coverage report leaves out entirely is listed as uncovered
  code in the analysis.
- **SC-005**: No pull request from a fork or from the dependency update bot fails, or stays
  blocked, because of the analysis.
- **SC-006**: A maintainer who reads only the project documentation finds the analysis page, and
  the list of what must be configured, in under 2 minutes.
- **SC-007**: No pull request is merged while its analysis fails the quality gate.
- **SC-008**: No pull request that passes the existing coverage gate is refused by the quality gate
  for the coverage of UI code.

## Assumptions

- The service is SonarQube Cloud, in the `monicahq` organisation that already hosts the web app's
  project, with a public project so anybody can read the results.
- The project for the Android app does not exist there yet. A maintainer of the organisation
  creates it, bound to the repository; this feature does not create accounts or tokens.
- The analysis runs in the project's existing checks on GitHub, on pull requests and on `main`.
  Running it on a developer's machine is out of scope.
- The service's default rules and default quality gate for the app's language are used, changed
  only where FR-012 requires it. Other tuning is a later decision.
- Blocking is enforced by the repository's merge rules on GitHub, which live outside the
  repository: a maintainer adds the analysis to the checks required to merge.
- A pull request whose analysis is skipped (FR-007) can be merged without it. This is a known
  limit: a contribution from a fork is analysed only once it is on `main`.
- Coverage comes from the unit tests only, as in the existing coverage gate. Coverage of the
  instrumented tests is out of scope.
- Fixing the problems the first analysis finds in existing code is out of scope; each is handled as
  its own change.
