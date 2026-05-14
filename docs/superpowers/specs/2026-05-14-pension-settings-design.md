# Pension Settings Endpoints Design

## Goal

Implement backend issue #11: real `GET /api/v1/plans/{planId}/pension` and `PATCH /api/v1/plans/{planId}/pension` endpoints backed by persisted `pension_settings`.

## Scope

- Read persisted `PensionSettings` for an accessible plan.
- Replace persisted `PensionSettings` via PATCH for writable plans only.
- Keep percent fields as percentage points in API and storage.
- Ensure pension projection reflects updated settings immediately.
- Add real Springdoc operations and reduce OpenAPI known gap by 2.
- Update docs/status/roadmap/contract/README after implementation.

Out of scope:

- New pension calculation formulas beyond consuming updated settings.
- Partial merge-patch semantics. The OpenAPI request body references full `PensionSettings`, so this implementation treats PATCH as full replace.
- Database migrations beyond current H2 schema; the table already exists.

## API Behavior

### GET `/api/v1/plans/{planId}/pension`

Returns `{ "data": PensionSettings }` using existing mapper fields:

- `currentAge`
- `retirementAge`
- `monthlyExpenses`
- `desiredMonthlyExpensesCurrentPrices`
- `currency`
- `expectedReturnPct`
- `inflationPct`
- `withdrawalStrategy` as `preserve_capital` or `spend_down_30y`
- `statePensionEnabled`
- `statePensionMonthly`

Access uses existing plan access rules through `PlanReadService.plan(planId)` / `PlanAccessService.requirePlan`.

### PATCH `/api/v1/plans/{planId}/pension`

Accepts the same fields as a full `PensionSettings` body, validates OpenAPI constraints, persists the row, and returns the updated settings.

Access uses `PlanAccessService.requireWritablePlan(planId)`, so anonymous shared demo seed cannot be changed.

## Validation

Reject with `400 Bad Request` when:

- body is missing;
- `currentAge` is outside `16..100`;
- `retirementAge` is outside `40..80`;
- money fields are missing or negative;
- `expectedReturnPct` or `inflationPct` is missing or outside `0..30`;
- `currency` is missing or not 3 characters;
- `withdrawalStrategy` is missing.

The design intentionally does not require `retirementAge > currentAge`, because the OpenAPI contract does not define this constraint and existing projection code supports `yearsToRetirement = 0`.

## Components

- `PlanReadController`: add GET/PATCH routes.
- `PlanReadService`: add `pension(planId)`, `updatePension(planId, request)`, and validation helpers.
- `PlanStateRepository`: add `updatePensionSettings(UUID planId, PensionSettings pension)`.
- `JdbcPlanStateRepository`: update `pension_settings` row and return current persisted state.
- `PlanApiMapper`: existing `pension(PensionSettings)` is reused.
- `PlanReadControllerTests`: controller-level regression coverage for read/update/projection/validation/access.
- `OpenApiContractCoverageTests`: remove GET/PATCH pension from known missing operations.
- Docs: mark #11 done/in-progress according to final PR status and update OpenAPI coverage numbers.

## Testing

Use TDD:

1. Add failing controller tests for GET, PATCH persistence, projection change after PATCH, validation failure, anonymous write denial.
2. Add repository/service implementation until tests pass.
3. Run targeted tests.
4. Update OpenAPI coverage gap and run full test suite.
5. Update docs and run `mkdocs build --strict`.

## Release Flow

Follow the established workflow: branch/worktree, tests, docs, PR, code review, fixes, merge, cleanup.
