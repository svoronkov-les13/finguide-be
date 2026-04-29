# FinGuide Excel model analytics

Source workbook: `Модель_P---56630d2a-6465-4036-bd42-9117c7dc9bd6.xlsx`  
SHA-256: `9f5b900aa95dcb8bb75f60abb3bdbd4a9c3c8cb99154b35d08ac9e89eaf7aff2`

This workbook is the reference financial model for FinGuide calculations. The API and backend must keep the frontend simple: frontend edits inputs and displays derived data; backend owns all projections, totals, savings, pension and scenario calculations.

## Workbook structure

| Sheet | Role | Backend module |
|---|---|---|
| `Вводные` | Source assumptions and user-entered inputs: years, age, inflation, investment return, income/expense lines, goal spending, retirement settings. | `plans`, `users`, `incomes`, `expenses`, `goals`, `pension`, `analytics` |
| `Доходы` | Year-by-year income flags, growth factors, monthly/yearly income totals. | `incomes`, `analytics` |
| `Расходы` | Year-by-year expense flags, growth factors, monthly/yearly expense totals. | `expenses`, `analytics` |
| `Цели` | Goal-related planned spending as monthly/yearly cash outflows. | `goals`, `analytics` |
| `Баланс` | Current-year balance split by monthly/yearly income and outflows. | `analytics` |
| `Сбережения` | Annual net savings and accumulated invested capital. | `analytics` |
| `Пенсия` | Two retirement variants: preserve capital and spend-down. | `pension`, `analytics` |

The workbook uses 2024 as start year in the sample and projects yearly columns out to 2076. Backend must not hard-code these years: store `startYear`, optional `projectionEndYear` / `horizonYears`, and build the projection dynamically.

## Input model that contract must support

### Global assumptions

From `Вводные`:

- `startYear` — first projection year.
- `birthYear` / derived current age.
- `monthsPerYear` — normally `12`.
- `inflationSchedule[]` — per-year inflation rates; the sample has 5% for first year, then 3%.
- `investmentReturnPct` — nominal annual return on invested capital; sample `6%`.
- `initialCapital` — current invested savings / balance if available; workbook sample effectively starts from zero in `Сбережения`, while product profile already has `initialBalance`.

### Income and expense lines

The workbook models both monthly and annual lines:

- monthly income: amount per month, active start/end year, per-year growth schedule;
- yearly income: amount per year, active start/end year, per-year growth schedule;
- monthly expense: amount per month, active start/end year, per-year growth schedule;
- yearly expense: amount per year, active start/end year, per-year growth schedule.

Formula pattern:

```txt
activeFlag(year) = year >= startYear && year <= endYear ? 1 : 0
growthFactor(baseYear) = 1
growthFactor(year) = growthFactor(previousYear) * (1 + growthRate[year])
annualMonthlyLineValue = monthlyAmount * monthsPerYear * growthFactor(year) * activeFlag(year)
annualYearlyLineValue = yearlyAmount * growthFactor(year) * activeFlag(year)
```

API inputs should use positive `amount` for both income and expenses. Backend normalizes signs internally. API outputs should expose outflows as positive values (`totalExpenses`, `totalGoalExpenses`) and net savings as `income - expenses - goalExpenses`.

### Goal spending

The sheet `Цели` is not just a list of target goals. It is a separate planned-spending stream:

- monthly goal expenses;
- yearly goal expenses;
- active period per line;
- growth schedule per line.

Contract impact: `Goal` must support recurring planned spending (`frequency`, `plannedAmount`, `startDate/endDate` or `startYear/endYear`, `growthSchedule`) in addition to one-time target goals from the UI.

## Derived calculations

### Income totals (`Доходы`)

```txt
totalMonthlyIncome[year] = sum(monthly income line annualized values)
totalYearlyIncome[year] = sum(yearly income line values)
totalIncome[year] = totalMonthlyIncome[year] + totalYearlyIncome[year]
```

### Expense totals (`Расходы`)

```txt
totalMonthlyExpenses[year] = sum(monthly expense line annualized values)
totalYearlyExpenses[year] = sum(yearly expense line values)
totalExpenses[year] = totalMonthlyExpenses[year] + totalYearlyExpenses[year]
```

### Goal-spending totals (`Цели`)

```txt
totalMonthlyGoalExpenses[year] = sum(monthly goal expense annualized values)
totalYearlyGoalExpenses[year] = sum(yearly goal expense values)
totalGoalExpenses[year] = totalMonthlyGoalExpenses[year] + totalYearlyGoalExpenses[year]
```

### Current-year balance (`Баланс`)

The workbook exposes current-year totals:

```txt
monthlyBalance = monthlyIncome - monthlyExpenses - monthlyGoalExpenses
yearlyBalance = yearlyIncome - yearlyExpenses - yearlyGoalExpenses
totalBalance = monthlyBalance + yearlyBalance
```

Contract impact: add a current balance endpoint so dashboard can render the workbook-style income/outflow split without duplicating formulas on frontend.

### Savings and accumulated capital (`Сбережения`)

Workbook pattern:

```txt
annualSavings[year] = totalIncome[year] - totalExpenses[year] - totalGoalExpenses[year]
capitalEndOfYear[startYear] = initialCapital * (1 + investmentReturnPct[startYear]) + annualSavings[startYear]
capitalEndOfYear[year] = capitalEndOfYear[previousYear] * (1 + investmentReturnPct[year]) + annualSavings[year]
```

The sample workbook uses a constant nominal return of 6%.

### Pension preserve-capital variant (`Пенсия`, rows 10-21)

Inputs:

- retirement age;
- retirement year;
- projected capital at retirement;
- nominal investment return;
- average inflation before retirement.

Formula pattern:

```txt
retirementYear = currentYear + (retirementAge - currentAge)
capitalAtRetirement = accumulatedCapital[retirementYear]
averageInflationPct = average(inflationPct from currentYear to retirementYear)
realReturnPct = (1 + nominalReturnPct) / (1 + averageInflationPct) - 1
annualSpendableAtRetirement = capitalAtRetirement * realReturnPct
annualSpendableCurrentPrices = annualSpendableAtRetirement * discountFactorToCurrentPrices
monthlySpendableCurrentPrices = annualSpendableCurrentPrices / 12
```

This answers: “If I never spend principal, how much can I spend from real return?”

### Pension spend-down variant (`Пенсия`, rows 26-45)

Inputs:

- desired monthly retirement spending in current prices;
- same retirement capital, return and inflation assumptions.

Formula pattern:

```txt
desiredAnnualCurrentPrices = desiredMonthlyCurrentPrices * 12
desiredAnnualAtRetirement = desiredAnnualCurrentPrices / discountFactorToCurrentPrices
plannedExpense[retirementYear] = desiredAnnualAtRetirement
plannedExpense[nextYear] = plannedExpense[previousYear] * (1 + averageInflationPct)
capitalEndOfYear = (capitalStartOfYear - plannedExpense) * (1 + nominalReturnPct)
retirementYears = count(years where capitalEndOfYear >= 0)
depletionAge = retirementAge + retirementYears
```

This answers: “If I spend the target amount, at what age does capital run out?”

## Contract changes required

1. Add model assumptions to plan state: start year, projection horizon/end year, months per year, inflation schedule, investment return, initial capital.
2. Add optional per-year `growthSchedule[]` and active year bounds to incomes, expenses and recurring goals.
3. Add a rich yearly cashflow projection endpoint with income, expenses, goal expenses, annual savings and accumulated capital.
4. Add current-year balance endpoint mirroring `Баланс`.
5. Add pension projection endpoint with both variants: preserve-capital and spend-down.
6. Keep `dashboard`, `health`, and scenario comparison as summaries built on top of the same calculation engine.
7. Import/export must support the Excel model (`type=excel_model`) in addition to JSON/CSV/PDF outputs.

## Backend implementation notes

Recommended analytics package split:

```txt
analytics/
  ModelAssumptions           global assumptions and schedules
  YearRatePoint              per-year rate, percent points in API
  CashFlowProjectionPoint    yearly workbook-style cashflow row
  BalanceSnapshot            current-year Баланс output
  ProjectionCalculator       income/expense/goal/savings timeline
  DashboardCalculator        summary cards from projection
pension/
  PensionProjection          preserve-capital + spend-down result
  PensionSpendDownPoint      yearly retirement depletion row
```

Rate convention: API fields ending with `Pct` are percent points (`6` means 6%). Calculation code should convert them to decimal (`0.06`) internally.
