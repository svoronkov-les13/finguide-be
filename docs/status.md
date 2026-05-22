# Текущее состояние реализации

Эта страница фиксирует фактическое состояние real Spring Boot backend и публичного demo-стенда. Она важнее старых mock-артефактов: mock остаётся только как переходный эталон для сравнения контрактов.

## Публичный стенд

- Frontend: <http://66.42.121.18/fg/>
- Real API base: <http://66.42.121.18/finguide-api/api/v1>
- Swagger UI: <http://66.42.121.18/finguide-api/swagger-ui.html>
- Real OpenAPI JSON: <http://66.42.121.18/finguide-api/v3/api-docs>
- Keycloak realm: <http://66.42.121.18/auth/realms/finguide>
- GitHub Pages docs: <https://svoronkov-les13.github.io/finguide-be/>
- Backend service: `finguide-api.service`
- Backend jar: `/opt/finguide-api/finguide-be.jar`

## Backend

Текущий backend — Java 21 + Spring Boot 3.3 + Spring Security OAuth2 Resource Server + Spring Data JDBC + embedded H2 demo persistence.

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
- H2 seed data from `schema.sql` + `data.sql`;
- OpenAPI coverage guard [#16](https://github.com/svoronkov-les13/finguide-be/issues/16): checked-in `openapi/openapi.json` содержит 58 операций, real Springdoc покрывает 49 уже реализованных операций, а известный gap в 9 операций зафиксирован тестом и не должен расти случайно.

Текущая checked-in OpenAPI спецификация всё ещё шире real Springdoc, но расхождение теперь явно зафиксировано тестом `OpenApiContractCoverageTests`. Следующие задачи должны уменьшать список missing operations по мере реализации endpoints.

## Demo/H2 режим

По умолчанию используется:

```txt
jdbc:h2:mem:finguide;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1
FINGUIDE_DEMO_MODE=true
spring.sql.init.mode=always
```

Anonymous requests читают seeded plan `22222222-2222-4222-8222-222222222222`. Authenticated users получают собственный cloned current plan. Общий anonymous seed read-only для финансовых мутаций, PATCH analytics assumptions и PATCH pension settings.

## CI/CD

Backend deploy автоматизирован через `.github/workflows/deploy.yml`:

- trigger: push в `main` или ручной `workflow_dispatch`;
- runner: self-hosted на `66.42.121.18`, labels `self-hosted`, `finguide-be`;
- gate: `mvn -B clean package`;
- deploy: установка jar в `/opt/finguide-api/finguide-be.jar` и restart `finguide-api.service`;
- smoke: локальный `127.0.0.1:3093/actuator/health` и публичный `/finguide-api/actuator/health`.

Docs deploy автоматизирован через `.github/workflows/pages.yml` и `mkdocs build --strict`.

Frontend deploy также переведён на self-hosted runner на этом же сервере: push в `main` собирает `dist/` и выкладывает публичный стенд под `/fg/`.

## Frontend

Текущий frontend — React 19 + TypeScript + Vite + TanStack Query/Router + Orval generated API client.

Реализовано:

- публичный deploy под `/fg/`;
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
