insert into user_profiles (id, keycloak_subject, email, name, phone, avatar_url, age, gender, initial_balance, created_at, updated_at)
values (
  '11111111-1111-4111-8111-111111111111',
  'demo-seed',
  'alex.petrov@example.com',
  'Александр Петров',
  '+7 (903) 555-12-34',
  null,
  32,
  'male',
  2500000,
  '2026-04-28T09:00:00Z',
  '2026-04-28T09:00:00Z'
);

insert into financial_plans (id, owner_user_id, name, base_currency, created_at, updated_at)
values (
  '22222222-2222-4222-8222-222222222222',
  '11111111-1111-4111-8111-111111111111',
  'Основной план',
  'RUB',
  '2026-04-28T09:00:00Z',
  '2026-04-28T09:00:00Z'
);

insert into pension_settings (plan_id, current_age, retirement_age, monthly_expenses, desired_monthly_expenses_current_prices, currency, expected_return_pct, inflation_pct, withdrawal_strategy, state_pension_enabled, state_pension_monthly)
values (
  '22222222-2222-4222-8222-222222222222',
  32,
  60,
  120000,
  120000,
  'RUB',
  9,
  7,
  'spend_down_30y',
  true,
  22000
);

insert into model_assumptions (plan_id, start_year, projection_end_year, horizon_years, birth_year, months_per_year, currency, initial_capital, investment_return_pct, source_model)
values (
  '22222222-2222-4222-8222-222222222222',
  2024,
  2076,
  53,
  1993,
  12,
  'RUB',
  2500000,
  6,
  'Модель_P sha256:9f5b900aa95dcb8bb75f60abb3bdbd4a9c3c8cb99154b35d08ac9e89eaf7aff2'
);

insert into inflation_rates (plan_id, rate_year, rate_pct) values
('22222222-2222-4222-8222-222222222222', 2024, 5),
('22222222-2222-4222-8222-222222222222', 2025, 3),
('22222222-2222-4222-8222-222222222222', 2026, 3),
('22222222-2222-4222-8222-222222222222', 2027, 3);

insert into incomes (id, plan_id, name, amount, currency, frequency, growth_type, growth_pct, start_date, end_date, sort_order, created_at, updated_at) values
('33333333-3333-4333-8333-333333333331', '22222222-2222-4222-8222-222222222222', 'Зарплата', 280000, 'RUB', 'monthly', 'manual', 8, '2024-01-01', null, 1, '2026-04-28T09:00:00Z', '2026-04-28T09:00:00Z'),
('33333333-3333-4333-8333-333333333332', '22222222-2222-4222-8222-222222222222', 'Фриланс-проекты', 65000, 'RUB', 'monthly', 'manual', 5, '2024-06-01', null, 2, '2026-04-28T09:00:00Z', '2026-04-28T09:00:00Z'),
('33333333-3333-4333-8333-333333333333', '22222222-2222-4222-8222-222222222222', 'Дивиденды', 180000, 'RUB', 'yearly', 'manual', 10, '2025-04-15', null, 3, '2026-04-28T09:00:00Z', '2026-04-28T09:00:00Z');

insert into expenses (id, plan_id, name, amount, currency, frequency, growth_type, growth_pct, growth_label, budget_class, start_date, end_date, sort_order, created_at, updated_at) values
('44444444-4444-4444-8444-444444444441', '22222222-2222-4222-8222-222222222222', 'Ипотека', 85000, 'RUB', 'monthly', 'manual', 0, 'Фикс. ставка', 'needs', '2023-01-01', '2043-01-01', 1, '2026-04-28T09:00:00Z', '2026-04-28T09:00:00Z'),
('44444444-4444-4444-8444-444444444442', '22222222-2222-4222-8222-222222222222', 'Продукты и быт', 42000, 'RUB', 'monthly', 'inflation', 7, 'Инфляция', 'needs', '2024-01-01', null, 2, '2026-04-28T09:00:00Z', '2026-04-28T09:00:00Z'),
('44444444-4444-4444-8444-444444444443', '22222222-2222-4222-8222-222222222222', 'Рестораны и кафе', 22000, 'RUB', 'monthly', 'manual', 4, '+4% / год', 'wants', '2024-01-01', null, 3, '2026-04-28T09:00:00Z', '2026-04-28T09:00:00Z');

insert into goals (id, plan_id, name, icon, current_cost, saved_amount, currency, target_year, type, growth_type, growth_pct, index_label, priority, created_at, updated_at) values
('55555555-5555-4555-8555-555555555551', '22222222-2222-4222-8222-222222222222', 'Финансовая подушка', 'shield', 1500000, 0, 'RUB', 2027, 'one_time', 'inflation', 7, 'Инфляция', 1, '2026-04-28T09:00:00Z', '2026-04-28T09:00:00Z'),
('55555555-5555-4555-8555-555555555552', '22222222-2222-4222-8222-222222222222', 'Первый взнос на квартиру', 'home', 5000000, 0, 'RUB', 2029, 'one_time', 'manual', 8, '+8% / год', 2, '2026-04-28T09:00:00Z', '2026-04-28T09:00:00Z'),
('55555555-5555-4555-8555-555555555553', '22222222-2222-4222-8222-222222222222', 'Новый автомобиль', 'car', 3500000, 0, 'RUB', 2028, 'one_time', 'inflation', 5, 'Инфляция', 3, '2026-04-28T09:00:00Z', '2026-04-28T09:00:00Z');
