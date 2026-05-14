# Документация FinGuide

Документация backend/API для **FinGuide / «Финансовый капитал»**.

FinGuide строится как contract-first продукт: frontend редактирует входные данные финансового плана, backend владеет хранением, безопасностью и расчётами. Текущий этап — переход от mock/localStorage прототипа к real Spring Boot backend с persisted demo state и Keycloak/OIDC boundary.

## Быстрые ссылки

- Frontend demo: <http://66.42.121.18/fg/>
- Real API base: <http://66.42.121.18/finguide-api/api/v1>
- Swagger UI real backend: <http://66.42.121.18/finguide-api/swagger-ui.html>
- OpenAPI JSON real backend: <http://66.42.121.18/finguide-api/v3/api-docs>
- Keycloak realm: <http://66.42.121.18/auth/realms/finguide>
- Backend repository: <https://github.com/svoronkov-les13/finguide-be>
- Web repository: <https://github.com/svoronkov-les13/finguide-web>
- GitHub Project: <https://github.com/orgs/svoronkov-les13/projects/5/views/1>

Legacy mock Swagger: <http://66.42.121.18/finguide-mock/> — только для переходного сравнения.

## Что читать первым

- [Текущее состояние реализации](status.md) — что реально работает сейчас.
- [Roadmap](roadmap.md) — готовые и будущие задачи.
- [База данных](database.md) — фактическая H2 schema и ограничения demo persistence.
- [Контракт API](contract.md) — целевой договор backend ↔ frontend.
- [Operations и CI/CD](operations.md) — self-hosted runner, auto-deploy и smoke checks.
- [Аналитическая Excel-модель](model-analytics.md) — канонические расчёты из исходной модели.
- [Архитектура бэкенда и Keycloak](backend-architecture-keycloak.md) — целевое устройство backend/auth.
- [Модули бэкенда](backend-modules.md) — пакетная структура и границы модулей.

## Текущий статус коротко

Реализовано в real backend:

- API index;
- `GET /me`;
- `GET /plans/current`;
- dashboard/health/cashflow and scenario CRUD/compare;
- analytics assumptions, current balance, yearly projection, pension settings and pension projection from persisted state;
- CRUD incomes/expenses/goals;
- goals reorder;
- Keycloak JWT Resource Server boundary;
- user-owned current plan после логина;
- защита authenticated users от чтения/мутации чужих планов;
- frontend session restore без demo/default profile flash;
- self-hosted GitHub Actions deploy backend/web после push в `main`;
- [#16](https://github.com/svoronkov-les13/finguide-be/issues/16) OpenAPI coverage guard: real Springdoc защищён от регрессий относительно checked-in OpenAPI;
- [#13](https://github.com/svoronkov-les13/finguide-be/issues/13) persisted scenario CRUD/compare: user scenarios are adjustment deltas, built-ins are read-only;
- [#26](https://github.com/svoronkov-les13/finguide-be/issues/26) общий anonymous demo seed plan read-only для мутаций;
- [#4](https://github.com/svoronkov-les13/finguide-be/issues/4) analytics/pension endpoints строятся из persisted plan state;
- [#11](https://github.com/svoronkov-les13/finguide-be/issues/11) pension settings endpoints реализованы поверх persisted state;
- [#10](https://github.com/svoronkov-les13/finguide-be/issues/10) contributions ledger endpoints реализованы поверх persisted state; `Goal.savedAmount` теперь выводится из суммы взносов.

Следующий backend guardrail:

- [#12](https://github.com/svoronkov-les13/finguide-be/issues/12) — budget/monthly tracker endpoints.

## Главная договорённость

Frontend не должен дублировать финансовую математику. Backend принимает persisted `PlanState` и `ModelAssumptions`, затем строит производные данные:

```txt
PlanState + ModelAssumptions
  -> годовой денежный поток
  -> сбережения и накопленный капитал
  -> пенсионная проекция
  -> dashboard, health score, scenarios CRUD/compare
```

Канонический расчётный endpoint:

```txt
GET /plans/{planId}/analytics/cashflow
```
