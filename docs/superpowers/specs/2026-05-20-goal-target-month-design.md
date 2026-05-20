# Goal Target Month Design

## Goal
Add month-level precision to financial goals while keeping existing `targetYear` API compatibility.

## Contract
- Add `targetMonth` to goal request/response payloads.
- Valid values are `1..12`.
- Create default: omitted `targetMonth` means December (`12`).
- Patch default: omitted `targetMonth` keeps current value; existing rows start as December.
- `targetYear` remains required on create and unchanged in meaning.

## Persistence
- Add `goals.target_month integer not null default 12` to the H2 schema.
- Seed demo data with explicit `target_month = 12`.

## Analytics
- Goal ordering becomes `targetYear, targetMonth, priority, id`.
- Target-cost growth remains year-based for now; month precision controls deadline/order/reachability timing.
- `recommendedMonthlyGoalContribution` becomes the current free monthly cashflow: `max(monthlyIncome - monthlyExpenses, 0)`.
- Goal allocation tracks available cash by month so a goal due in an earlier month cannot use later-month cashflow for reachability.

## Testing
- Controller tests cover create/patch/default/validation for `targetMonth`.
- Analytics tests cover monthly ordering and monthly reachability.
- OpenAPI tests cover the new schema field.
