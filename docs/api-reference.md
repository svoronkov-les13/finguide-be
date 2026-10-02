# API reference

Эта страница фиксирует фактическую карту real Spring Boot API. Для точной схемы request/response смотри Swagger UI и OpenAPI JSON:

- Swagger UI: <https://finguide.les13.tech/finguide-api/swagger-ui.html>
- Runtime OpenAPI JSON: <https://finguide.les13.tech/finguide-api/v3/api-docs>
- Checked-in target contract: [`openapi/openapi.json`](https://github.com/svoronkov-les13/finguide-be/blob/main/openapi/openapi.json)

Публичный base URL:

```txt
https://finguide.les13.tech/finguide-api/api/v1
```

Локальный base URL с дефолтным `server.servlet.context-path=/finguide-api`:

```txt
http://127.0.0.1:8080/finguide-api/api/v1
```

Все бизнес-ответы заворачиваются в `{ "data": ... }`. Ошибки возвращаются как `{ "error": { "code", "message", "details", "requestId" } }`.

## Auth boundary

Основной вход остаётся за Keycloak/OIDC: frontend использует Authorization Code + PKCE, backend валидирует Bearer JWT как OAuth2 Resource Server.

FinGuide API дополнительно содержит два публичных convenience endpoint'а, которые проксируют управляемые операции в Keycloak:

| Method | Path | Status | Назначение |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register` | implemented | Создать пользователя в Keycloak через backend admin client. |
| `POST` | `/api/v1/auth/password/forgot` | implemented | Запросить письмо сброса пароля через Keycloak execute-actions email. |

Backend не владеет login/refresh/logout flow и не хранит пароли. Для этих сценариев клиент идёт в Keycloak endpoints под `/auth/realms/finguide/protocol/openid-connect/...`.

## Plan management

| Method | Path | Status | Назначение |
| --- | --- | --- | --- |
| `GET` | `/api/v1/plans/current` | implemented | Вернуть текущий план: anonymous seed в demo mode или user-owned current plan для JWT user. |
| `GET` | `/api/v1/plans` | implemented | Список планов текущего authenticated user; при первом вызове создаёт current plan из seed. |
| `POST` | `/api/v1/plans` | implemented | Создать пустой план и сделать его current. Body: `{ "name": "..." }`. |
| `POST` | `/api/v1/plans/{planId}/copy` | implemented | Скопировать модель плана без фактической истории tracker/monthly-tracker/contributions. |
| `PUT` | `/api/v1/plans/current` | implemented | Переключить current plan. Body: `{ "planId": "..." }`. |

## Plan read and analytics

| Method | Path | Status | Назначение |
| --- | --- | --- | --- |
| `GET` | `/api/v1` | implemented | Индекс API. |
| `GET` | `/api/v1/me` | implemented | Локальный бизнес-профиль, связанный с `JWT.sub`. |
| `GET` | `/api/v1/plans/{planId}/dashboard` | implemented | Dashboard summary, KPI и goal projections. |
| `GET` | `/api/v1/plans/{planId}/analytics/health` | implemented | Health score. |
| `GET` | `/api/v1/plans/{planId}/analytics/cashflow?years=12` | implemented | Годовой cashflow; `years` опционален, clamp `1..80`. |
| `GET` | `/api/v1/plans/{planId}/analytics/cashflow/monthly` | implemented | Помесячный cashflow для tracker/chart: плановые значения с учётом фактов. |
| `GET` | `/api/v1/plans/{planId}/analytics/assumptions` | implemented | Model assumptions. |
| `PATCH` | `/api/v1/plans/{planId}/analytics/assumptions` | implemented | Full replace assumptions; требует writable plan. |
| `GET` | `/api/v1/plans/{planId}/analytics/balance/current` | implemented | Снимок текущего года. |
| `GET` | `/api/v1/plans/{planId}/analytics/projection?years=30` | implemented | Yearly projection; `years` должен быть `1..60`. |

## Financial items

| Method | Path | Status |
| --- | --- | --- |
| `GET/POST` | `/api/v1/plans/{planId}/incomes` | implemented |
| `GET/PATCH/DELETE` | `/api/v1/plans/{planId}/incomes/{id}` | implemented |
| `GET/POST` | `/api/v1/plans/{planId}/expenses` | implemented |
| `GET/PATCH/DELETE` | `/api/v1/plans/{planId}/expenses/{id}` | implemented |
| `GET/POST` | `/api/v1/plans/{planId}/goals` | implemented |
| `GET/PATCH/DELETE` | `/api/v1/plans/{planId}/goals/{id}` | implemented |
| `POST` | `/api/v1/plans/{planId}/goals/reorder` | implemented |

`goals/reorder` принимает все текущие goal id ровно по одному разу:

```json
{ "goalIds": ["..."] }
```

## Pension

| Method | Path | Status | Назначение |
| --- | --- | --- | --- |
| `GET` | `/api/v1/plans/{planId}/pension` | implemented | Persisted pension settings. |
| `PATCH` | `/api/v1/plans/{planId}/pension` | implemented | Full replace pension settings; требует writable plan. |
| `GET` | `/api/v1/plans/{planId}/pension/projection` | implemented | Preserve-capital и spend-down projections. |

`pension/projection` возвращает два разных понятия:

- `capitalAtRetirement` — прогноз накопленного капитала при текущем плане;
- `requiredCapitalAtRetirement` — капитал, нужный для выбранной пенсионной стратегии.

В `preserveCapital` есть:

```json
{
  "requiredCapitalAtRetirement": 25000000,
  "requiredCapitalStatus": "calculated"
}
```

Если реальная доходность неположительная, `requiredCapitalAtRetirement` может быть `null`, а `requiredCapitalStatus` будет `non_positive_real_return`.

В `spendDown` есть `requiredCapitalAtRetirement`, рассчитанный для фиксированных 30 лет расходования капитала. При этом `spendDown.series` продолжает следовать горизонту модели/графика.

## Budget and tracker

| Method | Path | Status | Назначение |
| --- | --- | --- | --- |
| `GET/PATCH` | `/api/v1/plans/{planId}/budget` | implemented | 50/30/20 или envelope settings. |
| `POST` | `/api/v1/plans/{planId}/budget/envelopes/autogenerate` | implemented | Сгенерировать envelopes по расходам. |
| `GET` | `/api/v1/plans/{planId}/calendar/monthly-tracker?year=2026` | implemented | Месячные статусы накоплений. |
| `POST` | `/api/v1/plans/{planId}/calendar/monthly-tracker` | implemented | Upsert статуса месяца; `204 No Content`. |
| `GET` | `/api/v1/plans/{planId}/tracker/entries?year=2026&month=5` | implemented | Operation journal. |
| `POST` | `/api/v1/plans/{planId}/tracker/entries` | implemented | Создать operation journal entry. |
| `PATCH/DELETE` | `/api/v1/plans/{planId}/tracker/entries/{entryId}` | implemented | Изменить или удалить entry. |

Operation journal — canonical write-path для фактических расходов на цели (`type=goal`, `status=actual`). Legacy contributions остаются только для совместимости.

## Legacy contributions

| Method | Path | Status |
| --- | --- | --- |
| `GET/POST` | `/api/v1/plans/{planId}/contributions` | implemented, deprecated |
| `GET/PATCH/DELETE` | `/api/v1/plans/{planId}/contributions/{id}` | implemented, deprecated |

Не записывай один и тот же факт одновременно в `contributions` и operation journal: analytics учитывает оба источника, и это даст double-counting.

## Scenarios

| Method | Path | Status | Назначение |
| --- | --- | --- | --- |
| `GET/POST` | `/api/v1/scenarios` | implemented | Список и создание user scenarios. |
| `GET/PATCH/DELETE` | `/api/v1/scenarios/{scenarioId}` | implemented | Операции над user scenario. |
| `POST` | `/api/v1/scenarios/compare` | implemented | Сравнить built-in и user сценарии. |

User scenarios — adjustment deltas; built-in `base`/`optimistic`/`pessimistic` генерируются кодом и не пишутся в таблицу.

## Observability

Actuator endpoints живут под тем же context path:

| Path | Назначение |
| --- | --- |
| `/finguide-api/actuator/health` | Health/readiness smoke. |
| `/finguide-api/actuator/info` | Actuator info. |
| `/finguide-api/actuator/prometheus` | Prometheus text exposition, включая JVM metrics. |

Тестовый guard `ActuatorPrometheusMetricsTests` проверяет, что `/actuator/prometheus` отдаёт `jvm_memory_used_bytes` и `jvm_threads_live_threads`.

## Target-only contract gap

Checked-in `openapi/openapi.json` остаётся шире текущего Springdoc, потому что содержит будущие import/export, notifications и profile/avatar endpoints. Тест `OpenApiContractCoverageTests` фиксирует известный target-only gap в 8 операций:

- `PATCH /api/v1/me`
- `PUT /api/v1/me/avatar`
- `DELETE /api/v1/me/avatar`
- `POST /api/v1/import`
- `POST /api/v1/export`
- `GET /api/v1/export/{jobId}`
- `GET /api/v1/notifications`
- `POST /api/v1/notifications/read`
