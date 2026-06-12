# База данных

Сейчас FinGuide backend использует Liquibase migrations для схемы. По умолчанию локальный/demo runtime работает на embedded H2 в PostgreSQL compatibility mode; prod profile подключает PostgreSQL.

## Runtime настройки

```yaml
spring:
  datasource:
    url: ${FINGUIDE_DATASOURCE_URL:jdbc:h2:mem:${random.uuid};MODE=PostgreSQL;DATABASE_TO_UPPER=false}
    username: ${FINGUIDE_DATASOURCE_USERNAME:sa}
    driver-class-name: org.h2.Driver
  sql:
    init:
      mode: always
  liquibase:
    change-log: classpath:/db/changelog/db.changelog-master.sql
```

Инициализация идёт из:

- `src/main/resources/db/changelog/db.changelog-master.sql` — схема через Liquibase;
- `src/main/resources/data.sql` — demo seed для локального/H2 режима.

В prod profile `spring.sql.init.mode=never`, поэтому demo seed не загружается, а схема всё равно управляется Liquibase.

## Источник DDL

Текущая схема описана в Liquibase changeset `finguide:001-initial-schema`; не дублируем DDL в документации, чтобы не расходиться с миграцией.


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

`contributions.goal_id` references `goals(id)`. The repository deletes contributions explicitly before deleting a goal, so goal removal does not produce FK errors or orphan ledger rows. The table is now a legacy compatibility ledger; current UI writes factual goal outflows through `operation_journal_entries` (`type=goal`, `status=actual`), so clients must not write the same fact into both tables.

`scenarios` хранит пользовательские scenarios как adjustment deltas. Поле `snapshot_json` зарезервировано для будущих snapshot-сценариев; built-in `base`/`optimistic`/`pessimistic` генерируются кодом и не пишутся в таблицу.

## Важные ограничения текущей схемы

- Нет поля `is_demo_seed`; seed определяется константным id/subject в коде и данных.
- Нет soft delete, optimistic `version`, audit table и idempotency table.
- Нет таблиц для `import/export jobs`, `notifications`. `budget`, `monthly_tracker`, `operation_journal` и пользовательские `scenarios` уже persisted в H2 demo.
- Demo seed остаётся SQL init-данными, а не Liquibase changeset; prod profile отключает `spring.sql.init`.

## Следующие шаги схемы

Рекомендуемая следующая итерация хранения:

1. Добавить явную защиту seed:
   - `financial_plans.is_demo_seed boolean not null default false`;
   - mutation guard в service layer;
   - regression tests на все write endpoints.
2. Добавить `is_current` или отдельный указатель current plan, если понадобится несколько планов на пользователя.
3. Добавить `version int not null default 0` для редактируемых сущностей и `ETag`/`If-Match`.
4. Расширить схему под roadmap: import/export jobs, notifications и будущие snapshot scenarios. Pension settings mutations, legacy contributions ledger, budget settings, monthly tracker, operation journal и adjustment-based scenarios уже реализованы поверх текущей persisted схемы.
