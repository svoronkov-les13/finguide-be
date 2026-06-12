# FinGuide Backend

Backend for **FinGuide / «Финансовый капитал»**: contract-first Spring Boot API, persisted demo plan state, Keycloak/OIDC auth boundary and canonical financial calculations.

## Links

- Docs / GitHub Pages: https://svoronkov-les13.github.io/finguide-be/
- Frontend: https://finguide.les13.tech/fg/
- Real API base: https://finguide.les13.tech/finguide-api/api/v1
- Swagger UI: https://finguide.les13.tech/finguide-api/swagger-ui.html
- Real OpenAPI JSON: https://finguide.les13.tech/finguide-api/v3/api-docs
- Keycloak realm: https://finguide.les13.tech/auth/realms/finguide
- Keycloak admin: https://finguide.les13.tech/auth/admin/master/console/
- Roadmap issue: https://github.com/svoronkov-les13/finguide-be/issues/27
- GitHub Project: https://github.com/orgs/svoronkov-les13/projects/5/views/1

Legacy mock artifacts remain in the repository only for transition checks; they are not part of the current public deployment contract.

## Stack

- Java 21
- Spring Boot 3.3
- Spring Web / Validation / Actuator
- Spring Security OAuth2 Resource Server
- Spring Data JDBC
- Springdoc OpenAPI
- Embedded H2 in PostgreSQL compatibility mode for local/demo development
- PostgreSQL 16 in Kubernetes for the `prod` profile
- Liquibase SQL migrations
- Keycloak 26 + PostgreSQL in Kubernetes

## Current implementation

Real backend currently supports:

- `GET /api/v1` API index;
- `GET /api/v1/me`;
- `GET /api/v1/plans/current`;
- plan dashboard, health, cashflow and persisted scenario CRUD/compare;
- CRUD for incomes, expenses and goals;
- `POST /plans/{planId}/goals/reorder`;
- Keycloak JWT validation and audience check;
- lazy local profile mapping from JWT claims;
- user-owned current plan creation after login;
- plan ownership checks for authenticated users;
- schema managed by Liquibase, with H2 demo seed data loaded from `data.sql`;
- persisted analytics assumptions, current balance, yearly projection and pension projection endpoints;
- persisted pension settings `GET/PATCH /plans/{planId}/pension` with writable-plan guardrails;
- legacy/deprecated persisted contributions ledger `GET/POST /plans/{planId}/contributions`, `GET/PATCH/DELETE /plans/{planId}/contributions/{id}` with `Goal.savedAmount` derived from contribution sums;
- persisted budget settings `GET/PATCH /plans/{planId}/budget`, envelope autogeneration, monthly tracker `GET/POST /plans/{planId}/calendar/monthly-tracker`, and operation journal `GET/POST/PATCH/DELETE /plans/{planId}/tracker/entries` as the canonical write-path for factual goal outflows;
- persisted user scenarios `GET/POST /scenarios`, `GET/PATCH/DELETE /scenarios/{scenarioId}`, and `POST /scenarios/compare` with built-in read-only scenarios.

Completed guardrails and analytics milestones:

- [#16](https://github.com/svoronkov-les13/finguide-be/issues/16) — OpenAPI coverage guard compares checked-in contract operations with real Springdoc and locks the known implementation gap;
- [#26](https://github.com/svoronkov-les13/finguide-be/issues/26) — prevent mutations of the shared anonymous demo seed plan;
- [#4](https://github.com/svoronkov-les13/finguide-be/issues/4) — serve analytics and pension projections from persisted plan state;
- [#11](https://github.com/svoronkov-les13/finguide-be/issues/11) — persisted pension settings endpoints;
- [#10](https://github.com/svoronkov-les13/finguide-be/issues/10) — persisted contributions ledger endpoints;
- [#12](https://github.com/svoronkov-les13/finguide-be/issues/12) — persisted budget and monthly tracker endpoints;
- [#13](https://github.com/svoronkov-les13/finguide-be/issues/13) — persisted scenario CRUD and comparison endpoints.

## Repository map

```txt
src/main/java/les13/finguide/backend/
  auth/              Keycloak JWT, current user, audience and plan access checks
  users/             local business profile mapped to Keycloak identity
  plans/             current plan, persisted H2 state, financial item CRUD
  contributions/     legacy contribution ledger domain model
  incomes/           income domain model
  expenses/          expense domain model
  goals/             goals and waterfall priority reorder
  analytics/         assumptions, cashflow, dashboard and health calculations
  pension/           pension settings/projection model
  budget/            budget settings, envelopes, monthly tracker and operation journal models
  scenarios/         scenario CRUD and comparison model
  importexport/      target import/export boundary
  notifications/     target notification boundary
src/main/resources/
  db/changelog/      Liquibase database migrations
  data.sql           demo seed data
openapi/
  openapi.json       target backend/frontend contract
  openapi-mock.json  legacy mock contract/config

docs/                MkDocs GitHub Pages documentation
deploy/keycloak/     Docker Compose Keycloak stack and theme
```

## Run locally

```bash
mvn spring-boot:run
```

Open:

```txt
http://127.0.0.1:8080/swagger-ui.html
http://127.0.0.1:8080/v3/api-docs
http://127.0.0.1:8080/api/v1
http://127.0.0.1:8080/api/v1/plans/current
```

Default local mode:

```txt
FINGUIDE_DEMO_MODE=true
jdbc:h2:mem:${random.uuid};MODE=PostgreSQL;DATABASE_TO_UPPER=false
spring.sql.init.mode=always
spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.sql
```

Anonymous requests read the seeded demo plan `22222222-2222-4222-8222-222222222222`. Authenticated users get a cloned user-owned current plan on first `GET /api/v1/plans/current`.

Production-like Kubernetes mode uses:

```txt
SPRING_PROFILES_ACTIVE=prod
FINGUIDE_DEMO_MODE=false
FINGUIDE_DATASOURCE_URL=jdbc:postgresql://finguide-api-postgres:5432/finguide
FINGUIDE_DATASOURCE_USERNAME=finguide
KEYCLOAK_ISSUER_URI=https://finguide.les13.tech/auth/realms/finguide
KEYCLOAK_AUDIENCE=finguide-api
server.servlet.context-path=/finguide-api
```

The context path is part of the application config. The current Kubernetes ingress forwards `/finguide-api` unchanged; it does not strip the prefix.

To require Keycloak JWT validation:

```bash
FINGUIDE_DEMO_MODE=false \
KEYCLOAK_ISSUER_URI=https://finguide.les13.tech/auth/realms/finguide \
KEYCLOAK_AUDIENCE=finguide-api \
mvn spring-boot:run
```

## Test

```bash
mvn test
```

Relevant test areas:

- auth boundary and audience checks;
- demo mode auth behavior;
- plan access service;
- persisted H2 repository;
- plan read endpoints;
- income/expense/goal CRUD;
- contribution ledger CRUD and derived goal progress;
- OpenAPI exposure for financial item and scenario endpoints;
- OpenAPI contract coverage guard: checked-in `openapi/openapi.json` has 58 operations; real Springdoc must cover every implemented operation and must not regress beyond the documented 9-operation gap.

## Documentation

Install docs dependencies and build strictly:

```bash
pip install -r requirements-docs.txt
mkdocs build --strict
```

GitHub Pages deployment is handled by `.github/workflows/pages.yml` on pushes to `main` that touch `docs/**`, `mkdocs.yml`, `requirements-docs.txt` or the workflow itself.

GitHub Pages deployment is intentionally kept in this repository. The published site is:

```txt
https://svoronkov-les13.github.io/finguide-be/
```

Backend runtime deployment is no longer a systemd/JAR install from this repository. The application image is published to GHCR by `.github/workflows/docker-ghcr.yaml`; Kubernetes rollout is owned by `finguide-ops` deploy workflows and overlays.

Important pages:

- `docs/status.md` — actual implementation status;
- `docs/roadmap.md` — completed and planned work;
- `docs/database.md` — Liquibase schema and migration notes;
- `docs/contract.md` — backend/frontend API contract;
- `docs/operations.md` — CI/CD, runner and deploy details;
- `docs/model-analytics.md` — Excel model analysis;
- `docs/backend-architecture-keycloak.md` — architecture and auth.

## Keycloak

Current public Keycloak runs in Kubernetes under `/auth`:

```txt
https://finguide.les13.tech/auth/realms/finguide
```

The older `deploy/keycloak/` Docker Compose stack is retained as a local/legacy reference for theme and realm configuration, not as the current production-like deployment path. Live Kubernetes manifests and secrets are owned by `finguide-ops`.

## Legacy mock

The legacy Java mock is kept only for regression and transition comparison.

```bash
./scripts/run-mock.sh
```

Open:

```txt
http://127.0.0.1:3092/
http://127.0.0.1:3092/api/v1
```
