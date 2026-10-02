# Аналитика текущего backend

Эта страница описывает не исходный Numbers-файл, а расчётную модель, которая сейчас реально работает в Spring Boot backend.

Главные точки входа:

- код расчёта: `PlanReadService`;
- HTTP boundary: `PlanReadController`;
- JSON mapping: `PlanApiMapper`;
- сценарии: `ScenarioService`;
- пенсионный required capital: `PensionRequiredCapitalCalculator`.

Numbers-модель остаётся только историческим источником формул. Runtime source of truth — persisted `PlanState` из backend.

## Карта аналитических endpoints

Базовый URL production:

```txt
https://finguide.les13.tech/finguide-api/api/v1
```

| Endpoint | Что возвращает | Основной расчёт |
| --- | --- | --- |
| `GET /plans/{planId}/dashboard` | KPI главного экрана, краткий прогноз, цели, pension estimate | `dashboard()` поверх годового cashflow |
| `GET /plans/{planId}/analytics/cashflow?years=N` | годовые строки доходов, расходов, целей, net savings и капитала | `cashflow(planId, years)` |
| `GET /plans/{planId}/analytics/cashflow/monthly` | 12 помесячных строк для tracker/chart | `monthlyCashflow()` |
| `GET /plans/{planId}/analytics/projection?years=N` | упрощённый yearly projection | wrapper над `cashflow()` |
| `GET /plans/{planId}/analytics/balance/current` | снимок текущего года | текущие доходы/расходы/цели |
| `GET /plans/{planId}/analytics/health` | health score и причины | `dashboard()` + количество income sources |
| `GET /plans/{planId}/pension/projection` | preserve-capital и spend-down pension model | `pensionProjection()` |
| `POST /scenarios/compare` | base/optimistic/pessimistic/user scenario projections | `ScenarioService.compare()` |

`/analytics/cashflow` принимает `years`, но backend clamp'ит значение в диапазон `1..80`. `/analytics/projection` принимает `years` в диапазоне `1..60`.

## Runtime inputs

Расчёт собирается из persisted state:

- `ModelAssumptions`: `startYear`, `horizonYears`, `birthYear`, `monthsPerYear`, `currency`, `initialCapital`, `investmentReturnPct`, `inflationSchedule`;
- `PensionSettings`: возраст, пенсионный возраст, желаемые расходы, пенсионная доходность, пенсионная инфляция, стратегия, государственная пенсия;
- `IncomeSource`: сумма, частота, рост, даты активности, `continueAfterRetirement`;
- `ExpenseItem`: сумма, частота, рост, budget class, даты активности;
- `Goal`: стоимость, накопленный факт `savedAmount`, месяц/год цели, рост, priority;
- `MonthlyTrackerEntry`: фактическая сумма накопления по месяцу.

Legacy `contributions` читаются для совместимости, но write-path отключён. `operation_journal_entries` существует для tracker page, но goal-операции сейчас отклоняются backend'ом. В текущем расчётном ядре фактический прогресс целей задаётся через `Goal.savedAmount`, а месячный план-факт накоплений — через monthly tracker.

## Время и горизонт

Backend не начинает прогноз в прошлом:

```txt
startYear = max(currentCalendarYear, modelAssumptions.startYear)
```

Горизонт по умолчанию:

```txt
if modelAssumptions.horizonYears exists:
  horizon = max(1, horizonYears)
else:
  horizon = max(1, latestGoalYear - startYear + 1)
```

Пенсионный год:

```txt
yearsToRetirement = max(0, pension.retirementAge - pension.currentAge)
retirementYear = startYear + yearsToRetirement
retired = year >= retirementYear
```

## Рост сумм

Доходы и расходы используют один паттерн growth:

```txt
amountAtOffset = amount * product(1 + rateForYear / 100)
```

Для строк с `growthType=inflation` backend берёт:

- `item.growthSchedule`, если он задан;
- иначе `modelAssumptions.inflationSchedule`;
- fallback — `pension.inflationPct`.

Для строк без inflation-growth используется `item.growthPct`, но годовой `growthSchedule` всё равно может переопределить ставку конкретного года.

Активность строки считается по пересечению `startDate/endDate` с годом или месяцем прогноза.

## Годовой cashflow

`GET /analytics/cashflow` возвращает строки `CashFlowProjectionPoint`.

Ключевые поля:

```txt
year
age
periodNo
monthlyIncome
yearlyIncome
totalIncome
monthlyExpenses
yearlyExpenses
totalExpenses
monthlyGoalExpenses
yearlyGoalExpenses
totalGoalExpenses
netSavings
investmentReturnPct
capitalStartOfYear
capitalEndOfYear
```

Для каждого года backend считает:

```txt
monthlyIncomeTotal = sum(active monthly incomes for each month)
yearlyIncome = sum(active yearly/one-time incomes)
totalIncome = monthlyIncomeTotal + yearlyIncome

monthlyExpensesTotal = sum(active monthly expenses for each month)
yearlyExpenses = sum(active yearly/one-time expenses)
totalExpenses = monthlyExpensesTotal + yearlyExpenses
```

Затем считает месячные накопления:

```txt
plannedMonthlySavings = monthlyIncomeForMonth - monthlyExpensesForMonth
monthlySavings = trackerAmountForMonth if exists else plannedMonthlySavings
```

Важно: `MonthlyTrackerEntry.status` сам по себе не участвует в формуле. На расчёт влияет сохранённый `amount`; для `missed` frontend обычно передаёт `0`.

Цели в годовом cashflow сейчас попадают как плановый outflow в месяц/год цели:

```txt
targetCost = currentCost grown until targetYear
remaining = max(targetCost - savedAmount, 0)
plannedGoalExpenses[targetYear-targetMonth] += remaining
```

Годовая формула:

```txt
yearlyGoalExpenses = sum(planned goal outflows in this year)
netSavings = yearlyIncome - yearlyExpenses + sum(monthlySavings) - yearlyGoalExpenses
capitalEndOfYear =
  capitalStartOfYear
  + netSavings
  + max(capitalStartOfYear, 0) * investmentReturnPctForYear / 100
```

До пенсионного года `investmentReturnPctForYear = modelAssumptions.investmentReturnPct`. Начиная с пенсионного года — `pension.expectedReturnPct`.

## Помесячный cashflow

`GET /analytics/cashflow/monthly` возвращает 12 месяцев от `startYear`.

Отличия от годового cashflow:

- `income` и `expenses` считаются по месяцу;
- yearly/one-time income и expenses добавляются в декабре;
- investment return добавляется в декабре и считается от `capitalStartOfYear`;
- monthly tracker заменяет только месячную часть savings;
- goal outflow применяется в конкретном target month.

Строка содержит:

```txt
month
year
monthNumber
age
income
expenses
goalExpenses
netSavings
investmentReturnPct
capitalStartOfMonth
capitalEndOfMonth
```

## Retirement mode внутри cashflow

Начиная с `retirementYear` меняется источник monthly income/expenses.

Доходы после пенсии:

```txt
income = active incomes with continueAfterRetirement=true
if statePensionEnabled:
  income += statePensionMonthly grown by pension.inflationPct
```

Расходы после пенсии:

```txt
expenses = active regular expenses
expenses += desiredMonthlyExpensesCurrentPrices grown by pension.inflationPct
```

Если `desiredMonthlyExpensesCurrentPrices` отсутствует, используется `pension.monthlyExpenses`.

Поэтому на графике после пенсионного года может оставаться доход: это либо доходная строка с `continueAfterRetirement=true`, либо государственная пенсия, проиндексированная инфляцией.

## Dashboard

`GET /plans/{planId}/dashboard` — это сводка поверх текущего состояния и cashflow.

Основные вычисления:

```txt
currentYearGoalExpenses = first cashflow row totalGoalExpenses

netMonthlyBalance =
  monthlyIncome - monthlyExpenses - currentYearGoalExpenses / 12

netYearlyBalance =
  yearlyIncome - yearlyExpenses - currentYearGoalExpenses

savingsRatePct =
  yearlyIncome == 0 ? 0 : netYearlyBalance / yearlyIncome * 100

monthlyGoalContribution =
  max(yearlyIncome - yearlyExpenses, 0) / 12

availableForPension =
  max(netMonthlyBalance, 0)

projectedPensionCapital =
  initialCapital + availableForPension * 12 * yearsToRetirement
```

`dashboard.projectedPensionCapital` — грубая dashboard-оценка накопления, не required capital. Для вопроса «сколько капитала нужно на пенсии» используется `/pension/projection`.

`dashboard.yearlyProjection` — первые 4 строки `cashflow` с полями `year`, `income`, `expenses`, `goalsCost`, `netSavings`.

## Health score

`GET /analytics/health` считается из dashboard:

```txt
score = min(
  100,
  savingsRatePct
  + emergencyFundPct / 4
  + incomeSourcesCount * 5
)
```

Компоненты:

- `savings_rate`: good от `20%`, warning от `10%`;
- `emergency_fund`: good от `100%`, warning от `50%`;
- `diversification`: good при 3+ источниках дохода.

Emergency fund ищется как первая цель, где имя содержит `подушка`. Target — 6 месяцев текущих monthly expenses.

## Goal projections

`GET /plans/current` возвращает цели с дополнительными projected-полями:

```txt
projectedTargetCost
projectedSavedAmount
projectedProgressPct
projectedReachable
projectedCompletionYear
```

Расчёт идёт отдельно от `cashflow` через `goalAllocationPlan()`.

Алгоритм:

```txt
pool = max(initialCapital, 0)
goals = sort by priority, targetYear, targetMonth, id

for each projected month:
  monthlyFreeCashflow =
    monthlyIncomeForMonth
    - monthlyExpensesForMonth
    + yearlyFreeCashflowShare

  monthlyFreeCashflow = trackerAmount if tracker exists else monthlyFreeCashflow
  pool += monthlyFreeCashflow

  for goal in goals:
    allocate min(pool, remainingTargetCost)
```

Для reachability важен target month: сумма, накопленная после дедлайна цели, не помогает выполнить цель вовремя. `projectedProgressPct` считается как `(savedAmount + projected allocation by deadline) / projectedTargetCost`.

## Pension projection

`GET /pension/projection` возвращает две модели: `preserveCapital` и `spendDown`.

Общие поля:

```txt
currentAge
retirementAge
retirementYear
capitalAtRetirement
nominalReturnPct
averageInflationPct
realReturnPct
```

`capitalAtRetirement` берётся из `cashflow` на год выхода на пенсию. Если пользователь уже на пенсии, используется `initialCapital`.

`averageInflationPct` — средняя ставка из `modelAssumptions.inflationSchedule`; если schedule пустой, берётся `pension.inflationPct`.

### Preserve capital

Смысл: жить на доходность капитала, не тратя principal.

```txt
annualSpendableAtRetirement =
  capitalAtRetirement * pension.expectedReturnPct / 100
  + statePensionMonthly * 12 if enabled

annualSpendableCurrentPrices =
  discount(annualSpendableAtRetirement, averageInflationPct, yearsToRetirement)

monthlySpendableCurrentPrices =
  annualSpendableCurrentPrices / 12
```

Отдельно считается required capital:

```txt
desiredAnnualAtRetirement =
  desiredMonthlyExpensesCurrentPrices * 12
  * (1 + pension.inflationPct / 100) ^ yearsToRetirement

statePensionAnnualAtRetirement =
  statePensionEnabled
    ? statePensionMonthly * 12 * inflationFactor
    : 0

annualNeed =
  max(desiredAnnualAtRetirement - statePensionAnnualAtRetirement, 0)

realReturnPct =
  pension.expectedReturnPct - pension.inflationPct

requiredCapitalAtRetirement =
  annualNeed / (realReturnPct / 100)
```

Если `annualNeed > 0`, а `realReturnPct <= 0`, backend возвращает `requiredCapitalAtRetirement = null` и `requiredCapitalStatus = non_positive_real_return`.

### Spend down

Смысл: постепенно расходовать капитал.

Displayed series:

```txt
desiredAnnualExpensesAtRetirement =
  desiredMonthlyExpensesCurrentPrices * 12
  grown by averageInflationPct until retirement

for each retirement year in projection horizon:
  investmentReturn = max(beginningCapital, 0) * expectedReturnPct / 100
  plannedExpense = retirement expenses + active one-time/yearly expenses - retirement income
  endingCapital = beginningCapital + investmentReturn - plannedExpense
```

`retirementYears` для series берётся из общего горизонта модели:

```txt
retirementYears = max(1, projectionHorizon - yearsToRetirement)
```

`spendDown.requiredCapitalAtRetirement` считается отдельно на фиксированные `30` лет обратным проходом:

```txt
required = 0
for year from 29 down to 0:
  withdrawal = annualNeed * (1 + pension.inflationPct / 100) ^ year
  required = (required + withdrawal) / (1 + pension.expectedReturnPct / 100)
```

## Scenarios

`POST /scenarios/compare` не мутирует план. Он строит adjusted copy:

- income amounts умножаются на `incomeAdjPct`;
- expense amounts и пенсионные расходы умножаются на `expenseAdjPct`;
- goal currentCost умножается на `goalsCostAdjPct`;
- investment return и pension expected return сдвигаются на `returnAdjPct`;
- inflation schedule и pension inflation сдвигаются на `inflationAdjPct`;
- retirement age сдвигается на `retirementAgeShift`.

Затем `ScenarioService` вызывает:

```txt
PlanReadService.cashflow(adjusted, 30)
```

Важное ограничение текущей реализации: scenario compare использует static cashflow без monthly tracker facts. Поэтому tracker влияет на base `/analytics/cashflow`, но не влияет на optimistic/pessimistic/user scenario lines.

## Что сейчас точно не делает backend

- Не считает analytics от Apple Numbers-файла в runtime.
- Не пишет и не учитывает legacy contribution ledger как текущий write-path: create/update disabled.
- Не принимает `type=goal` в operation journal: backend возвращает ошибку и просит использовать savings tracker и `Goal.savedAmount`.
- Не учитывает monthly tracker в `/scenarios/compare`.
- Не считает `dashboard.projectedPensionCapital` как required pension capital.

## Историческая Numbers-модель

Apple Numbers-файл `Модель_P_3---3c875af3-ffe2-4e95-96e6-0b82f82a7a40.numbers` был исходным reference для первых формул доходов, расходов, целей, сбережений и пенсии.

Текущий backend сохранил несколько идей из файла:

- доходы/расходы имеют период активности и growth;
- cashflow разделяет income, expenses, goal outflow, net savings и capital;
- pension projection имеет preserve-capital и spend-down варианты.

Но фактическая документация для продукта должна смотреть на текущие endpoints и `PlanReadService`, потому что runtime-модель уже шире и местами отличается от Numbers.
