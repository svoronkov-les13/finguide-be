--liquibase formatted sql

--changeset finguide:001-initial-schema
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
  current_plan_id uuid,
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
  updated_at timestamp with time zone not null
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
  target_month integer not null default 12,
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
  amount numeric(19, 2) not null default 0,
  note varchar(1024),
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  primary key (plan_id, tracker_month)
);

create table operation_journal_entries (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  entry_date date not null,
  title varchar(255) not null,
  amount numeric(19, 2) not null,
  entry_type varchar(32) not null,
  status varchar(32) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
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
  snapshot_json text,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

--rollback drop table if exists operation_journal_entries;
--rollback drop table if exists monthly_tracker_entries;
--rollback drop table if exists scenarios;
--rollback drop table if exists budget_classifications;
--rollback drop table if exists budget_envelopes;
--rollback drop table if exists budget_settings;
--rollback drop table if exists contributions;
--rollback drop table if exists goals;
--rollback drop table if exists expenses;
--rollback drop table if exists incomes;
--rollback drop table if exists inflation_rates;
--rollback drop table if exists model_assumptions;
--rollback drop table if exists pension_settings;
--rollback drop table if exists financial_plans;
--rollback drop table if exists user_profiles;

--changeset finguide:002-performance-indexes
create index idx_financial_plans_owner on financial_plans(owner_user_id, updated_at desc);
create index idx_incomes_plan on incomes(plan_id, sort_order);
create index idx_expenses_plan on expenses(plan_id, sort_order);
create index idx_goals_plan on goals(plan_id, priority);
create index idx_contributions_plan on contributions(plan_id, contribution_date desc);
create index idx_contributions_plan_goal on contributions(plan_id, goal_id);
create index idx_operation_journal_plan on operation_journal_entries(plan_id, entry_type, status);
create index idx_scenarios_plan on scenarios(plan_id, created_at);

--rollback drop index if exists idx_scenarios_plan;
--rollback drop index if exists idx_operation_journal_plan;
--rollback drop index if exists idx_contributions_plan_goal;
--rollback drop index if exists idx_contributions_plan;
--rollback drop index if exists idx_goals_plan;
--rollback drop index if exists idx_expenses_plan;
--rollback drop index if exists idx_incomes_plan;
--rollback drop index if exists idx_financial_plans_owner;

--changeset finguide:003-cashflow-growth-schedules
alter table incomes add column growth_schedule text;
alter table expenses add column growth_schedule text;

--rollback alter table expenses drop column growth_schedule;
--rollback alter table incomes drop column growth_schedule;
