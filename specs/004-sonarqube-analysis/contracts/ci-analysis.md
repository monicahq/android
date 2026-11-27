# Contract: the Analysis check

Names that systems outside this repository depend on. Changing one here without changing the other
side breaks the feature silently.

| Name | Value | Who depends on it | What breaks if it changes |
|---|---|---|---|
| CI job name | `Analysis` | Ruleset of `main` (required status checks, with `Format`, `Lint`, `Unit tests`, `Build`, `Instrumented tests`) | The required check never reports: no pull request can be merged |
| Sign-in | GitHub OIDC token exchanged by `sonarqube-auth-action` (job permission `id-token: write`) | The Analysis job | The sign-in step fails and the check turns red |
| Organisation | `monicahq` | SonarQube Cloud | The scan fails |
| Project key | `monicahq_android` | SonarQube Cloud; the `sonar { }` block of `app/build.gradle.kts` is the only place it is written | The scan fails, or analyses land in another project |
| Artifact | `analysis-inputs`, unpacked into `app/build` | Unit tests job (producer), Analysis job (consumer) | Coverage shows 0% and the gate refuses pull requests |

## Behaviour of the check

| Situation | Result of `Analysis` | Pull request |
|---|---|---|
| Quality gate passes | success | mergeable (for this check) |
| Quality gate fails | failure | blocked |
| Scan cannot complete (service error, timeout) | failure | blocked until a re-run succeeds |
| Fork or Dependabot pull request | success, steps skipped | mergeable without analysis |
| Unit tests fail | skipped | blocked by `Unit tests` |
| Push to `main` | success when the scan was sent; the gate is not waited for | n/a |
