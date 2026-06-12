# Текущее состояние реализации

Эта страница фиксирует фактическое состояние real Spring Boot backend и публичного demo-стенда. Она важнее старых mock-артефактов: mock остаётся только как переходный эталон для сравнения контрактов.

## Публичный стенд

- Frontend: <https://finguide.les13.tech/fg/>
- Real API base: <https://finguide.les13.tech/finguide-api/api/v1>
- Swagger UI: <https://finguide.les13.tech/finguide-api/swagger-ui.html>
- Real OpenAPI JSON: <https://finguide.les13.tech/finguide-api/v3/api-docs>
- Keycloak realm: <https://finguide.les13.tech/auth/realms/finguide>
- GitHub Pages docs: <https://svoronkov-les13.github.io/finguide-be/>
- Kubernetes namespace: `finguide`
- Backend deployment/service: `finguide-api`
- Backend database: `finguide-api-postgres`, database `finguide`, user `finguide`

## Backend

Текущий backend — Java 21 + Spring Boot 3.3 + Spring Security OAuth2 Resource Server + Spring Data JDBC. Локально используется embedded H2 demo persistence; production-like стенд на `finguide.les13.tech` использует PostgreSQL в Kubernetes.

Реализовано:

- `GET /api/v1` — индекс API;
- `GET /api/v1/me` — профиль текущего пользователя из JWT / demo контекста;
- `GET /api/v1/plans/current` — текущий план;
- `GET /plans/{planId}/dashboard`;
- `GET /plans/{planId}/analytics/health`;
- `GET /plans/{planId}/analytics/cashflow`;
- `GET /plans/{planId}/analytics/assumptions` и `PATCH /plans/{planId}/analytics/assumptions`;
- `GET /plans/{planId}/analytics/balance/current`;
- `GET /plans/{planId}/analytics/projection?years=...`;
- `GET /plans/{planId}/pension` и `PATCH /plans/{planId}/pension`;
- `GET /plans/{planId}/pension/projection`;
- legacy/deprecated `GET/POST /plans/{planId}/contributions`, `GET/PATCH/DELETE /plans/{planId}/contributions/{id}`;
- `GET/PATCH /plans/{planId}/budget`, `POST /plans/{planId}/budget/envelopes/autogenerate`;
- `GET/POST /plans/{planId}/calendar/monthly-tracker`;
- `GET/POST /plans/{planId}/tracker/entries`, `PATCH/DELETE /plans/{planId}/tracker/entries/{entryId}` — persisted journal операций страницы `/tracking`, canonical write-path для фактических goal outflows;
- `GET/POST /scenarios`, `GET/PATCH/DELETE /scenarios/{scenarioId}`, `POST /scenarios/compare` — persisted пользовательские сценарии и сравнение;
- CRUD доходов, расходов и целей;
- `POST /plans/{planId}/goals/reorder`;
- Keycloak/OIDC boundary: JWT validation, audience check, lazy local profile mapping, user-owned current plan after first authenticated request, plan ownership checks;
- frontend/auth bootstrap fixes: authenticated session no longer reuses anonymous demo cache/default profile;
- schema managed by Liquibase, with H2 demo seed data from `data.sql`;
- OpenAPI coverage guard [#16](https://github.com/svoronkov-les13/finguide-be/issues/16): checked-in `openapi/openapi.json` содержит 58 операций, real Springdoc покрывает 49 уже реализованных операций, а известный gap в 9 операций зафиксирован тестом и не должен расти случайно.

Текущая checked-in OpenAPI спецификация всё ещё шире real Springdoc, но расхождение теперь явно зафиксировано тестом `OpenApiContractCoverageTests`. Следующие задачи должны уменьшать список missing operations по мере реализации endpoints.

## Demo/H2 режим

По умолчанию используется:

```txt
jdbc:h2:mem:finguide;MODE=PostgreSQL;DATABASE_TO_UPPER=false
FINGUIDE_DEMO_MODE=true
spring.sql.init.mode=always
spring.liquibase.change-log=classpath:/db/changelog/db.changelog-master.sql
```

Anonymous requests читают seeded plan `22222222-2222-4222-8222-222222222222`. Authenticated users получают собственный cloned current plan. Общий anonymous seed read-only для финансовых мутаций, PATCH analytics assumptions и PATCH pension settings.

## Prod/PostgreSQL режим

В Kubernetes backend запущен с:

```txt
SPRING_PROFILES_ACTIVE=prod
FINGUIDE_DEMO_MODE=false
FINGUIDE_DATASOURCE_URL=jdbc:postgresql://finguide-api-postgres:5432/finguide
KEYCLOAK_ISSUER_URI=https://finguide.les13.tech/auth/realms/finguide
server.servlet.context-path=/finguide-api
```

Ingress не срезает `/finguide-api`; prefix является context path самого Spring Boot приложения. Health probes и Swagger поэтому тоже находятся под `/finguide-api`.

## CI/CD

Backend image publishing автоматизирован через `.github/workflows/docker-ghcr.yaml`. Runtime rollout делает `finguide-ops`: deploy workflow рендерит Kubernetes overlay, подставляет image tag и ждёт rollout `finguide-api`, `finguide-web` и Keycloak.

Docs deploy автоматизирован через `.github/workflows/pages.yml` и `mkdocs build --strict`.

Frontend image publishing и Kubernetes rollout также идут через связку `finguide-web` + `finguide-ops`. Старый static deploy на legacy IP больше не является текущим production-like contract.

## Frontend

Текущий frontend — React 19 + TypeScript + Vite + TanStack Query/Router + Orval generated API client.

Реализовано:

- публичный deploy под `/fg/` (`/` redirects to `/fg/`);
- OIDC Authorization Code + PKCE через Keycloak;
- разделение React Query cache для anonymous demo и authenticated user;
- нейтральный loader во время auth/current-plan restore;
- persisted sidebar counters из `/plans/current`;
- real API base для demo deploy: `/finguide-api/api/v1`.

## Что ещё не реализовано

Основные открытые группы с учётом FinPlan redesign [finguide-web#7](https://github.com/svoronkov-les13/finguide-web/issues/7):

- Done: запрет мутации общего anonymous demo seed plan — [#26](https://github.com/svoronkov-les13/finguide-be/issues/26);
- Done: analytics/pension из persisted state — [#4](https://github.com/svoronkov-les13/finguide-be/issues/4);
- Done: pension settings — [#11](https://github.com/svoronkov-les13/finguide-be/issues/11);
- Done: legacy/deprecated contributions ledger — [#10](https://github.com/svoronkov-les13/finguide-be/issues/10);
- Done: budget/monthly tracker — [#12](https://github.com/svoronkov-les13/finguide-be/issues/12);
- Done: scenarios CRUD/compare — [#13](https://github.com/svoronkov-les13/finguide-be/issues/13);
- Now: frontend design foundation по FinPlan — design tokens, app shell/sidebar/topbar, shared UI primitives, dashboard desktop target;
- Next: frontend generated client smoke — [finguide-web#2](https://github.com/svoronkov-les13/finguide-web/issues/2);
- Later: `PUT /plans/current` — [#7](https://github.com/svoronkov-les13/finguide-be/issues/7);
- Later: profile/avatar/account mutations — [#8](https://github.com/svoronkov-les13/finguide-be/issues/8);
- Later: import/export — [#14](https://github.com/svoronkov-les13/finguide-be/issues/14);
- Later: notifications — [#15](https://github.com/svoronkov-les13/finguide-be/issues/15).
