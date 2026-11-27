# Research: Vault List

All decisions below resolve the plan's Technical Context. None is left open.

## R1. Where API calls live (the "Laravel Actions and controllers" question)

**Decision**: Three layers, each with one job. The Laravel equivalent of each is given to show the
mapping:

| Laravel | This app | Job |
|---|---|---|
| HTTP kernel, middleware, `Http::withToken()` | `network/ApiClient` | The only class that sends a request: adds the token, renews it on 401, refuses other origins, discards answers after a sign-out or an instance switch, turns status codes into an `ApiResult`. Knows no endpoint except `/me`. |
| Actions (one class per thing the domain does) | `vault/VaultRepository` (one per resource) | Knows the vault endpoints (`GET /vaults`, `GET /vaults/{id}`, `POST /vaults`), the JSON shapes, and pagination. Returns app types (`Vault`), never JSON. |
| Controller | `ViewModel` (one per screen) | Receives the screen's events, calls the repository, exposes one `StateFlow<UiState>`. No HTTP, no JSON. |
| Blade view + its ViewModel | `UiState` + stateless `*Screen` composable | Shows the state, sends events up. |

`ApiClient` gains two generic methods, `get(path, parse)` (made `internal` instead of private) and
`post(path, body, parse)`, so a repository writes one line per endpoint. Endpoint knowledge never
goes in `ApiClient`, which keeps it from becoming a god object as features arrive (contacts,
reminders, ... each get their own repository).

**Rationale**: This is the official Android architecture (UI layer + data layer) already used in
`docs/architecture.md`. A repository per resource gives the "everything clean in its place" the
person likes in Laravel, without a class per endpoint.

**Alternatives considered**:
- *One "Action"/use case class per call* (`ListVaults`, `CreateVault`): the Android name is a
  domain-layer use case. Rejected for now: the constitution (Principle I, II) allows a use case only
  when the same logic is shared by several ViewModels. When it happens (e.g. "create a vault" from
  two screens with extra rules), a use case is added then.
- *Retrofit interface per resource*: a new library for three calls, and it would bypass the
  isolation, renewal and generation logic `ApiClient` already guarantees. Rejected.
- *All endpoints as methods of `ApiClient`*: simplest today, but every feature would grow the same
  file. Rejected.

## R2. Errors the vault endpoints can answer

**Decision**: `ApiResult` gains two cases:
- `Invalid(message: String, fields: Map<String, String>)` for 422. Monica answers every refusal as
  JSON with a `message`, and validation adds `errors` keyed by field (first message kept per field).
  The plan ceiling arrives this way on `name` (`VaultController::create` turns the policy denial
  into a validation error). The texts are the instance's own, in the account's language.
- `NotFound` for 404 (a vault deleted, or never there: Monica answers 404 for both).

Other codes keep their current mapping (`Failed(AuthError)`).

**Rationale**: FR-015 requires the instance's own message on the right field; the edge case "vault
deleted on the web" requires telling 404 apart from a server failure.

**Alternatives considered**: mapping 422 to a generic `AuthError` (loses the instance's message);
a separate error type per repository (two error vocabularies for the same HTTP answers).

## R3. Pagination

**Decision**: `VaultRepository.vaults()` asks `per_page=100` (the API maximum) and keeps asking the
next page until it holds `meta.total` vaults or a page comes back empty. The result is one list in
the server's order.

**Rationale**: FR-002 (every vault, in order). Almost every account fits in one call; a lazy
"load more" adds states for a case that barely exists. The empty-page guard stops a loop if vaults
are deleted while paging.

## R4. Navigation

**Decision**: Add Navigation Compose (`androidx.navigation:navigation-compose` 2.10.2) with
type-safe `@Serializable` routes: `VaultsDestination`, `VaultDestination(id)`,
`NewVaultDestination(hasVaults)`. ViewModels get route arguments through their factory.
The `NavHost` lives in `SignedInApp`, shown by `MainActivity` while `SessionState.SignedIn`.
Signing out drops the whole signed-in tree, so no back stack survives an instance switch.

**Rationale**: This feature introduces the first navigation (list → vault, list → form) and the next
features (vault screen, contacts) deepen it. The constitution names Navigation Compose with
type-safe routes as the preferred library. kotlinx.serialization is already in the project.

**Alternatives considered**:
- *Hand-made back stack* (`rememberSaveable` list + `BackHandler`): fine for three screens, but
  re-implements predictive back, saved state and per-destination `ViewModel` scoping that every
  next feature will need. Rejected.
- *Navigation 3*: Google's newer library with a back stack the app owns. Rejected for now because
  the constitution names Navigation Compose; switching would need an amendment, and nothing here
  needs what Navigation 3 adds (adaptive multi-pane layouts).

## R5. Refreshing the list after creating a vault or coming back

**Decision**: `VaultsViewModel.onShown()` is called each time the list screen is resumed
(`LifecycleResumeEffect`). The first call loads; later calls reload quietly, keeping the list on
screen. A quiet reload that fails changes nothing; only a pull to refresh reports a failure.

**Rationale**: One mechanism covers three needs: the new vault appears after creation (FR-017), a
vault deleted on the web disappears after coming back from its screen (edge case), and returning to
the app shows fresh data. No result passing between destinations, no app-wide cache to clear on
instance switch.

**Alternatives considered**: a navigation result ("created") read from `SavedStateHandle` (covers
only creation); an in-memory list in the repository observed by the ViewModel (a shared cache that
must be cleared on every session change to keep instances isolated).

## R6. Vault colours in light and dark (FR-020)

**Decision**: `ui/theme/VaultColours.kt` maps each of the eight server colours to a pair
(initial colour, square tint) for light and for dark, measured with WCAG's formula:

| Server colour | Light initial | Light tint | Ratio | Dark initial | Dark tint | Ratio |
|---|---|---|---|---|---|---|
| `#3f5fd0` | `#3f5fd0` | `#e8ecf9` | 4.73 | `#97a9e6` | `#333f5b` | 4.53 |
| `#4f8a49` | `#447740` | `#eaf1e9` | 4.63 | `#8eb48b` | `#314249` | 4.54 |
| `#9a6a24` | `#8d6122` | `#f3ede5` | 4.66 | `#c2a67c` | `#3c3f46` | 4.55 |
| `#9a3f2b` | `#9a3f2b` | `#f3e8e6` | 5.60 | `#cc9f95` | `#3e3d4b` | 4.53 |
| `#6b4fa8` | `#6b4fa8` | `#edeaf5` | 5.34 | `#b2a3d2` | `#383e57` | 4.54 |
| `#2f7f86` | `#2c747b` | `#e6f0f0` | 4.62 | `#82b2b6` | `#2f4151` | 4.50 |
| `#a8447a` | `#a8447a` | `#f5e9ef` | 4.70 | `#d09ab7` | `#3e3c51` | 4.54 |
| `#4a4a52` | `#4a4a52` | `#e9e9ea` | 7.25 | `#aaaaae` | `#374050` | 4.53 |

Light: tint is 12% of the colour on white (the web's recipe); green, ochre and teal are darkened
toward ink just enough to pass 4.5:1, since the 16 sp initial is not "large text". Dark: the colour
is lifted toward white and the tint is 20% of it on the dark card (`#1A2538`). An unknown colour
uses the `#4a4a52` row. The same initial colour fills the colour swatches of the creation form
(well above 3:1 against both backgrounds: 5.3:1 or more on the light page, 6.5:1 or more on the dark page and card).

**Rationale**: The design's `color-mix` recipe fails 4.5:1 for three colours in light and for all in
the app's night-blue dark. Fixed, measured values are simpler and testable (a unit test recomputes
every ratio).

**Alternatives considered**: computing mixes at runtime (same result, harder to read and review);
white initials on the full colour (the web form's preview does this, but `#4f8a49` falls to 4.15:1
with white).

## R7. Initials

**Decision**: A pure function file `vault/Initials.kt`:
- vault initial: the first grapheme of the trimmed name, uppercased for the locale (the web uses
  `grapheme_substr` then `mb_strtoupper`);
- person initials: first grapheme of the first two words that start with a letter, uppercased; none
  → `null`, and the account button shows the person icon (User Story 4, scenario 3).

Graphemes come from `java.text.BreakIterator`, so accented letters and emoji are never split.

## R8. Icons

**Decision**: Vector drawables in `res/drawable`, as the app already does (`ic_logo`, `ic_settings`):
`ic_plus`, `ic_vault`, `ic_person`, `ic_check`, `ic_arrow_back`, `ic_close`, drawn in the design
system's style (16 dp grid, 1.4–1.8 stroke, round caps). No icon library.

**Rationale**: four icons do not justify `material-icons-extended`, and the design system's own
glyphs keep the app consistent with the web.

## R9. Pull to refresh and its alternative

**Decision**: Material 3 `PullToRefreshBox` around the list. The list also carries a custom
accessibility action "Refresh" (TalkBack and Switch Access menus). On the error state, "Try again"
is a regular button.

**Rationale**: Principle VII requires an alternative to every gesture; a custom action costs one line
and is reachable from every assistive technology. A visible refresh button would add something the
design does not have.

## R10. Account panel

**Decision**: `HomeViewModel` becomes `account/AccountViewModel` (name, initials, instance, sheet
open, sign-out confirmation), and `HomeScreen` becomes `AccountSheet`, a `ModalBottomSheet` opened
by the account button. The home's `me()` check on opening is dropped: the vault list's first load
detects an ended session or an unreachable instance the same way and already shows it. The instance sheet (`InstanceSheetRoute`) is reused
as is. The `home/` package disappears.

**Rationale**: Keeps every behaviour already tested in `HomeViewModelTest` while moving it where the
design puts it. No new logic.

## R11. Creating a vault: the form's rules

**Decision**:
- Name required (trimmed not blank), `maxLength` 255 enforced in the field; description optional;
  colour defaults to `#3f5fd0` (the API default).
- "Make this the default vault" is offered only when `NewVaultRoute.hasVaults` is true; sent as
  `is_default: true` only when ticked.
- No template choice: the API starts the vault from the account's default contact template.
- While saving, the button is disabled and shows progress; a second tap cannot create a duplicate.
- `Invalid` → field messages on `name`/`description`/`colour` (any other key goes to a general
  message above the button), values kept. `Failed` → the connection message, values kept.
- Leaving with text typed asks "Discard this vault?" (system back, top bar back, Cancel).

**Rationale**: Mirrors `resources/views/app/vaults/form.blade.php` and `VaultController::create`;
the ceiling is the instance's to enforce (constitution: no re-implemented server rules).
