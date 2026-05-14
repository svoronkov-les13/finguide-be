# Budget and Monthly Tracker Design

## Goal

Implement backend issue [#12](https://github.com/svoronkov-les13/finguide-be/issues/12): persisted budget settings/envelopes and a simple monthly savings tracker in the real Spring Boot backend.

## Endpoints

Implement the five OpenAPI contract operations currently missing from Springdoc:

- `GET /api/v1/plans/{planId}/budget`
- `PATCH /api/v1/plans/{planId}/budget`
- `POST /api/v1/plans/{planId}/budget/envelopes/autogenerate`
- `GET /api/v1/plans/{planId}/calendar/monthly-tracker?year=YYYY`
- `POST /api/v1/plans/{planId}/calendar/monthly-tracker`

This should reduce the known OpenAPI gap from 19 operations to 14.

## Budget model

`BudgetSettings` remains the response shape already used in `PlanApiMapper`:

- `method`: `503020` or `envelope`
- `envelopes[]`: `id`, `name`, `limit`, `icon`, `color`, plus computed `spent`, `remaining`, `pct`, `isOver`
- `classifications`: `{ expenseId: needs|wants|savings }`

Persistence uses H2 tables:

- `budget_settings(plan_id, method, created_at, updated_at)`
- `budget_envelopes(id, plan_id, name, limit_amount, icon, color, sort_order, created_at, updated_at)`
- `budget_classifications(plan_id, expense_id, budget_class, created_at, updated_at)`

`GET /budget` reads persisted settings. If a plan has no row yet, the repository returns the default existing in API today: `method=503020`, no envelopes, no classifications.

`PATCH /budget` is a full replace for this H2 iteration: replace method, envelopes, and classifications in one transaction. It validates method, non-negative envelope limits, non-blank envelope names, and classification expense ids belonging to the same plan.

## Envelope autogeneration

Use the user-approved option B: generate one budget envelope for each persisted expense category.

Rules:

- Requires writable plan access.
- Deletes existing envelopes for the plan and replaces them with generated envelopes.
- Keeps existing `method` and classifications if present; if no settings exist, creates `method=503020`.
- Order is deterministic: current persisted expense order.
- Envelope fields:
  - `id`: generated UUID
  - `name`: expense name
  - `limit`: expense amount
  - `icon`: stable default by `budgetClass` (`home` for needs, `sparkles` for wants, `piggy-bank` for savings)
  - `color`: stable default by `budgetClass` (`#2563EB`, `#A855F7`, `#16A34A`)
- Computed fields on read:
  - `spent`: sum of monthly expenses whose `name` matches the envelope name, or 0 if no match
  - `remaining`: `max(limit - spent, 0)`
  - `pct`: `spent / limit * 100`, or 0 when limit is 0
  - `isOver`: `spent > limit`

The name-based `spent` calculation is intentionally simple for the H2/demo stage because envelopes are generated from expense names and the current contract has no explicit `expenseId` on envelopes. Future production schema can add stable links if needed.

## Monthly tracker model

Use the user-approved option A: simple monthly status entries.

Persist entries in `monthly_tracker_entries`:

- `plan_id`
- `month` as `YYYY-MM`
- `status`: `completed`, `partial`, `missed`
- `note`
- `created_at`, `updated_at`

`GET /calendar/monthly-tracker?year=YYYY` returns only persisted entries for that year, ordered by month ascending:

```json
{
  "data": [
    { "month": "2026-05", "status": "completed", "note": "On track", "updatedAt": "..." }
  ]
}
```

If `year` is omitted, use the current UTC year. The endpoint returns only stored rows, not synthetic empty months.

`POST /calendar/monthly-tracker` upserts one month and returns `204 No Content`. Request body:

```json
{
  "month": "2026-05",
  "status": "completed",
  "note": "On track"
}
```

Validation:

- `month` must match `YYYY-MM` and month number 01-12.
- `status` must be `completed`, `partial`, or `missed`.
- `note` is optional.

## Access rules

- Budget and tracker reads call `PlanAccessService.requirePlan(planId)`.
- Budget patch, envelope autogenerate, and tracker upsert call `PlanAccessService.requireWritablePlan(planId)`.
- Anonymous shared seed remains read-only for writes.
- Authenticated users can mutate only their own current plan.

## Tests

Use TDD with MockMvc integration tests:

- Budget happy path: GET default, PATCH settings, GET persisted settings, autogenerate envelopes from expenses.
- Budget validation: invalid method/classification/negative limit returns `400`.
- Budget access: anonymous seed write returns `403`; another user's plan read returns `403`.
- Monthly tracker happy path: POST upsert, GET by year, POST same month updates status/note.
- Monthly tracker validation: bad month/status returns `400`.
- OpenAPI coverage: remove five operations from known missing set; extend endpoint OpenAPI smoke tests.

## Documentation

Update README and MkDocs pages:

- `docs/status.md`
- `docs/roadmap.md`
- `docs/contract.md`
- `docs/database.md`
- `docs/index.md`

Record `#12` as done after implementation and note OpenAPI gap `19 -> 14`.

## Non-goals

- No frontend changes.
- No pagination for monthly tracker in this PR.
- No idempotency key support in this PR.
- No ETag/version support in this PR.
- No production PostgreSQL/Flyway migration in this PR.
