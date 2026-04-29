# FinGuide Backend

Backend artifacts for **FinGuide / «Финансовый капитал»**.

## Current contents

- `src/main/java/les13/finguide/backend/` — Spring Boot 3 / Java 21 modular backend with embedded H2 demo persistence and real read API endpoints.
- `src/main/java/les13/finguide/mock/` — legacy Java 21 mock server kept only for transition and regression checks.
- `openapi/openapi.json` — backend/frontend OpenAPI contract.
- `openapi/openapi-mock.json` — legacy mock Swagger configuration.
- `docs/contract.md` — human-readable API contract.
- `docs/model-analytics.md` — Excel model analysis and calculation rules.
- `docs/backend-architecture-keycloak.md` — target backend architecture with Keycloak auth.
- `docs/backend-modules.md` — package/module map.

## Deployed services

Primary real backend services:

- Frontend: http://66.42.121.18/fg/
- Real Swagger UI: http://66.42.121.18/finguide-api/swagger-ui.html
- Real OpenAPI JSON: http://66.42.121.18/finguide-api/v3/api-docs
- Real API base/index: http://66.42.121.18/finguide-api/api/v1
- Example real endpoint: http://66.42.121.18/finguide-api/api/v1/plans/current
- Markdown contract: http://66.42.121.18/finguide-contract/contract.md

Transition-only mock services:

- Mock Swagger: http://66.42.121.18/finguide-mock/
- Mock OpenAPI: http://66.42.121.18/finguide-mock/openapi.json

## Run the real backend locally

```bash
mvn spring-boot:run
```

Then open:

```txt
http://127.0.0.1:8080/swagger-ui.html
http://127.0.0.1:8080/v3/api-docs
http://127.0.0.1:8080/api/v1
http://127.0.0.1:8080/api/v1/plans/current
```

Default local/demo mode uses embedded H2 and permits `/api/v1/**` without Keycloak so frontend integration can move off mock responses incrementally.

## Run the legacy Java 21 mock locally

The repo keeps one shared `src/` tree: Spring Boot code and legacy mock code both live under `src/main/java`.

```bash
./scripts/run-mock.sh
```

Then open:

```txt
http://127.0.0.1:3092/
http://127.0.0.1:3092/api/v1
```

## Suggested production backend direction

Start as a **contract-first modular monolith**:

- Java 21
- Spring Boot 3
- Spring Security OAuth2 Resource Server
- Keycloak for identity/auth
- PostgreSQL + Flyway after the H2 demo phase
- Redis for cache/rate limiting
- async workers for analytics/export/notifications

This keeps the financial plan core consistent and still allows horizontal scaling and later extraction of workers/services.
