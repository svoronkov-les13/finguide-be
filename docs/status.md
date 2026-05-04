# Текущее состояние реализации

Эта страница фиксирует фактическое состояние real Spring Boot backend и публичного demo-стенда. Она важнее старых mock-артефактов: mock остаётся только как переходный эталон для сравнения контрактов.

## Публичный стенд

- Frontend: <http://66.42.121.18/fg/>
- Real API base: <http://66.42.121.18/finguide-api/api/v1>
- Swagger UI: <http://66.42.121.18/finguide-api/swagger-ui.html>
- Real OpenAPI JSON: <http://66.42.121.18/finguide-api/v3/api-docs>
- Keycloak realm: <http://66.42.121.18/auth/realms/finguide>
- GitHub Pages docs: <https://svoronkov-les13.github.io/finguide-be/>

## Backend

Текущий backend — Java 21 + Spring Boot 3.3 + Spring Security OAuth2 Resource Server + Spring Data JDBC + embedded H2 demo persistence.

Реализовано:

- `GET /api/v1` — индекс API;
- `GET /api/v1/me` — профиль текущего пользователя из JWT / demo контекста;
- `GET /api/v1/plans/current` — текущий план;
- `GET /plans/{planId}/dashboard`;
- `GET /plans/{planId}/analytics/health`;
- `GET /plans/{planId}/analytics/cashflow`;
- `GET /scenarios` — read-only список сценариев;
- CRUD доходов, расходов и целей;
- `POST /plans/{planId}/goals/reorder`;
- Keycloak/OIDC boundary: JWT validation, audience check, lazy local profile mapping, user-owned current plan after first authenticated request, plan ownership checks;
- frontend/auth bootstrap fixes: authenticated session no longer reuses anonymous demo cache/default profile;
- H2 seed data from `schema.sql` + `data.sql`.

Текущая checked-in OpenAPI спецификация шире real Springdoc. Guardrail-задача: [#16](https://github.com/svoronkov-les13/finguide-be/issues/16).

## Demo/H2 режим

По умолчанию используется:

```txt
jdbc:h2:mem:finguide;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1
FINGUIDE_DEMO_MODE=true
spring.sql.init.mode=always
```

Anonymous requests читают seeded plan `22222222-2222-4222-8222-222222222222`. Authenticated users получают собственный cloned current plan. Запрет мутации общего anonymous seed выделен в отдельную задачу [#26](https://github.com/svoronkov-les13/finguide-be/issues/26).

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

Основные открытые группы:

- analytics/pension из persisted state — [#4](https://github.com/svoronkov-les13/finguide-be/issues/4);
- `PUT /plans/current` — [#7](https://github.com/svoronkov-les13/finguide-be/issues/7);
- profile/avatar/account mutations — [#8](https://github.com/svoronkov-les13/finguide-be/issues/8);
- contributions ledger — [#10](https://github.com/svoronkov-les13/finguide-be/issues/10);
- pension settings — [#11](https://github.com/svoronkov-les13/finguide-be/issues/11);
- budget/monthly tracker — [#12](https://github.com/svoronkov-les13/finguide-be/issues/12);
- scenarios CRUD/compare — [#13](https://github.com/svoronkov-les13/finguide-be/issues/13);
- import/export — [#14](https://github.com/svoronkov-les13/finguide-be/issues/14);
- notifications — [#15](https://github.com/svoronkov-les13/finguide-be/issues/15);
- frontend generated client smoke — [finguide-web#2](https://github.com/svoronkov-les13/finguide-web/issues/2).
