# Документация FinGuide

Документация бэкенда/API для **FinGuide / Финансовый капитал**.

## Что здесь находится

- [Контракт API](contract.md) — договор между бэкендом и фронтендом.
- [Аналитическая Excel-модель](model-analytics.md) — разбор Excel-модели, которую бэкенд должен воспроизводить.
- [Архитектура бэкенда и Keycloak](backend-architecture-keycloak.md) — целевая архитектура продукта.
- [Модули бэкенда](backend-modules.md) — структура Java-пакетов и границы модулей.

## Быстрые ссылки

- Репозиторий GitHub: <https://github.com/svoronkov-les13/finguide-be>
- Swagger UI mock-сервера: <http://66.42.121.18/finguide-mock/>
- Публичный предпросмотр контракта: <http://66.42.121.18/finguide-contract/>
- JSON-спецификация OpenAPI: <https://github.com/svoronkov-les13/finguide-be/blob/main/openapi/openapi.json>

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
