# FinGuide Backend

Backend for **FinGuide / «Финансовый капитал»**: contract-first Spring Boot API, persisted demo plan state, Keycloak/OIDC auth boundary and canonical financial calculations.

## Links

- Docs / GitHub Pages: https://svoronkov-les13.github.io/finguide-be/
- Frontend demo: http://66.42.121.18/fg/
- Real API base: http://66.42.121.18/finguide-api/api/v1
- Swagger UI: http://66.42.121.18/finguide-api/swagger-ui.html
- Real OpenAPI JSON: http://66.42.121.18/finguide-api/v3/api-docs
- Keycloak realm: http://66.42.121.18/auth/realms/finguide
- Roadmap issue: https://github.com/svoronkov-les13/finguide-be/issues/27
- GitHub Project: https://github.com/orgs/svoronkov-les13/projects/5/views/1

Legacy mock Swagger remains available only for transition checks: http://66.42.121.18/finguide-mock/

## Stack

- Java 21
- Spring Boot 3.3
- Spring Web / Validation / Actuator
- Spring Security OAuth2 Resource Server
- Spring Data JDBC
- Springdoc OpenAPI
- Embedded H2 in PostgreSQL compatibility mode for the current demo phase
- Keycloak 26 + PostgreSQL as a separate auth stack

Target persistence direction: PostgreSQL + Flyway after the H2 demo phase.

## Current implementation

Real backend currently supports:

- `GET /api/v1` API index;
- `GET /api/v1/me`;
- `GET /api/v1/plans/current`;
- plan dashboard, health, cashflow and read-only scenarios;
- CRUD for incomes, expenses and goals;
- `POST /plans/{planId}/goals/reorder`;
- Keycloak JWT validation and audience check;
- lazy local profile mapping from JWT claims;
- user-owned current plan creation after login;
- plan ownership checks for authenticated users;
- H2 seed data loaded from `schema.sql` + `data.sql`;
- persisted analytics assumptions, current balance, yearly projection and pension projection endpoints;
- persisted pension settings `GET/PATCH /plans/{planId}/pension` with writable-plan guardrails;
- persisted contributions ledger `GET/POST /plans/{planId}/contributions`, `GET/PATCH/DELETE /plans/{planId}/contributions/{id}` with `Goal.savedAmount` derived from contribution sums.

Completed guardrails and analytics milestones:

- [#16](https://github.com/svoronkov-les13/finguide-be/issues/16) — OpenAPI coverage guard compares checked-in contract operations with real Springdoc and locks the known implementation gap;
- [#26](https://github.com/svoronkov-les13/finguide-be/issues/26) — prevent mutations of the shared anonymous demo seed plan;
- [#4](https://github.com/svoronkov-les13/finguide-be/issues/4) — serve analytics and pension projections from persisted plan state;
- [#11](https://github.com/svoronkov-les13/finguide-be/issues/11) — persisted pension settings endpoints;
- [#10](https://github.com/svoronkov-les13/finguide-be/issues/10) — persisted contributions ledger endpoints.

## Repository map

```txt
src/main/java/les13/finguide/backend/
  auth/              Keycloak JWT, current user, audience and plan access checks
  users/             local business profile mapped to Keycloak identity
  plans/             current plan, persisted H2 state, financial item CRUD
  contributions/     contribution ledger domain model
  incomes/           income domain model
  expenses/          expense domain model
  goals/             goals and waterfall priority reorder
  analytics/         assumptions, cashflow, dashboard and health calculations
  pension/           pension settings/projection model
  budget/            target budget model
  scenarios/         target scenario model
  importexport/      target import/export boundary
  notifications/     target notification boundary
src/main/resources/
  schema.sql         current H2 DDL
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
jdbc:h2:mem:finguide;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1
spring.sql.init.mode=always
```

Anonymous requests read the seeded demo plan `22222222-2222-4222-8222-222222222222`. Authenticated users get a cloned user-owned current plan on first `GET /api/v1/plans/current`.

To require Keycloak JWT validation:

```bash
FINGUIDE_DEMO_MODE=false \
KEYCLOAK_ISSUER_URI=http://66.42.121.18/auth/realms/finguide \
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
- OpenAPI exposure for financial item endpoints;
- OpenAPI contract coverage guard: checked-in `openapi/openapi.json` has 54 operations; real Springdoc must cover every implemented operation and must not regress beyond the documented 19-operation gap.

## Documentation

Install docs dependencies and build strictly:

```bash
pip install -r requirements-docs.txt
mkdocs build --strict
```

GitHub Pages deployment is handled by `.github/workflows/pages.yml` on pushes to `main` that touch `docs/**`, `mkdocs.yml`, `requirements-docs.txt` or the workflow itself.

Backend production demo deployment is handled by `.github/workflows/deploy.yml` on every push to `main`: the self-hosted runner on `66.42.121.18` runs `mvn -B clean package`, installs `/opt/finguide-api/finguide-be.jar`, restarts `finguide-api.service`, then smoke-tests health.

Important pages:

- `docs/status.md` — actual implementation status;
- `docs/roadmap.md` — completed and planned work;
- `docs/database.md` — current H2 DDL and migration notes;
- `docs/contract.md` — backend/frontend API contract;
- `docs/operations.md` — CI/CD, runner and deploy details;
- `docs/model-analytics.md` — Excel model analysis;
- `docs/backend-architecture-keycloak.md` — architecture and auth.

## Keycloak

Keycloak is deployed separately:

```bash
cd deploy/keycloak
cp .env.example .env
# edit .env with real secrets outside git/logs
docker compose --env-file .env up -d
./configure-realm.py
```

See [`deploy/keycloak/README.md`](deploy/keycloak/README.md).

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
