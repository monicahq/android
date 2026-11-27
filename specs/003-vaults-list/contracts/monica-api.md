# Contract: Monica API calls used by this feature

Source of truth: `resources/docs/api/05-vaults.php` and `app/Http/Controllers/Api/VaultController.php`
in the Monica repository. Every call goes through `ApiClient` (bearer token of the session, same
origin only, 401 renews once). No endpoint is added to Monica by this feature.

## GET /api/v1/vaults?page={n}&per_page=100

Answer 200:

```json
{
  "data": [
    {
      "type": "vault",
      "id": "9d4f1c2e-7b3a-4f88-9a2d-6c1e5b0f7a31",
      "attributes": {
        "slug": "dunder-mifflin",
        "name": "Dunder Mifflin",
        "description": "Everybody at the Scranton branch.",
        "colour": "#4f8a49",
        "position": 1,
        "is_default": true,
        "contact_count": 12,
        "shows_people": true,
        "shows_things": true,
        "shows_knowledge": false,
        "created_at": 1756252800,
        "updated_at": 1756339200
      },
      "links": { "self": "…", "web": "…" }
    }
  ],
  "meta": { "page": 1, "per_page": 100, "total": 1 }
}
```

- Read: `id`, `name`, `description`, `colour`, `is_default`, `contact_count`, `meta.total`.
  Everything else is ignored (`ignoreUnknownKeys`).
- An account with no vault answers `"data": []`, `"total": 0`.
- The app asks pages 1, 2, … until it holds `total` vaults or a page is empty.

## GET /api/v1/vaults/{id}

Answer 200: `{ "data": <vault object as above> }`. Answer 404 when the vault was deleted, belongs to
someone else or never existed → `ApiResult.NotFound`.

## POST /api/v1/vaults

Request (`Content-Type: application/json`):

```json
{ "name": "Family", "description": "Everybody I am related to.", "colour": "#4f8a49", "is_default": true }
```

`description` is omitted when blank; `is_default` is omitted unless `true`; `structure_version_id`
is never sent.

Answer 201: `{ "data": <vault object> }`.

Answer 422:

```json
{
  "message": "The free plan holds 1 vault, and this account already has it. Full access lifts the ceiling.",
  "errors": { "name": ["The free plan holds 1 vault, and this account already has it. Full access lifts the ceiling."] }
}
```

→ `ApiResult.Invalid(message, fields = {"name": "…"})`.

## Status mapping (all calls)

| Status | ApiResult |
|---|---|
| 2xx | `Success` (unparsable body → `Failed(ServerError)`) |
| 401 | renew once, then retry; second 401 → `SessionEnded` |
| 404 | `NotFound` *(new)* |
| 422 | `Invalid` *(new)*; unparsable body → `Failed(ServerError)` |
| 3xx | `Failed(Moved)` |
| 403 | `Failed(NotAllowed)` |
| other | `Failed(ServerError)` |
| network | `Failed(Unreachable / Timeout / Certificate)` |

## Test double

`FakeMonica` (unit tests) and `TestMonica` (instrumented) answer these three endpoints from an
in-memory list of vaults, starting empty, with `overrides` for errors and paging cases.
