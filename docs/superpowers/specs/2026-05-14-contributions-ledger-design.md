# Contributions Ledger Design

## Context

Issue [#10](https://github.com/svoronkov-les13/finguide-be/issues/10) adds persisted contribution tracking for goal deposits. The checked-in OpenAPI contract already defines five contribution operations:

- `GET /api/v1/plans/{planId}/contributions`
- `POST /api/v1/plans/{planId}/contributions`
- `GET /api/v1/plans/{planId}/contributions/{id}`
- `PATCH /api/v1/plans/{planId}/contributions/{id}`
- `DELETE /api/v1/plans/{planId}/contributions/{id}`

Current backend state has a `Contribution` record and mapper support, but no persisted table, repository methods, service methods, or controller endpoints. `PlanState` currently returns an empty contribution list.

## Product Decision

The contribution ledger is the source of truth for goal progress.

`goals.savedAmount` must be recalculated as `sum(contributions.amount)` for each affected goal after contribution create, update, or delete. Existing seeded goal progress becomes `0` until contributions exist. This is intentional and avoids maintaining two independent saved-amount sources.

## API Behavior

All endpoints use existing `ApiEnvelope` response style.

- `GET /plans/{planId}/contributions` returns all contributions for the plan, ordered by `date desc, createdAt desc`.
- `POST /plans/{planId}/contributions` creates a contribution and returns `201` with the persisted contribution.
- `GET /plans/{planId}/contributions/{id}` returns one contribution scoped to the plan.
- `PATCH /plans/{planId}/contributions/{id}` partially updates mutable fields and returns the updated contribution.
- `DELETE /plans/{planId}/contributions/{id}` deletes the contribution and returns `204`.

## Access Rules

- Reads call `PlanAccessService.requirePlan(planId)`.
- Mutations call `PlanAccessService.requireWritablePlan(planId)` so the shared anonymous demo seed remains read-only.
- Contribution lookup is always scoped by both `planId` and `contributionId`; a contribution from another plan must not be visible or mutable.
- `goalId` must belong to the same `planId`.

## Validation

Create requires:

- `goalId`: required UUID and existing goal in this plan.
- `amount`: required and `>= 0`.
- `currency`: optional? No. Required and must match `[A-Z]{3}`.
- `date`: required.
- `note`: optional text.

Patch is partial:

- Missing fields keep current values.
- If `goalId` is supplied, it must belong to the same plan.
- If `amount` is supplied, it must be `>= 0`.
- If `currency` is supplied, it must match `[A-Z]{3}`.
- If `date` is supplied, it must be non-null.

## Persistence

Add `contributions` table to `schema.sql`:

- `id uuid primary key`
- `plan_id uuid not null references financial_plans(id)`
- `goal_id uuid not null references goals(id)`
- `amount numeric(19,2) not null`
- `currency varchar(3) not null`
- `contribution_date date not null`
- `note varchar(1024)`
- `created_at timestamp with time zone not null`
- `updated_at timestamp with time zone not null`

When cloning a seed plan for an authenticated user, clone contributions after goals. Because cloned goal ids are new, contribution cloning must map old goal ids to new goal ids. The first implementation may clone no seed contributions because the seed has none, but the repository should be structured so cloning can support seeded contributions safely.

## Repository / Service Shape

Extend `PlanStateRepository` with contribution-specific methods:

- `findContributions(UUID planId)`
- `findContribution(UUID planId, UUID contributionId)`
- `createContribution(UUID planId, Contribution contribution)`
- `updateContribution(UUID planId, Contribution contribution)`
- `deleteContribution(UUID planId, UUID contributionId)`
- `recalculateGoalSavedAmount(UUID planId, UUID goalId)`

Implement orchestration in a focused `ContributionService` rather than adding more responsibilities to `FinancialItemService`.

## Consistency Rules

- After create: recalculate the new contribution's `goalId`.
- After update: recalculate old `goalId`; if `goalId` changed, also recalculate new `goalId`.
- After delete: recalculate deleted contribution's `goalId`.
- Recalculation query uses `coalesce(sum(amount), 0)` scoped by `plan_id` and `goal_id`.

## OpenAPI / Docs

`OpenApiContractCoverageTests` should remove the five contribution operations from the known gap. The known OpenAPI gap should shrink from 24 to 19 operations.

Update README/MkDocs status/contract/roadmap/database docs to mark #10 done and contributions persisted.

## Testing

Use TDD with controller/integration tests first:

- create contribution, read it back, list includes it.
- patch amount/date/note and verify persistence.
- patch `goalId` and verify old/new goals are recalculated.
- delete contribution and verify `204` plus goal saved amount returns to `0`.
- invalid request returns `400`.
- unknown contribution returns `404`.
- another user's plan is inaccessible.
- anonymous demo mutation returns `403`.
- OpenAPI coverage gap is reduced by 5.

## Non-goals

- No pagination implementation in this issue, despite contract discussion in docs.
- No multi-currency conversion. Contributions are stored in their stated currency; goal progress sum assumes same-currency values and validation does not enforce equality with goal currency in this slice.
- No frontend work.
