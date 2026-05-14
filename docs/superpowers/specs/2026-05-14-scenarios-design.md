# Scenario Management Design

## Goal

Implement backend issue [#13](https://github.com/svoronkov-les13/finguide-be/issues/13): user-created scenario CRUD plus deterministic scenario comparison, without breaking the existing read-only built-in scenario list.

## Chosen approach

Use a hybrid model:

- Current implementation persists user scenarios as adjustment deltas only.
- Database schema is intentionally extensible for future snapshot scenarios, but this PR does not implement plan snapshots.
- Built-in `base`, `optimistic`, and `pessimistic` scenarios remain read-only and are not stored as user rows.

This keeps the PR small enough for the current H2/demo backend while preserving a future path to snapshot-based what-if planning.

## Endpoints

Implement the five missing OpenAPI operations:

- `POST /api/v1/scenarios`
- `GET /api/v1/scenarios/{scenarioId}`
- `PATCH /api/v1/scenarios/{scenarioId}`
- `DELETE /api/v1/scenarios/{scenarioId}`
- `POST /api/v1/scenarios/compare`

Existing `GET /api/v1/scenarios` remains available and changes from built-in-only to built-in plus user-created scenarios for the current plan.

Expected OpenAPI gap: `14 -> 9`.

## Data model

Add table `scenarios`:

```sql
create table scenarios (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  name varchar(120) not null,
  emoji varchar(16),
  description varchar(1024),
  is_base boolean not null default false,
  income_adj_pct numeric(9, 4) not null,
  expense_adj_pct numeric(9, 4) not null,
  return_adj_pct numeric(9, 4) not null,
  inflation_adj_pct numeric(9, 4) not null,
  retirement_age_shift integer not null,
  goals_cost_adj_pct numeric(9, 4) not null,
  snapshot_json clob,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);
```

`is_base` remains `false` for user-created rows in this PR. `snapshot_json` is nullable and unused for now; it is the reserved extension point for future snapshot scenarios.

Domain shape reuses existing `Scenario` and `Scenario.Adjustments`:

- `id`
- `basePlanId`
- `name`
- `emoji`
- `description`
- `base`
- `adjustments`
- `createdAt`
- `updatedAt`

Built-ins are generated in service code with stable ids/keys and `base=true`:

- `base`: zero adjustments
- `optimistic`: positive income/return, lower inflation/retirement age as already exposed today
- `pessimistic`: lower income/return, higher expenses/inflation/retirement age as already exposed today

## Access rules

- Current plan is resolved via `PlanReadService.currentPlan()` for unscoped `/scenarios` endpoints.
- Listing scenarios returns built-ins plus rows for the current plan.
- Creating scenarios requires writable access to the current plan via `PlanAccessService.requireWritablePlan(planId)`.
- Reading a scenario:
  - built-ins are readable by key/id;
  - user scenario must belong to current plan, otherwise return 404 without leaking ownership.
- Patching/deleting:
  - user scenario requires writable access to its plan;
  - built-ins are read-only and return 403.
- Anonymous shared seed can read built-ins but cannot create/update/delete user scenarios.

## Request and response semantics

### Create

`POST /api/v1/scenarios`

Request:

```json
{
  "name": "Aggressive savings",
  "emoji": "🚀",
  "description": "Higher income and lower expenses",
  "adjustments": {
    "incomeAdjPct": 10,
    "expenseAdjPct": -5,
    "returnAdjPct": 1,
    "inflationAdjPct": -1,
    "retirementAgeShift": -2,
    "goalsCostAdjPct": 0
  }
}
```

Response: `201 { "data": Scenario }`.

### Read

`GET /api/v1/scenarios/{scenarioId}`

`scenarioId` accepts either a UUID for user scenarios or built-in key `base`, `optimistic`, `pessimistic`.

### Patch

`PATCH /api/v1/scenarios/{scenarioId}`

Partial update for user scenarios. Fields omitted from request keep current values. `adjustments` is also partial at field level.

Built-in scenarios return `403`.

### Delete

`DELETE /api/v1/scenarios/{scenarioId}`

Deletes user scenario and returns `204`. Built-in scenarios return `403`.

### Compare

`POST /api/v1/scenarios/compare`

Request:

```json
{
  "scenarioIds": ["base", "optimistic", "550e8400-e29b-41d4-a716-446655440000"]
}
```

Rules:

- Minimum 1 scenario, maximum 10.
- Unknown scenario returns 404.
- Foreign user scenario returns 404 to avoid ownership leakage.
- Built-ins are accepted by key.

Response:

```json
{
  "data": {
    "scenarios": [
      {
        "scenarioId": "base",
        "name": "Базовый",
        "finalCapital": 123456789,
        "minCapital": 2500000,
        "retirementYear": 2053,
        "capitalAtRetirement": 45678900,
        "goalCoveragePct": 100,
        "projection": [
          {
            "year": 2026,
            "age": 33,
            "totalIncome": 4140000,
            "totalExpenses": 1788000,
            "totalGoalExpenses": 0,
            "netSavings": 2352000,
            "capitalEndOfYear": 5000000
          }
        ]
      }
    ]
  }
}
```

## Compare calculation

Use the same projection engine used by `GET /plans/{planId}/analytics/cashflow`.

For each scenario:

1. Load current persisted `PlanState` for the current plan.
2. Apply adjustment deltas in memory:
   - incomes amount and growth: `+ incomeAdjPct` where relevant;
   - expenses amount and growth: `+ expenseAdjPct`;
   - investment return: `investmentReturnPct + returnAdjPct`;
   - inflation schedule rates and pension inflation: `+ inflationAdjPct`;
   - pension retirement age: `+ retirementAgeShift`;
   - goal current cost/planned amount: `+ goalsCostAdjPct`.
3. Run projection with adjusted state.
4. Build deterministic summary:
   - `finalCapital`: last projection `capitalEndOfYear`;
   - `minCapital`: minimum `capitalEndOfYear` across projection;
   - `retirementYear`: `startYear + (retirementAge - currentAge)`;
   - `capitalAtRetirement`: projection point at retirement year, or nearest last point;
   - `goalCoveragePct`: percentage of goal costs coverable by positive projected capital, capped at 100.

Adjustment math is additive for percentage-point fields and multiplicative for amounts: `amount * (1 + pct / 100)`.

## Validation

- `name`: required, non-blank, max 120 chars.
- `emoji`: optional, max 16 chars.
- `description`: optional, max 1024 chars.
- Adjustment pct fields: `-100..1000`.
- `retirementAgeShift`: `-20..20`.
- Compare request: 1..10 scenario ids.
- Reject duplicate scenario ids in compare with `400`.
- Keep error envelopes consistent with existing API behavior.

## Tests

Use TDD with MockMvc integration tests:

- `GET /scenarios` includes built-ins and persisted user scenarios.
- `POST /scenarios` creates a user scenario for authenticated current plan.
- `GET/PATCH/DELETE /scenarios/{scenarioId}` works for user scenarios.
- Built-in scenario read works; built-in patch/delete returns `403`.
- Anonymous seed writes return `403`.
- Foreign user scenario read/write returns `404` or `403` without ownership leakage; prefer `404` for reads and `403` for writes if access service semantics force it.
- Validation for bad name, out-of-range adjustments, duplicate compare ids, and too many compare ids returns `400`.
- `POST /scenarios/compare` returns deterministic summaries and projections for built-in and user scenarios.
- OpenAPI coverage removes the five #13 operations from the known gap.

## Documentation

Update:

- `README.md`
- `docs/index.md`
- `docs/status.md`
- `docs/roadmap.md`
- `docs/contract.md`
- `docs/database.md`

Record #13 done after merge and OpenAPI gap `14 -> 9`.

## Non-goals

- No frontend changes in this PR.
- No snapshot scenario behavior beyond nullable schema extension.
- No scenario sharing between users.
- No pagination for scenarios.
- No idempotency-key persistence in this PR.
- No PostgreSQL/Flyway production migration in this H2/demo iteration.
