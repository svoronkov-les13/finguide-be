# Pension Required Capital Design

**Date:** 2026-09-16  
**Status:** Approved for planning

## Problem

The Pension page labels `dashboard.projectedPensionCapital` as the capital required to fund the user's retirement spending. That dashboard value is actually an accumulation estimate:

```text
initial capital + current free cashflow * 12 * years to retirement
```

It does not depend on desired retirement spending or the expected post-retirement investment return. As a result, changing either field and recalculating updates the form and related labels but leaves the displayed required capital unchanged.

The concepts must remain separate:

- **projected pension capital**: how much the current plan is expected to accumulate;
- **required pension capital**: how much capital is needed at retirement to fund the selected withdrawal strategy.

## Decision

The backend is the canonical source for required-capital calculations. Extend `GET /plans/{planId}/pension/projection`; do not redefine `dashboard.projectedPensionCapital` and do not put production pension formulas in the frontend.

The two supported strategies are:

- `preserve_capital`: fund retirement spending without consuming principal;
- `spend_down_30y`: consume capital over exactly 30 years after retirement.

The frontend displays the required capital for the persisted selected strategy after the user presses Calculate and the updated plan is reloaded.

## Calculation Inputs

Use the existing persisted pension settings:

- `retirementAge` and `currentAge`;
- `desiredMonthlyExpensesCurrentPrices`;
- `expectedReturnPct`;
- `inflationPct`;
- `statePensionEnabled` and `statePensionMonthly`;
- `withdrawalStrategy`.

Define:

```text
yearsToRetirement = max(0, retirementAge - currentAge)
inflationFactor = (1 + inflationPct / 100) ^ yearsToRetirement
desiredAnnualAtRetirement = desiredMonthlyExpensesCurrentPrices * 12 * inflationFactor
statePensionAnnualAtRetirement =
  statePensionEnabled ? statePensionMonthly * 12 * inflationFactor : 0
netAnnualNeedAtRetirement = max(0, desiredAnnualAtRetirement - statePensionAnnualAtRetirement)
```

This treats both desired spending and state pension as current-price inputs indexed by the same pension inflation assumption, matching the existing projection behavior.

### Preserve Capital

Use the product's existing definition of real return:

```text
realReturnPct = expectedReturnPct - inflationPct
requiredCapitalAtRetirement = netAnnualNeedAtRetirement / (realReturnPct / 100)
```

Rules:

- If `netAnnualNeedAtRetirement` is zero, required capital is zero.
- If `netAnnualNeedAtRetirement` is positive and `realReturnPct <= 0`, the calculation status is `non_positive_real_return` and required capital is `null`.
- Otherwise the status is `calculated` and the result is rounded to two decimal places.

### Spend Down Capital

Use a fixed 30-year retirement period. Calculate the starting capital by reversing the existing annual spend-down recurrence so the result reaches zero after year 30:

```text
required = 0
for year from 29 down to 0:
  withdrawal = netAnnualNeedAtRetirement * (1 + inflationPct / 100) ^ year
  required = (required + withdrawal) / (1 + expectedReturnPct / 100)
requiredCapitalAtRetirement = required
```

This matches the existing convention where annual investment return is applied before that year's retirement expense is subtracted.

## API Contract

Extend the existing pension projection response without removing or changing current fields:

```json
{
  "preserveCapital": {
    "requiredCapitalAtRetirement": 25000000,
    "requiredCapitalStatus": "calculated"
  },
  "spendDown": {
    "requiredCapitalAtRetirement": 18000000
  }
}
```

`requiredCapitalStatus` has two values:

- `calculated`;
- `non_positive_real_return`.

Update the OpenAPI schema and regenerate the TypeScript client. Existing pension projection consumers remain compatible because the change is additive.

The existing `spendDown.retirementYears` and `spendDown.series` fields continue to follow the configured projection/chart horizon. The new required-capital field alone uses the fixed 30-year funding period. This avoids changing the behavior of existing projection consumers.

## Frontend Data Flow

1. `backendPlanClient.readBackendPlan()` fetches `/pension/projection` with the other plan reads.
2. The mapped `FinancialPlan` exposes the pension projection separately from `dashboardSnapshot.pensionCapitalRub`.
3. `PensionPage` selects `preserveCapital.requiredCapitalAtRetirement` or `spendDown.requiredCapitalAtRetirement` from the persisted withdrawal strategy.
4. Pressing Calculate continues to persist pension settings, then reloads the plan and projection through the existing mutation path.
5. Remove the hard-coded `80_330_049` fallback. A missing result renders an explicit unavailable state.
6. For `non_positive_real_return`, show that preserving capital cannot fund positive spending under the selected return and inflation assumptions.

The existing pension cashflow remains the chart source. It is already reloaded after settings are saved and should react to the updated spending and return assumptions.

## Mock Behavior

The in-memory mock must expose the same pension projection shape and recompute required capital when pension settings change. Keep this implementation isolated to mock support; production UI logic must only consume projection values and must not calculate them.

## Tests

### Backend

- Preserve-capital required capital increases when desired spending increases.
- Preserve-capital required capital decreases when expected return increases.
- Enabled state pension decreases required capital.
- Positive need with non-positive real return returns `null` plus `non_positive_real_return`.
- Spend-down required capital funds exactly 30 projected years and ends at zero within rounding tolerance.
- Existing spend-down series length continues to follow the configured projection horizon.
- Spend-down required capital increases with spending and decreases with return.
- Controller and OpenAPI tests cover the additive response fields.

### Frontend

- The backend adapter requests and maps `/pension/projection`.
- PensionPage uses the strategy-specific required capital rather than dashboard projected capital.
- Changing spending and return, then calculating, updates the displayed required capital.
- Switching strategies displays the corresponding required-capital value after persistence.
- Non-positive real return renders the unavailable explanation.
- No magic-number fallback remains.

## Non-Goals

- Do not change the dashboard accumulation estimate in this work.
- Do not change income, expense, goal, or scenario projection formulas.
- Do not add a configurable retirement duration; `spend_down_30y` remains fixed at 30 years.
- Do not redesign the Pension page beyond the states required to display the correct result.
