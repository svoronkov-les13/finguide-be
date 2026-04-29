# Backend architecture with Keycloak

## Recommendation

Use **Java 21 + Spring Boot 3 + Keycloak + PostgreSQL** as a contract-first modular monolith.

Microservices are not required on day one. For a 100k registered-user target, a stateless horizontally-scaled API with PostgreSQL, Redis, async workers and read models is usually simpler and safer. Design modules as extractable bounded contexts so analytics/import/notifications can be split later.

## High-level flow

```txt
Frontend
  -> Keycloak OIDC Authorization Code + PKCE
  -> receives access_token / refresh_token
  -> Backend API with Authorization: Bearer <JWT>
  -> Backend validates JWT via Keycloak JWKS
  -> Backend checks local user + plan access
  -> PostgreSQL / Redis / object storage
```

Backend does **not** own passwords or login forms. Keycloak owns identity, credentials, MFA and sessions.

## Keycloak setup

Realm:

```txt
finguide
```

Clients:

```txt
finguide-web    public client, PKCE
finguide-api    resource server / bearer-only semantics
finguide-admin  confidential client, service account for admin automation
```

Roles:

```txt
USER
ADVISOR
ADMIN
```

Useful token claims:

```json
{
  "sub": "keycloak-user-id",
  "email": "user@example.com",
  "name": "Александр Петров",
  "realm_access": {
    "roles": ["USER"]
  }
}
```

## Backend modules

```txt
auth/              JWT adapter, current user, security config
users/             local business profile
plans/             plan ownership and access policy
incomes/           income source CRUD
expenses/          expense CRUD and budget classification
goals/             goals and waterfall priority
contributions/     actual savings contributions
pension/           pension settings, preserve-capital and spend-down projections
budget/            50/30/20 and envelopes
analytics/         Excel-model assumptions, balance, cashflow, savings, dashboard, health score
scenarios/         scenario snapshots/adjustments/compare
importexport/      JSON/CSV/XLSX/PDF jobs
notifications/     derived alerts/milestones/tips
```

## Authorization rules

Every protected request:

1. Validate JWT signature and issuer using Keycloak JWKS.
2. Extract `sub`, `email`, `roles`.
3. Find or create local user by `keycloak_subject`.
4. Check domain access, e.g. user owns/can access `planId`.

Example:

```txt
GET /plans/{planId}/dashboard
allowed if:
- token valid
- user active
- user has access to planId
```

## Local user table

Keycloak is the identity source. Backend stores business profile.

```sql
users
- id uuid primary key
- keycloak_subject text unique not null
- email text not null
- name text
- phone text
- avatar_url text
- age int
- gender text
- initial_balance numeric
- created_at timestamptz
- updated_at timestamptz
```

## Calculation architecture

The Excel workbook `Модель_P---56630d2a-6465-4036-bd42-9117c7dc9bd6.xlsx` is the reference model. Backend should reimplement it as deterministic calculation services:

```txt
PlanState + ModelAssumptions
  -> normalize amounts/rates/dates
  -> build yearly timeline
  -> apply active flags by start/end year
  -> apply yearly growth schedules
  -> calculate income/expense/goal cashflow
  -> calculate annual savings and accumulated capital
  -> calculate pension preserve-capital and spend-down variants
  -> cache snapshots for dashboard/scenarios
```

Important conventions:

- API uses positive amounts for expenses and goal outflows.
- API rates ending with `Pct` are percent points (`6` = 6%). Calculation code converts to decimal rates.
- `analytics/cashflow` is the canonical derived projection. Dashboard, health score, scenarios and notifications should derive from it instead of duplicating formulas.
- Persist raw inputs and optional snapshot outputs; do not persist every transient formula unless needed for audit/cache.

## Main persistence model

```txt
financial_plans
income_sources
expense_items
goals
goal_contributions
pension_settings
budget_settings
budget_envelopes
scenarios
monthly_tracker_entries
export_jobs
notification_events
model_assumptions
analytics_snapshots
pension_projection_snapshots
```

Common columns:

```txt
id uuid
plan_id uuid
created_at timestamptz
updated_at timestamptz
deleted_at timestamptz nullable
```

## Scaling to 100k users

100k registered users does not automatically require microservices. Start with:

```txt
Load balancer
  -> 2..N stateless backend instances
  -> PostgreSQL primary + read replica when needed
  -> Redis cache
  -> Keycloak 2+ instances backed by PostgreSQL
  -> async worker pool
```

Add:

- Redis cache for dashboard/projection snapshots derived from `analytics/cashflow`.
- Read models: `plan_dashboard_snapshot`, `plan_projection_snapshot`, `financial_health_snapshot`.
- Outbox pattern for business events.
- Async jobs for recalculation, export, notifications.
- Proper indexes and later partitioning if event/history tables grow.

## Extract later if needed

Good candidates for extraction:

```txt
analytics-worker
import-export-worker
notification-worker
```

Do **not** split core financial entities too early:

```txt
incomes / expenses / goals / pension
```

They are one consistency boundary: the financial plan.
