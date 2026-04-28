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
  pension/           retirement assumptions and settings
  budget/            50/30/20 and envelopes
  analytics/         dashboard, projections, health score calculators
  scenarios/         scenario snapshots/adjustments
  importexport/      import/export job boundary
  notifications/     derived alerts/milestones/tips
```

## Extraction candidates later

If load or team ownership requires it, extract these first:

- `analytics` -> analytics worker/service
- `importexport` -> export worker/service
- `notifications` -> notification worker/service

Keep `plans + incomes + expenses + goals + pension` together while financial calculations require strong consistency.
```
