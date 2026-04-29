# FinGuide / «Финансовый капитал» — контракт бэкенд ↔ фронтенд

Источники анализа:

- Сайт Figma `https://smooth-try-70453479.figma.site/`; пакет Figma Make разобран статически.
- Excel-файл `Модель_P---56630d2a-6465-4036-bd42-9117c7dc9bd6.xlsx` — расчётная модель FinGuide; подробный разбор: [`docs/model-analytics.md`](./model-analytics.md).

В прототипе все данные живут в `localStorage`; для боевого продукта источником истины должен быть бэкенд.

## Экраны из макета

- `/` Дашборд: доходы/расходы за год, чистый баланс, норма сбережений, цели, пенсионный капитал, прогноз и рекомендации.
- `/foundation` Общие данные: профиль, возраст, валюта, стартовый капитал, доходность/инфляция.
- `/income` CRUD источников дохода.
- `/expenses` CRUD категорий расходов.
- `/goals` финансовые цели + приоритет waterfall-распределения.
- `/goal-tracking` фактические взносы к целям.
- `/pension` пенсионный расчёт и стратегии `preserve` / `spend`.
- `/summary` сводный отчёт.
- `/analytics` графики, оценка финансового здоровья, план/факт, тренды, капитал.
- `/calendar` ежемесячный трекер накоплений.
- `/budget` 50/30/20 и конверты бюджета.
- `/scenarios/compare` сценарии и сравнение.
- `/settings`, `/profile`, `/faq`.

## Базовые правила API

- Базовый URL: `/api/v1`.
- Авторизация: `Authorization: Bearer <JWT>`.
- Все даты: ISO-8601 (`YYYY-MM-DD`, `date-time` UTC).
- Деньги: число в валюте записи + `currency`; агрегаты возвращаются в базовой валюте плана.
- Ответы: `{ "data": ... }`; ошибки: `{ "error": { "code", "message", "details", "requestId" } }`.
- Все запросы на запись возвращают актуальный ресурс; фронтенд может делать оптимистичное обновление, но затем обязан принять ответ бэкенда.
- Расчёты (`dashboard`, `analytics`, `pension`, `budget spent`) делает бэкенд, чтобы фронтенд не дублировал финансовую логику.
- Денежные входные суммы для расходов и целей передаются положительными числами; бэкенд сам нормализует знак исходящих платежей. В рассчитанных ответах расходы и цели тоже положительные, а `netSavings = income - expenses - goalExpenses`.
- Поля `*Pct` в API — процентные пункты (`6` означает 6%). Внутри расчётов бэкенд переводит их в десятичную ставку (`0.06`).

## Общие конвенции API

### Пагинация

Все методы API со списками, которые могут вырасти неограниченно (`contributions`, `notifications`, `monthly-tracker`, `export jobs`), используют курсорную пагинацию:

```txt
GET /plans/{planId}/contributions?cursor=<opaque>&limit=50
```

- `limit` по умолчанию `50`, максимум `200`.
- `cursor` — непрозрачная строка, выдаваемая сервером; клиент не интерпретирует её.
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

Короткие справочные коллекции (`incomes`, `expenses`, `goals`, `envelopes`, пользовательские `scenarios`) возвращаются целиком без пагинации — они ограничены по бизнес-смыслу, например до 10 сценариев.

### Идемпотентность запросов на запись

Все небезопасные `POST`-запросы, повторное выполнение которых создаёт дубликаты, поддерживают заголовок:

```txt
Idempotency-Key: <client-generated UUIDv4>
```

Применимо к:

- `POST /plans/{planId}/contributions`;
- `POST /plans/{planId}/incomes`, `POST /plans/{planId}/expenses`, `POST /plans/{planId}/goals`;
- `POST /import`, `POST /export`;
- `POST /scenarios`, `POST /scenarios/compare`.

Бэкенд хранит `(user_id, idempotency_key)` минимум 24 часа. Повтор с тем же ключом и тем же телом возвращает ранее сохранённый ответ; повтор с другим телом — `409 CONFLICT`.

`PATCH`/`PUT`/`DELETE` идемпотентны по семантике HTTP и не требуют заголовка `Idempotency-Key`.

### Оптимистическая конкуренция

Сущности, редактируемые из нескольких вкладок/устройств (`Goal`, `IncomeSource`, `ExpenseItem`, `PensionSettings`, `BudgetSettings`, `UserProfile`), включают поле `version: int` в теле ответа и заголовок `ETag`.

Клиент при `PATCH`/`PUT` присылает:

```txt
If-Match: "<etag>"
```

Если версия в БД новее — бэкенд отвечает `412 PRECONDITION_FAILED` с актуальным состоянием в `error.details.current`. Фронтенд обязан показать конфликт пользователю и не перезаписывать изменения молча.

`Contribution` не требует версионирования — это журнал только на добавление.

### Коды ошибок

Стандартный формат ошибки:

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

Перечень `code` расширяется, но существующие коды не переименовываются:

| code | HTTP | Когда |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | Невалидное тело запроса; `details.fields[]` содержит ошибки по полям |
| `UNAUTHENTICATED` | 401 | Нет JWT или JWT невалиден |
| `FORBIDDEN` | 403 | JWT валиден, но нет доступа к ресурсу |
| `PLAN_NOT_FOUND` | 404 | План не найден или скрыт по правам |
| `RESOURCE_NOT_FOUND` | 404 | Общий 404 для goal/income/expense/contribution |
| `CONFLICT` | 409 | Бизнес-конфликт, например несовпадение идемпотентного запроса или дубликат |
| `PRECONDITION_FAILED` | 412 | `If-Match` не совпал с текущей версией |
| `RATE_LIMITED` | 429 | Превышен лимит; `details.retryAfterSec` |
| `INTERNAL` | 500 | Непредвиденная ошибка; всегда логируется с `requestId` |
| `DEPENDENCY_UNAVAILABLE` | 503 | Keycloak / БД / внешний сервис недоступны |

Фронтенд маппит `code` на UX: уведомление, диалог конфликта, перенаправление на экран входа. На `message` завязываться нельзя — текст может локализоваться.

## Главные сущности

### Профиль пользователя (`UserProfile`)

`id`, `name`, `email`, `phone`, `avatarUrl`, `age`, `gender`, `initialBalance`, `createdAt`, `updatedAt`.

### Доходы и расходы (`IncomeSource` / `ExpenseItem`)

`id`, `name`, `amount`, `currency`, `frequency: monthly|yearly|one_time`, `growthType: manual|inflation|none|schedule`, `growthPct`, `growthSchedule[]`, `startDate`, `endDate`, опционально `startYear`, `endYear`.

Для расходов дополнительно: `budgetClass: needs|wants|savings`, `growthLabel`.

`growthSchedule[]` нужен для Excel-модели: по каждой строке дохода/расхода могут быть разные ставки роста по годам, а не один постоянный процент.

### Цель (`Goal`)

`id`, `name`, `icon`, `currentCost`, `savedAmount`, `currency`, `targetYear`, `type: one_time|recurring`, `growthType`, `growthPct`, `growthSchedule[]`, `priority`.

Логика waterfall-распределения: ближайшая или приоритетная цель получает свободные накопления первой; порядок сохраняется через `/goals/reorder`.

Для Excel-модели `Goal` также должен поддерживать плановые расходы на цели: `plannedAmount`, `frequency`, `startDate/endDate` или `startYear/endYear`. Это покрывает лист `Цели`: ежемесячные и ежегодные расходы на цели как отдельный денежный поток.

### Взнос (`Contribution`)

`id`, `goalId`, `amount`, `currency`, `date`, `note`.

Бэкенд либо хранит `Goal.savedAmount` денормализованно, либо пересчитывает его из записей взносов и возвращает в `Goal`.

### Пенсионные настройки (`PensionSettings`)

`currentAge`, `retirementAge`, `monthlyExpenses`, `desiredMonthlyExpensesCurrentPrices`, `currency`, `expectedReturnPct`, `inflationPct`, `withdrawalStrategy: preserve_capital|spend_down_30y`, `statePensionEnabled`, `statePensionMonthly`.

Excel-модель требует два пенсионных расчёта:

- `preserve_capital`: тратить только реальную доходность от капитала;
- `spend_down`: тратить желаемый уровень расходов и считать возраст исчерпания капитала.

### Предположения модели (`ModelAssumptions`)

`startYear`, `projectionEndYear` или `horizonYears`, `birthYear`, `monthsPerYear`, `currency`, `initialCapital`, `investmentReturnPct`, `inflationSchedule[]`, `sourceModel`.

Это слой, который переносит лист `Вводные` в бэкенд и делает расчёты воспроизводимыми без Excel.

### Настройки бюджета (`BudgetSettings`)

`method: 503020|envelope`, `envelopes[]`, `classifications{expenseId: needs|wants|savings}`.

Конверт бюджета: `id`, `name`, `limit`, `icon`, `color`, рассчитанные `spent`, `remaining`, `pct`, `isOver`.

### Сценарий (`Scenario`)

`id`, `name`, `emoji`, `description`, `isBase`, `snapshot` или `adjustments`.

Стандартные сценарии из макета: базовый, оптимистичный, пессимистичный. Пользовательские сценарии — до 10 штук.

## Карта методов API

### Авторизация и профиль

- `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`
- `POST /auth/password/forgot`, `POST /auth/password/reset`
- `GET/PATCH /me`, `PUT/DELETE /me/avatar`, `PUT /me/password`

### План и CRUD

- `GET/PUT /plans/current`
- `GET/POST /plans/{planId}/incomes`, `GET/PATCH/DELETE /plans/{planId}/incomes/{id}`
- `GET/POST /plans/{planId}/expenses`, `GET/PATCH/DELETE /plans/{planId}/expenses/{id}`
- `GET/POST /plans/{planId}/goals`, `GET/PATCH/DELETE /plans/{planId}/goals/{id}`
- `POST /plans/{planId}/goals/reorder`
- `GET/POST /plans/{planId}/contributions`, `GET/PATCH/DELETE /plans/{planId}/contributions/{id}`
- `GET/PATCH /plans/{planId}/pension`
- `GET/PATCH /plans/{planId}/budget`, `POST /plans/{planId}/budget/envelopes/autogenerate`

### Аналитика и производные данные

- `GET /plans/{planId}/dashboard`
- `GET /plans/{planId}/analytics/projection?years=30`
- `GET/PATCH /plans/{planId}/analytics/assumptions`
- `GET /plans/{planId}/analytics/balance/current`
- `GET /plans/{planId}/analytics/cashflow?startYear=2024&endYear=2076`
- `GET /plans/{planId}/analytics/health`
- `GET/POST /plans/{planId}/calendar/monthly-tracker`
- `GET /plans/{planId}/pension/projection`

`analytics/cashflow` — главный метод API в стиле Excel-модели. Он возвращает годовые строки: возраст, номер периода, доходы, расходы, расходы на цели, чистые сбережения, капитал на начало/конец года.

### Сценарии

- `GET/POST /scenarios`
- `GET/PATCH/DELETE /scenarios/{scenarioId}`
- `POST /scenarios/compare`

### Уведомления, импорт и экспорт

- `GET /notifications?filter=all|unread`
- `POST /notifications/read`
- `POST /import`
- `POST /export`, `GET /export/{jobId}`

## Интеграция фронтенда

1. На старте: `GET /me` + `GET /plans/current` + `GET /plans/{id}/dashboard`.
2. CRUD-экраны используют локальное оптимистичное состояние, но инвалидируют `dashboard`, `analytics`, `budget` после записи.
3. Дашборд и графики не считают деньги сами — только отображают расчётные методы API.
4. Для миграции из прототипа можно один раз прочитать ключи localStorage `finguide-data`, `finguide-user-profile`, `finguide-budgets`, `finguide-scenarios` и отправить `PUT /plans/current` / `POST /import`.
5. Импорт/экспорт должен поддерживать JSON для полного плана, `excel_model` для исходной модели и CSV/XLSX/PDF для отчётов.
