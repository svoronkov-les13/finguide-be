# База данных

Сейчас FinGuide backend работает на embedded H2 в PostgreSQL compatibility mode. Это demo persistence, не финальная PostgreSQL/Flyway схема.

## Runtime настройки

```yaml
spring:
  datasource:
    url: ${FINGUIDE_DATASOURCE_URL:jdbc:h2:mem:finguide;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1}
    username: ${FINGUIDE_DATASOURCE_USERNAME:sa}
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always
```

Инициализация идёт из:

- `src/main/resources/schema.sql`;
- `src/main/resources/data.sql`.

В текущем H2 demo нет Flyway/Liquibase. При старте schema пересоздаётся через `drop table if exists ...`.

## Текущий H2 DDL

```sql
create table user_profiles (
  id uuid primary key,
  keycloak_subject varchar(128) not null,
  email varchar(255) not null,
  name varchar(255) not null,
  phone varchar(64),
  avatar_url varchar(1024),
  age integer,
  gender varchar(32),
  initial_balance numeric(19, 2) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint uq_user_profiles_keycloak_subject unique (keycloak_subject)
);

create table financial_plans (
  id uuid primary key,
  owner_user_id uuid not null references user_profiles(id),
  name varchar(255) not null,
  base_currency varchar(3) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint uq_financial_plans_owner_user_id unique (owner_user_id)
);

create table pension_settings (
  plan_id uuid primary key references financial_plans(id),
  current_age integer not null,
  retirement_age integer not null,
  monthly_expenses numeric(19, 2) not null,
  desired_monthly_expenses_current_prices numeric(19, 2) not null,
  currency varchar(3) not null,
  expected_return_pct numeric(9, 4) not null,
  inflation_pct numeric(9, 4) not null,
  withdrawal_strategy varchar(64) not null,
  state_pension_enabled boolean not null,
  state_pension_monthly numeric(19, 2) not null
);

create table model_assumptions (
  plan_id uuid primary key references financial_plans(id),
  start_year integer not null,
  projection_end_year integer,
  horizon_years integer,
  birth_year integer,
  months_per_year integer not null,
  currency varchar(3) not null,
  initial_capital numeric(19, 2) not null,
  investment_return_pct numeric(9, 4) not null,
  source_model varchar(1024)
);

create table inflation_rates (
  plan_id uuid not null references financial_plans(id),
  rate_year integer not null,
  rate_pct numeric(9, 4) not null,
  primary key (plan_id, rate_year)
);

create table incomes (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  name varchar(255) not null,
  amount numeric(19, 2) not null,
  currency varchar(3) not null,
  frequency varchar(32) not null,
  growth_type varchar(32) not null,
  growth_pct numeric(9, 4) not null,
  start_date date not null,
  end_date date,
  sort_order integer not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create table expenses (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  name varchar(255) not null,
  amount numeric(19, 2) not null,
  currency varchar(3) not null,
  frequency varchar(32) not null,
  growth_type varchar(32) not null,
  growth_pct numeric(9, 4) not null,
  growth_label varchar(255),
  budget_class varchar(32) not null,
  start_date date not null,
  end_date date,
  sort_order integer not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create table goals (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  name varchar(255) not null,
  icon varchar(64),
  current_cost numeric(19, 2) not null,
  saved_amount numeric(19, 2) not null,
  currency varchar(3) not null,
  target_year integer not null,
  type varchar(32) not null,
  growth_type varchar(32) not null,
  growth_pct numeric(9, 4) not null,
  index_label varchar(255),
  priority integer not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create table contributions (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  goal_id uuid not null references goals(id),
  amount numeric(19, 2) not null,
  currency varchar(3) not null,
  contribution_date date not null,
  note varchar(1024),
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create table budget_settings (
  plan_id uuid primary key references financial_plans(id),
  method varchar(32) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create table budget_envelopes (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  name varchar(255) not null,
  limit_amount numeric(19, 2) not null,
  icon varchar(64) not null,
  color varchar(32) not null,
  sort_order integer not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create table budget_classifications (
  plan_id uuid not null references financial_plans(id),
  expense_id uuid not null references expenses(id),
  budget_class varchar(32) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  primary key (plan_id, expense_id)
);

create table monthly_tracker_entries (
  plan_id uuid not null references financial_plans(id),
  tracker_month varchar(7) not null,
  status varchar(32) not null,
  note varchar(1024),
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  primary key (plan_id, tracker_month)
);

create table scenarios (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  name varchar(120) not null,
  emoji varchar(16),
  description varchar(1024),
  is_base boolean not null default false,
  income_adj_pct numeric(9, 4) not null,
  expense_adj_pct numeric(9, 4) not null,
  return_adj_pct numeric(9, 4) not null,
  inflation_adj_pct numeric(9, 4) not null,
  retirement_age_shift integer not null,
  goals_cost_adj_pct numeric(9, 4) not null,
  snapshot_json clob,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

```

## Seed данные

`data.sql` создаёт demo user/profile и один seeded plan:

- user id: `11111111-1111-4111-8111-111111111111`;
- `keycloak_subject`: `demo-seed`;
- plan id: `22222222-2222-4222-8222-222222222222`;
- profile name: `Александр Петров`;
- base currency: `RUB`.

Также seed содержит:

- pension settings;
- model assumptions;
- inflation rates for 2024–2027;
- 3 incomes;
- 3 expenses;
- 3 goals with `saved_amount = 0` because goal progress is derived from contributions.

`contributions.goal_id` references `goals(id)`. The repository deletes contributions explicitly before deleting a goal, so goal removal does not produce FK errors or orphan ledger rows.

`scenarios` хранит пользовательские scenarios как adjustment deltas. Поле `snapshot_json` зарезервировано для будущих snapshot-сценариев; built-in `base`/`optimistic`/`pessimistic` генерируются кодом и не пишутся в таблицу.

## Важные ограничения текущей схемы

- Один план на пользователя: `unique(owner_user_id)`.
- Нет поля `is_current`; текущий план выводится из уникального плана владельца.
- Нет поля `is_demo_seed`; seed определяется константным id/subject в коде и данных.
- Нет soft delete, optimistic `version`, audit table и idempotency table.
- Нет таблиц для `import/export jobs`, `notifications`. `budget`, `monthly_tracker` и пользовательские `scenarios` уже persisted в H2 demo.
- H2 schema предназначена для demo/dev. Для PostgreSQL нужно вводить Flyway migrations и аккуратно разделить demo seed, user-owned plans и production данные.

## Целевая PostgreSQL миграция

Рекомендуемая следующая итерация хранения:

1. Ввести Flyway и `V1__baseline.sql` на основе текущей H2 схемы.
2. Добавить явную защиту seed:
   - `financial_plans.is_demo_seed boolean not null default false`;
   - mutation guard в service layer;
   - regression tests на все write endpoints.
3. Добавить `is_current` или отдельный указатель current plan, если понадобится несколько планов на пользователя.
4. Добавить `version int not null default 0` для редактируемых сущностей и `ETag`/`If-Match`.
5. Расширить схему под roadmap: import/export jobs, notifications и будущие snapshot scenarios. Pension settings mutations, contributions ledger, budget settings, monthly tracker и adjustment-based scenarios уже реализованы поверх текущей persisted схемы.
