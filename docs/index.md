# Документация FinGuide

Документация бэкенда/API для **FinGuide**.

## Что здесь находится

- [Контракт API](contract.md) — договор между бэкендом и фронтендом.
- [Аналитическая Excel-модель](model-analytics.md) — разбор Excel-модели, которую бэкенд должен воспроизводить.
- [Архитектура бэкенда и Keycloak](backend-architecture-keycloak.md) — целевая архитектура продукта.
- [Модули бэкенда](backend-modules.md) — структура Java-пакетов и границы модулей.

## Быстрые ссылки

- Репозиторий GitHub: <https://github.com/svoronkov-les13/finguide-be>
- Swagger UI реального бэкенда: <http://66.42.121.18/finguide-api/swagger-ui.html>
- OpenAPI JSON реального бэкенда: <http://66.42.121.18/finguide-api/v3/api-docs>
- База/индекс реального API: <http://66.42.121.18/finguide-api/api/v1>
- Пример живого endpoint: <http://66.42.121.18/finguide-api/api/v1/plans/current>
- Публичный предпросмотр контракта: <http://66.42.121.18/finguide-contract/>
- JSON-спецификация OpenAPI в репозитории: <https://github.com/svoronkov-les13/finguide-be/blob/main/openapi/openapi.json>
- Legacy mock Swagger, только на период перехода: <http://66.42.121.18/finguide-mock/>
- Keycloak deployment guide: [`deploy/keycloak/README.md`](https://github.com/svoronkov-les13/finguide-be/tree/main/deploy/keycloak)

## Текущий статус реализации

Real Spring Boot backend сейчас обслуживает:

- индекс API `GET /api/v1`;
- чтение текущего плана, дашборда, health/cashflow и списка сценариев;
- CRUD доходов, расходов и целей;
- Keycloak/OIDC security boundary: JWT Resource Server, `GET /api/v1/me`, lazy profile mapping and plan ownership checks;
- `POST /plans/{planId}/goals/reorder` для сохранения waterfall-приоритета целей.

Оставшиеся группы контракта ведутся отдельными задачами GitHub Issues.

## Главная договорённость

Фронтенд редактирует входные данные и показывает рассчитанные значения. Бэкенд владеет финансовой логикой:

```txt
PlanState + ModelAssumptions
  -> годовой денежный поток
  -> сбережения и накопленный капитал
  -> пенсионная проекция
  -> дашборд, оценка финансового здоровья и сценарии
```

Главный метод API для производных расчётов:

```txt
GET /plans/{planId}/analytics/cashflow
```
