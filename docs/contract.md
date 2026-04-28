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
