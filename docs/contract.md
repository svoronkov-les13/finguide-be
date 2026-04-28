# FinGuide / «Финансовый капитал» — контракт backend ↔ frontend

Источник анализа: Figma site `https://smooth-try-70453479.figma.site/`, статически разобран Figma Make bundle. В прототипе все данные живут в `localStorage`; для боевого продукта источник истины должен быть backend.

## Экраны из макета

- `/` Дашборд: доходы/расходы за год, чистый баланс, норма сбережений, цели, пенсионный капитал, прогноз и рекомендации.
- `/foundation` Общие данные: профиль, возраст, валюта, стартовый капитал, доходность/инфляция.
- `/income` CRUD источников дохода.
- `/expenses` CRUD категорий расходов.
- `/goals` финансовые цели + waterfall-приоритет.
- `/goal-tracking` фактические взносы к целям.
- `/pension` пенсионный расчёт и стратегии `preserve` / `spend`.
- `/summary` сводный отчёт.
- `/analytics` графики, health score, план/факт, тренды, капитал.
- `/calendar` ежемесячный трекер накоплений.
- `/budget` 50/30/20 и конверты бюджета.
- `/scenarios/compare` сценарии и сравнение.
- `/settings`, `/profile`, `/faq`.

## Базовые правила API

- Base URL: `/api/v1`.
- Auth: `Authorization: Bearer <JWT>`.
- Все даты: ISO-8601 (`YYYY-MM-DD`, `date-time` UTC).
- Деньги: число в валюте записи + `currency`; агрегаты возвращаются в базовой валюте плана.
- Ответы: `{ "data": ... }`; ошибки: `{ "error": { "code", "message", "details", "requestId" } }`.
- Все write-запросы возвращают актуальный ресурс; frontend может делать optimistic update, но затем обязан принять ответ backend.
- Расчёты (`dashboard`, `analytics`, `pension`, `budget spent`) делает backend, чтобы фронт не дублировал финансовую логику.

## Общие конвенции API

### Пагинация

Все list-эндпоинты, которые могут вырасти неограниченно (`contributions`, `notifications`, `monthly-tracker`, `export jobs`), используют курсорную пагинацию:

```txt
GET /plans/{planId}/contributions?cursor=<opaque>&limit=50
```

- `limit` по умолчанию `50`, максимум `200`.
- `cursor` — непрозрачная строка, выдаваемая сервером; клиент не интерпретирует.
- Ответ:

```json
{
  "data": [ ... ],
  "page": {
    "nextCursor": "eyJpZCI6...",
    "hasMore": true
  }
}
```

Короткие справочные коллекции (`incomes`, `expenses`, `goals`, `envelopes`, пользовательские `scenarios`) возвращаются целиком без пагинации — они ограничены по бизнес-смыслу (например, до 10 сценариев).

### Идемпотентность write-запросов

Все небезопасные `POST`-запросы, повторное выполнение которых создаёт дубликаты, поддерживают заголовок:

```txt
Idempotency-Key: <client-generated UUIDv4>
```

Применимо к:

- `POST /plans/{planId}/contributions`
- `POST /plans/{planId}/incomes`, `POST /plans/{planId}/expenses`, `POST /plans/{planId}/goals`
- `POST /import`, `POST /export`
- `POST /scenarios`, `POST /scenarios/compare`

Backend хранит `(user_id, idempotency_key)` минимум 24 часа. Повтор с тем же ключом и тем же телом возвращает ранее сохранённый ответ; повтор с другим телом — `409 CONFLICT`.

`PATCH`/`PUT`/`DELETE` идемпотентны по семантике HTTP и заголовок не требуют.

### Оптимистическая конкуренция

Сущности, редактируемые из нескольких вкладок/устройств (`Goal`, `IncomeSource`, `ExpenseItem`, `PensionSettings`, `BudgetSettings`, `UserProfile`), включают поле `version: int` в payload и заголовок `ETag` в ответе.

Клиент при `PATCH`/`PUT` присылает:

```txt
If-Match: "<etag>"
```

Если версия в БД новее — backend отвечает `412 PRECONDITION_FAILED` с актуальным состоянием в `error.details.current`. Frontend обязан показать конфликт пользователю и не перезаписывать молча.

`Contribution` версионирование не требует — это append-only журнал.

### Коды ошибок

Стандартный конверт ошибки:

```json
{
  "error": {
    "code": "PLAN_NOT_FOUND",
    "message": "Plan not found or access denied",
    "details": { ... },
    "requestId": "01HF..."
  }
}
```

Перечень `code` (расширяется, но без переименований существующих):

| code | HTTP | Когда |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | Невалидный payload; `details.fields[]` содержит per-field ошибки |
| `UNAUTHENTICATED` | 401 | Нет/невалиден JWT |
| `FORBIDDEN` | 403 | JWT валиден, но нет доступа к ресурсу |
| `PLAN_NOT_FOUND` | 404 | План не найден или скрыт по правам |
| `RESOURCE_NOT_FOUND` | 404 | Generic 404 для goal/income/expense/contribution |
| `CONFLICT` | 409 | Бизнес-конфликт (например, idempotency mismatch, дубликат) |
| `PRECONDITION_FAILED` | 412 | `If-Match` не совпал с текущей версией |
| `RATE_LIMITED` | 429 | Превышен лимит; `details.retryAfterSec` |
| `INTERNAL` | 500 | Непредвиденная ошибка; всегда логируется с `requestId` |
| `DEPENDENCY_UNAVAILABLE` | 503 | Keycloak / БД / внешний сервис недоступны |

Frontend маппит `code` на UX (тост / диалог конфликта / редирект на login), а не на `message` — текст может локализоваться.

## Главные сущности

### UserProfile
`id`, `name`, `email`, `phone`, `avatarUrl`, `age`, `gender`, `initialBalance`, `createdAt`, `updatedAt`.

### IncomeSource / ExpenseItem
`id`, `name`, `amount`, `currency`, `frequency: monthly|yearly|one_time`, `growthType: manual|inflation|none`, `growthPct`, `startDate`, `endDate`.
Для расходов дополнительно: `budgetClass: needs|wants|savings`, `growthLabel`.

### Goal
`id`, `name`, `icon`, `currentCost`, `savedAmount`, `currency`, `targetYear`, `type: one_time|recurring`, `growthType`, `growthPct`, `priority`.
Waterfall-логика: ближайшая/приоритетная цель получает свободные накопления первой; порядок сохраняется через `/goals/reorder`.

### Contribution
`id`, `goalId`, `amount`, `currency`, `date`, `note`.
Backend либо хранит `Goal.savedAmount` денормализованно, либо пересчитывает из contributions и возвращает в Goal.

### PensionSettings
`currentAge`, `retirementAge`, `monthlyExpenses`, `currency`, `expectedReturnPct`, `inflationPct`, `withdrawalStrategy: preserve_capital|spend_down_30y`, `statePensionEnabled`, `statePensionMonthly`.

### BudgetSettings
`method: 503020|envelope`, `envelopes[]`, `classifications{expenseId: needs|wants|savings}`.
Envelope: `id`, `name`, `limit`, `icon`, `color`, computed `spent`, `remaining`, `pct`, `isOver`.

### Scenario
`id`, `name`, `emoji`, `description`, `isBase`, `snapshot` или `adjustments`.
Стандартные сценарии из макета: base, optimistic, pessimistic. Пользовательские сценарии — до 10 штук.

## Endpoint map

### Auth/Profile
- `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
- `POST /auth/password/forgot`, `POST /auth/password/reset`
- `GET/PATCH /me`, `PUT/DELETE /me/avatar`, `PUT /me/password`

### Plan and CRUD
- `GET/PUT /plans/current`
- `GET/POST /plans/{planId}/incomes`, `GET/PATCH/DELETE /plans/{planId}/incomes/{id}`
- `GET/POST /plans/{planId}/expenses`, `GET/PATCH/DELETE /plans/{planId}/expenses/{id}`
- `GET/POST /plans/{planId}/goals`, `GET/PATCH/DELETE /plans/{planId}/goals/{id}`
- `POST /plans/{planId}/goals/reorder`
- `GET/POST /plans/{planId}/contributions`, `GET/PATCH/DELETE /plans/{planId}/contributions/{id}`
- `GET/PATCH /plans/{planId}/pension`
- `GET/PATCH /plans/{planId}/budget`, `POST /plans/{planId}/budget/envelopes/autogenerate`

### Analytics / derived data
- `GET /plans/{planId}/dashboard`
- `GET /plans/{planId}/analytics/projection?years=30`
- `GET /plans/{planId}/analytics/health`
- `GET/POST /plans/{planId}/calendar/monthly-tracker`

### Scenarios
- `GET/POST /scenarios`
- `GET/PATCH/DELETE /scenarios/{scenarioId}`
- `POST /scenarios/compare`

### Notifications / import-export
- `GET /notifications?filter=all|unread`
- `POST /notifications/read`
- `POST /import`
- `POST /export`, `GET /export/{jobId}`

## Frontend integration notes

1. На старте: `GET /me` + `GET /plans/current` + `GET /plans/{id}/dashboard`.
2. CRUD-экраны используют локальный optimistic state, но инвалидируют `dashboard`, `analytics`, `budget` после записи.
3. Дашборд и графики не считают деньги сами — только отображают computed endpoints.
4. Для миграции из прототипа можно один раз прочитать localStorage keys `finguide-data`, `finguide-user-profile`, `finguide-budgets`, `finguide-scenarios` и отправить `PUT /plans/current` / `POST /import`.
5. Импорт/экспорт должен поддерживать JSON для полного плана и CSV/XLSX/PDF для отчётов.
