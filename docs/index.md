# FinGuide Docs

Документация backend/API для **FinGuide / Финансовый капитал**.

## Что здесь лежит

- [API контракт](contract.md) — договор backend ↔ frontend.
- [Excel-модель аналитики](model-analytics.md) — разбор workbook-модели, которую backend должен воспроизводить.
- [Backend architecture + Keycloak](backend-architecture-keycloak.md) — целевая архитектура.
- [Backend modules](backend-modules.md) — структура Java-пакетов и границы модулей.

## Быстрые ссылки

- GitHub repo: <https://github.com/svoronkov-les13/finguide-be>
- Swagger mock: <http://66.42.121.18/finguide-mock/>
- Public contract preview: <http://66.42.121.18/finguide-contract/>
- OpenAPI JSON: <https://github.com/svoronkov-les13/finguide-be/blob/main/openapi/openapi.json>

## Ключевая договорённость

Frontend редактирует входные данные и показывает derived data. Backend владеет финансовой логикой:

```txt
PlanState + ModelAssumptions
  -> yearly cashflow
  -> savings / accumulated capital
  -> pension projection
  -> dashboard / health / scenarios
```

Главный derived endpoint:

```txt
GET /plans/{planId}/analytics/cashflow
```
