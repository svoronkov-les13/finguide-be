# FinGuide Backend

Backend artifacts for **FinGuide / «Финансовый капитал»**.

Current contents:

- `src/main/java/world/finguide/backend/` — Spring Boot 3 / Java 21 modular backend skeleton.
- `docs/backend-modules.md` — package/module map.
- `openapi/openapi.json` — backend/frontend OpenAPI contract.
- `openapi/openapi-mock.json` — same contract configured for deployed mock Swagger.
- `docs/contract.md` — human-readable API contract.
- `docs/backend-architecture-keycloak.md` — proposed backend architecture with Keycloak auth.
- `src/mock/java/` — Java 21 stub server with deterministic mock responses and Swagger UI.

Deployed preview:

- Hub: http://66.42.121.18/finguide/
- Swagger mock: http://66.42.121.18/finguide-mock/
- OpenAPI mock: http://66.42.121.18/finguide-mock/openapi.json
- Markdown contract: http://66.42.121.18/finguide-contract/contract.md

Demo bearer token for Swagger:

```txt
Bearer mock-access-token-java21
```

## Run Java 21 mock locally

The repo keeps one shared `src/` tree: Spring Boot code is in `src/main/java`, mock server code is in `src/mock/java`.

```bash
./scripts/run-mock.sh
```

Then open:

```txt
http://127.0.0.1:3092/
```

Mock API base:

```txt
http://127.0.0.1:3092/api/v1
```

## Suggested production backend direction

Start as a **contract-first modular monolith**:

- Java 21
- Spring Boot 3
- Spring Security OAuth2 Resource Server
- Keycloak for identity/auth
- PostgreSQL + Flyway
- Redis for cache/rate limiting
- async workers for analytics/export/notifications

This keeps the financial plan core consistent and still allows horizontal scaling and later extraction of workers/services.
