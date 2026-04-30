# Архитектура бэкенда с Keycloak

## Рекомендация

Используем **Java 21 + Spring Boot 3 + Keycloak + PostgreSQL** как модульный монолит с подходом «сначала контракт». Текущий переходный стенд уже поднят как real Spring Boot backend с embedded H2, публичным Swagger UI и CRUD доходов/расходов/целей по адресу `http://66.42.121.18/finguide-api/swagger-ui.html`; legacy mock Swagger остаётся только для сравнения во время миграции.

Микросервисы на первом этапе не нужны. Для цели в 100k зарегистрированных пользователей обычно проще и надёжнее горизонтально масштабировать API без состояния, PostgreSQL, Redis, асинхронные обработчики и модели чтения. Модули проектируем как ограниченные контексты, чтобы позже можно было вынести аналитику, импорт/экспорт или уведомления без разрыва ядра финансового плана.

## Общий поток

```txt
Фронтенд
  -> Keycloak OIDC Authorization Code + PKCE
  -> получает access_token / refresh_token
  -> API бэкенда с Authorization: Bearer <JWT>
  -> Бэкенд валидирует JWT через Keycloak JWKS
  -> Бэкенд проверяет локального пользователя и доступ к плану
  -> PostgreSQL / Redis / object storage
```

Бэкенд **не хранит пароли** и не владеет формами входа. Keycloak отвечает за идентификацию, учётные данные, MFA и пользовательские сессии. В demo/H2 режиме `/api/v1/**` временно открыт без JWT, чтобы фронтенд мог перейти с mock-запросов на реальные сервисы до подключения Keycloak.


## Docker Compose deployment Keycloak

Для первого production/demo deployment Keycloak разворачивается через `docker compose` из `deploy/keycloak/`:

- сервис `keycloak-postgres` — PostgreSQL 16 только для Keycloak, persistent volume `keycloak-postgres-data`;
- сервис `keycloak` — Keycloak 26 на `127.0.0.1:3094` с public path `/auth`;
- `.env.example` содержит только placeholders; реальные admin/DB secrets не коммитим и не пишем в логи;
- healthcheck и `restart: unless-stopped` включены для обоих сервисов;
- custom theme `finguide` оформляет login/account/password reset screens в стиле FinGuide;
- backup/restore PostgreSQL описан в `deploy/keycloak/README.md`.

PostgreSQL в этом stack обязателен. Embedded/dev-file DB Keycloak запрещён. Миграция финансовых данных FinGuide с H2 на PostgreSQL остаётся отдельной задачей, если только deployment design не потребует иначе.

## Настройка Keycloak

```txt
realm: finguide
clients:
  finguide-web      публичный client, PKCE
  finguide-admin    confidential client для админских задач, если понадобится
roles:
  user
  admin
scopes/claims:
  email
  profile
  preferred_username
```

В JWT нам нужны:

- `sub` — стабильный внешний идентификатор пользователя;
- `email`;
- `name` или пара `given_name` + `family_name` для реального ФИО из регистрации;
- `preferred_username` как безопасный fallback, если ФИО не заполнено;
- `realm_access.roles` или роли клиента;
- `email_verified`.

## Модули бэкенда

```txt
les13.finguide.backend
  auth/              проверка JWT, SecurityConfig, CurrentUserProvider
  users/             локальный бизнес-профиль
  plans/             агрегат финансового плана и доступ
  incomes/           CRUD источников дохода
  expenses/          CRUD расходов и бюджетная классификация
  goals/             цели и waterfall-приоритет
  contributions/     фактические взносы в цели
  pension/           настройки пенсии, preserve-capital и spend-down проекции
  budget/            50/30/20 и конверты
  analytics/         предположения из Excel-модели, баланс, денежный поток, сбережения, дашборд, оценка финансового здоровья
  scenarios/         снимки, корректировки и сравнение сценариев
  importexport/      задачи JSON/CSV/XLSX/PDF
  notifications/     производные уведомления, milestone-события и подсказки
```

## Авторизация запроса

1. Проверить подпись JWT и издателя токена (`issuer`) через Keycloak JWKS.
2. Получить `sub` из токена.
3. Найти или создать локального `users.keycloak_subject`.
4. На каждом запросе к плану проверить владение планом или явно выданный доступ.

Пример:

```txt
GET /plans/{planId}/dashboard
Authorization: Bearer <JWT>

JWT.sub -> users.keycloak_subject -> владелец плана / доступ к плану -> дашборд
```

## Профиль пользователя

Keycloak — источник идентичности. Бэкенд хранит бизнес-профиль.

```txt
users
- id uuid pk
- keycloak_subject text unique not null
- email text not null
- name text
- phone text
- avatar_url text
- age int
- gender text
- initial_balance numeric
- created_at timestamptz
- updated_at timestamptz
```

## Архитектура расчётов

Excel-файл `Модель_P---56630d2a-6465-4036-bd42-9117c7dc9bd6.xlsx` — эталонная финансовая модель. Бэкенд должен воспроизвести её как детерминированные расчётные сервисы:

```txt
PlanState + ModelAssumptions
  -> нормализовать суммы, ставки и даты
  -> построить годовую шкалу
  -> применить флаги активности по start/end year
  -> применить годовые графики роста
  -> посчитать доходы, расходы и расходы на цели
  -> посчитать годовые сбережения и накопленный капитал
  -> посчитать пенсионные варианты preserve-capital и spend-down
  -> закешировать snapshots для dashboard/scenarios
```

Важные соглашения:

- API принимает расходы и цели положительными суммами.
- Поля API с суффиксом `Pct` — процентные пункты (`6` = 6%). Расчётный код переводит их в десятичные ставки.
- `analytics/cashflow` — каноническая производная проекция. Дашборд, оценка финансового здоровья, сценарии и уведомления должны строиться поверх него, а не дублировать формулы.
- Храним исходные входные данные и опциональные снимки расчётов; каждую промежуточную формулу сохранять не нужно, если нет требования аудита/кеша.

## Основная модель хранения

```txt
users
plans
income_sources
expense_items
goals
contributions
pension_settings
budget_settings
budget_envelopes
scenarios
monthly_tracker_entries
export_jobs
notification_events
model_assumptions
analytics_snapshots
pension_projection_snapshots
```

Общие поля:

```txt
id uuid pk
plan_id uuid fk
created_at timestamptz
updated_at timestamptz
deleted_at timestamptz null
version int
```

`version` нужен для оптимистической блокировки и `ETag` / `If-Match`.

## Масштабирование до 100k пользователей

100k зарегистрированных пользователей не означает автоматическую необходимость в микросервисах. Начинаем с:

```txt
Балансировщик нагрузки
  -> 2-4 stateless-инстанса Spring Boot API
  -> PostgreSQL primary + read replica при необходимости
  -> Redis cache
  -> 2+ инстанса Keycloak на PostgreSQL
  -> асинхронные workers для analytics/export/notifications
```

Добавляем:

- кеш Redis для снимков дашборда/проекции, производных от `analytics/cashflow`;
- модели чтения: `plan_dashboard_snapshot`, `plan_projection_snapshot`, `financial_health_snapshot`;
- паттерн исходящих событий (outbox) для бизнес-событий;
- асинхронные задачи для пересчётов, экспорта и уведомлений.

## Что выносить в сервисы позже

```txt
analytics-worker
  читает события PlanChanged
  пересчитывает projections и scenarios

import-export-worker
  генерирует XLSX/PDF/CSV

notification-worker
  создаёт reminders, milestones и tips
```

Финансовое ядро (`plans`, `incomes`, `expenses`, `goals`, `pension`) сначала лучше держать вместе: много расчётов требует консистентного состояния одного плана.

## Безопасность

- Время жизни access token: 5-15 минут.
- Ротация refresh token в Keycloak.
- Бэкенд проверяет аудиторию и издателя токена (`audience` / `issuer`).
- Ограничение частоты запросов для auth-like и import/export методов API.
- Журнал аудита для критичных изменений плана.
- Персональные данные хранить минимально; секреты — только в переменных окружения или secret manager.

## Реализация в текущем backend

В рамках задачи #18 добавлен production auth boundary без удаления demo режима:

- Spring Security OAuth2 Resource Server валидирует issuer/JWKS из `KEYCLOAK_ISSUER_URI`;
- `KEYCLOAK_AUDIENCE` по умолчанию `finguide-api`, неподходящий `aud` даёт `403 FORBIDDEN`;
- `JWT.sub` маппится в `user_profiles.keycloak_subject`, профиль создаётся лениво при первом запросе;
- имя/email локального профиля синхронизируются из текущего JWT, поэтому authenticated API не возвращает seeded placeholder `Александр Петров` вместо ФИО зарегистрированного пользователя;
- `GET /api/v1/me` возвращает текущий бизнес-профиль;
- доступ к `/api/v1/plans/{planId}/...` проверяется по владельцу `financial_plans.owner_user_id`; роль `admin` может читать план для диагностики;
- операции записи income/expense/goal проходят ту же проверку доступа и пишут audit log без секретов;
- `FINGUIDE_DEMO_MODE=true` сохраняет прежний no-auth demo/H2 режим для локальных тестов и текущего публичного стенда до запуска Keycloak.
