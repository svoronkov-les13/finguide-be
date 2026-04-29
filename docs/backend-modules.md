# Backend modules

The backend starts as a modular monolith. Every package is a bounded context with a narrow public model/service surface, so we can extract heavy modules later without splitting the financial-plan core too early.

```txt
les13.finguide.backend
  auth/              Keycloak JWT integration, SecurityConfig, current user
  users/             local business profile synced with Keycloak identity
  plans/             financial plan aggregate and access policy
  incomes/           income source model
  expenses/          expense model and budget classification input
  goals/             goals and waterfall priority
  contributions/     actual savings contributions
  pension/           retirement settings plus preserve-capital/spend-down projections
  budget/            50/30/20 and envelopes
  analytics/         Excel-model assumptions, cashflow, balance, savings, dashboard, health score
  scenarios/         scenario snapshots/adjustments
  importexport/      import/export job boundary
  notifications/     derived alerts/milestones/tips
```

## Analytics model boundary

The uploaded Excel model is the reference for calculations. Backend owns the workbook logic, not frontend.

```txt
analytics/
  ModelAssumptions           start year, horizon/end year, inflation schedule, return, initial capital
  YearRatePoint              per-year rate schedule used by inflation and line growth
  BalanceSnapshot            current-year income/outflow/net split from workbook sheet Баланс
  CashFlowProjectionPoint    yearly income, expenses, goals, savings, accumulated capital
  ProjectionCalculator       reproduces sheets Доходы / Расходы / Цели / Сбережения
  DashboardCalculator        derives card metrics from projection outputs
pension/
  PensionProjection          preserves capital and spend-down variants from sheet Пенсия
  PensionSpendDownPoint      yearly retirement depletion row
```

Inputs should stay normalized and user-friendly: expenses/goals are positive outflow amounts in API. The calculation layer converts signs internally and exposes positive outflows + `netSavings`.

## Extraction candidates later

If load or team ownership requires it, extract these first:

- `analytics` -> analytics worker/service when recalculation becomes heavy or async
- `importexport` -> export worker/service
- `notifications` -> notification worker/service

Keep `plans + incomes + expenses + goals + pension` together while financial calculations require strong consistency.
```
