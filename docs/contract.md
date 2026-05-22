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

- Базовый URL внутри сервиса: `/api/v1`.
- Публичный URL реального бэкенда на текущем сервере: `http://66.42.121.18/finguide-api/api/v1`.
- `GET /api/v1` возвращает JSON-индекс сервиса со ссылками на Swagger/OpenAPI и основные endpoints.
- Swagger UI реального бэкенда: `http://66.42.121.18/finguide-api/swagger-ui.html`.
- Legacy mock Swagger остаётся только для переходного сравнения: `http://66.42.121.18/finguide-mock/`.

Текущая real-реализация на H2 уже покрывает чтение плана/дашборда/health/cashflow, persisted scenario CRUD/compare, persisted analytics assumptions/current balance/yearly projection/pension settings/pension projection, CRUD для `IncomeSource`, `ExpenseItem`, `Goal`, включая `goals/reorder`, legacy persisted `Contribution` ledger, persisted `BudgetSettings`, monthly tracker и operation journal. Остальные группы методов из карты ниже пока ведутся отдельными задачами.

OpenAPI guardrail [#16](https://github.com/svoronkov-les13/finguide-be/issues/16) включён в тестовый набор: checked-in `openapi/openapi.json` сейчас содержит 58 операций, real Springdoc покрывает 49 реализованных операций, а известный gap в 9 операций явно зафиксирован. Новые endpoints должны одновременно добавляться в real Springdoc и уменьшать этот gap; случайное исчезновение уже реализованной операции из `/v3/api-docs` ломает тесты.

- Авторизация: `Authorization: Bearer <JWT>` с access token из Keycloak realm `finguide`. Бэкенд валидирует JWT как OAuth2 Resource Server и не владеет password-based `/auth/register/login/refresh/logout` endpoints. В demo/H2 режиме (`FINGUIDE_DEMO_MODE=true`) `/api/v1/**` временно открыт для интеграции фронтенда с real backend без Keycloak.
- Все даты: ISO-8601 (`YYYY-MM-DD`, `date-time` UTC).
- Деньги: число в валюте записи + `currency`; агрегаты возвращаются в базовой валюте плана.
- Ответы: `{ "data": ... }`; ошибки: `{ "error": { "code", "message", "details", "requestId" } }`.
- Все запросы на запись возвращают актуальный ресурс; фронтенд может делать оптимистичное обновление, но затем обязан принять ответ бэкенда.
- Расчёты (`dashboard`, `analytics`, `pension`, `budget spent`) делает бэкенд, чтобы фронтенд не дублировал финансовую логику.
- Денежные входные суммы для расходов и целей передаются положительными числами; бэкенд сам нормализует знак исходящих платежей. В рассчитанных ответах расходы и цели тоже положительные, а `netSavings = income - expenses - goalExpenses`.
- Поля `*Pct` в API — процентные пункты (`6` означает 6%). Внутри расчётов бэкенд переводит их в десятичную ставку (`0.06`).

## Общие конвенции API

### Пагинация

Целевой contract для методов API со списками, которые могут вырасти неограниченно (`notifications`, `monthly-tracker`, `export jobs`; legacy `contributions` при сохранении совместимости), использует курсорную пагинацию:

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

Короткие справочные коллекции (`incomes`, `expenses`, `goals`, `envelopes`, пользовательские `scenarios`) возвращаются целиком без пагинации — они ограничены по бизнес-смыслу; пользовательских сценариев максимум 10.

### Идемпотентность запросов на запись

Все небезопасные `POST`-запросы, повторное выполнение которых создаёт дубликаты, поддерживают заголовок:

```txt
Idempotency-Key: <client-generated UUIDv4>
```

Применимо к:

- legacy `POST /plans/{planId}/contributions`;
- `POST /plans/{planId}/incomes`, `POST /plans/{planId}/expenses`, `POST /plans/{planId}/goals`;
- `POST /import`, `POST /export`;
- `POST /scenarios` и `POST /scenarios/compare` сейчас реализованы; idempotency storage остаётся целевым follow-up.

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

Для `POST` id и timestamps генерирует сервер; `PATCH` принимает частичное тело и меняет только переданные поля. В текущей H2-реализации `null` в nullable-полях (`endDate`, `growthLabel`, `icon`, `indexLabel`) трактуется как «оставить текущее значение», поэтому очистка таких полей будет оформлена отдельной merge-patch/replace семантикой позже. Валидация: суммы неотрицательные, `growthPct` от `0` до `100`, `endDate >= startDate`, `currency` — ISO-4217 alpha-3.

Для расходов дополнительно: `budgetClass: needs|wants|savings`, `growthLabel`.

`growthSchedule[]` нужен для Excel-модели: по каждой строке дохода/расхода могут быть разные ставки роста по годам, а не один постоянный процент.

### Цель (`Goal`)

`id`, `name`, `icon`, `currentCost`, `savedAmount`, `currency`, `targetYear`, `targetMonth`, `type: one_time|recurring`, `growthType`, `growthPct`, `growthSchedule[]`, `priority`. `targetYear` должен быть не меньше `2024`; `targetMonth` — `1..12`, при отсутствии в request backend использует декабрь (`12`).

Логика waterfall-распределения: ближайшая цель получает свободные накопления первой; порядок сортировки `targetYear`, `targetMonth`, `priority`, `id`, а `/goals/reorder` управляет priority внутри одинакового срока.

Для Excel-модели `Goal` также должен поддерживать плановые расходы на цели: `plannedAmount`, `frequency`, `startDate/endDate` или `startYear/endYear`. Это покрывает лист `Цели`: ежемесячные и ежегодные расходы на цели как отдельный денежный поток.

### Взнос (`Contribution`, legacy)

`id`, `goalId`, `amount`, `currency`, `date`, `note`.

`Contribution` ledger оставлен как legacy compatibility API и помечен deprecated в backend/controller/OpenAPI. Текущий UI пишет фактические goal outflows через operation journal (`/plans/{planId}/tracker/entries` с `type=goal`, `status=actual`). Не нужно писать один и тот же факт одновременно в `contributions` и operation journal: analytics учитывает оба источника для совместимости, и это даст double-counting.

В legacy path `Goal.savedAmount` — производное значение: после create/update/delete взноса бэкенд пересчитывает его как `sum(Contribution.amount)` по цели. Поле `savedAmount` в goal create/patch не является источником истины.

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

`id`, `name`, `emoji`, `description`, `isBase` (canonical), `base` (temporary compatibility alias), `snapshot` или `adjustments`.

Стандартные сценарии из макета: базовый, оптимистичный, пессимистичный. Пользовательские сценарии — до 10 штук.

## Карта методов API

### Авторизация и профиль

Keycloak владеет входом, регистрацией, refresh/logout, восстановлением пароля, MFA и пользовательскими сессиями. Эти endpoints доступны под публичным route `/auth/realms/finguide/protocol/openid-connect/...` и не входят в FinGuide API contract. Frontend использует Authorization Code + PKCE и передаёт access token в API.

- `GET /me` — реализовано в real backend; возвращает локальный бизнес-профиль, связанный с `JWT.sub`, с синхронизацией `email`/ФИО из JWT.
- `PATCH /me`, `PUT/DELETE /me/avatar` — отдельные задачи профиля.
- Смена пароля — вне зоны ответственности backend; пароль меняется через Keycloak account/password reset screens.

### План и CRUD

- `GET/PUT /plans/current` (`GET` реализован; `PUT` отдельная задача). Для anonymous demo возвращает seeded plan `22222222-2222-4222-8222-222222222222`; для authenticated пользователя первый `GET` транзакционно и идемпотентно создаёт его собственный current plan, клонируя persisted demo seed, и дальше возвращает только пользовательский план.
- `GET/POST /plans/{planId}/incomes`, `GET/PATCH/DELETE /plans/{planId}/incomes/{id}` — реализовано в real backend.
- `GET/POST /plans/{planId}/expenses`, `GET/PATCH/DELETE /plans/{planId}/expenses/{id}` — реализовано в real backend.
- `GET/POST /plans/{planId}/goals`, `GET/PATCH/DELETE /plans/{planId}/goals/{id}` — реализовано в real backend.
- `POST /plans/{planId}/goals/reorder` — реализовано в real backend; тело `{ "goalIds": ["..."] }` должно содержать все текущие id целей ровно по одному разу.
- `GET/POST /plans/{planId}/contributions`, `GET/PATCH/DELETE /plans/{planId}/contributions/{id}` — legacy/deprecated compatibility endpoints; read требует доступ к плану, write требует writable plan access, общий anonymous seed read-only. `Goal.savedAmount` пересчитывается из суммы взносов по цели. Удаление цели удаляет связанные с ней взносы, чтобы не оставлять orphan ledger records. Текущий canonical source для фактических goal outflows — operation journal; не смешивать оба write-path для одного факта из-за риска double-counting. Текущая H2-реализация возвращает полный список без pagination/idempotency.
- `GET/PATCH /plans/{planId}/pension` — реализовано в real backend; `PATCH` делает full replace persisted `PensionSettings` и требует writable plan access, поэтому общий anonymous seed read-only.
- `GET/PATCH /plans/{planId}/budget`, `POST /plans/{planId}/budget/envelopes/autogenerate` — реализовано в real backend.
- `GET/POST /plans/{planId}/calendar/monthly-tracker` — реализовано в real backend; хранит статус месяца (`completed|partial|missed`).
- `GET/POST /plans/{planId}/tracker/entries`, `PATCH/DELETE /plans/{planId}/tracker/entries/{entryId}` — реализовано в real backend; хранит журнал операций страницы `/tracking` (`date`, `title`, `amount`, `type`, `status`) и требует writable plan access для mutations. Для фактических расходов на цели это текущий canonical write-path (`type=goal`, `status=actual`).

### Аналитика и производные данные

- `GET /plans/{planId}/dashboard` — реализовано в real backend.
- `GET /plans/{planId}/analytics/projection?years=30` — реализовано в real backend; строит годовую проекцию из persisted `PlanState`.
- `GET/PATCH /plans/{planId}/analytics/assumptions` — реализовано в real backend; `PATCH` требует writable plan access, поэтому общий anonymous seed read-only.
- `GET /plans/{planId}/analytics/balance/current` — реализовано в real backend.
- `GET /plans/{planId}/analytics/cashflow?startYear=2024&endYear=2076` — базовый 12-летний cashflow реализован в real backend; параметры периода остаются частью целевого contract.
- `GET /plans/{planId}/analytics/health` — реализовано в real backend.
- `GET /plans/{planId}/pension/projection` — реализовано в real backend; строится из persisted state и текущих pension settings.

`analytics/cashflow` — главный метод API в стиле Excel-модели. Он возвращает годовые строки: возраст, номер периода, доходы, расходы, расходы на цели, чистые сбережения, капитал на начало/конец года.

### Сценарии

- `GET/POST /scenarios` — implemented
- `GET/PATCH/DELETE /scenarios/{scenarioId}` — implemented
- `POST /scenarios/compare` — implemented

### Уведомления, импорт и экспорт

- `GET /notifications?filter=all|unread`
- `POST /notifications/read`
- `POST /import`
- `POST /export`, `GET /export/{jobId}`

## Интеграция фронтенда

1. Если включён OIDC: пройти `/login` → Keycloak Authorization Code + PKCE → `/auth/callback`, сохранить access token и подставлять `Authorization`. Затем: `GET /me` + `GET /plans/current` + `GET /plans/{id}/dashboard`. Frontend должен разделять cache для anonymous demo и authenticated user session и показывать neutral loader до завершения восстановления токена/current plan, чтобы не мигал seeded demo/default профиль.
2. CRUD-экраны используют локальное оптимистичное состояние, но инвалидируют `dashboard`, `analytics`, `budget` после записи.
3. Дашборд и графики не считают деньги сами — только отображают расчётные методы API.
4. Для миграции из прототипа можно один раз прочитать ключи localStorage `finguide-data`, `finguide-user-profile`, `finguide-budgets`, `finguide-scenarios` и отправить `PUT /plans/current` / `POST /import`.
5. Импорт/экспорт должен поддерживать JSON для полного плана, `excel_model` для исходной модели и CSV/XLSX/PDF для отчётов.
