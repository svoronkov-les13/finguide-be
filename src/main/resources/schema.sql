drop table if exists operation_journal_entries;
drop table if exists monthly_tracker_entries;
drop table if exists scenarios;
drop table if exists budget_classifications;
drop table if exists budget_envelopes;
drop table if exists budget_settings;
drop table if exists contributions;
drop table if exists goals;
drop table if exists expenses;
drop table if exists incomes;
drop table if exists inflation_rates;
drop table if exists model_assumptions;
drop table if exists pension_settings;
drop table if exists financial_plans;
drop table if exists user_profiles;

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
  snapshot_json clob,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);
