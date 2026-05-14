# Аналитическая модель FinGuide

Актуальный эталон: Apple Numbers-файл `Модель_P_3---3c875af3-ffe2-4e95-96e6-0b82f82a7a40.numbers`.<br>
SHA-256: `b586ff7ecbb78bc2807c797217cb8f787e747403663056947051c64c987cd988`

Файл — источник формул для расчётного ядра FinGuide. Фронтенд должен оставаться тонким: он редактирует входные данные и показывает результаты, а backend владеет проекциями доходов, расходов, сбережений, целей, пенсионных сценариев и сводок.

Текущий backend уже реализует persisted plan state, cashflow/projection/balance/pension endpoints и projected goal progress. Важная договорённость для продукта: **одноразовые цели из UI не являются расходами cashflow**. Они финансируются из свободного денежного потока. Отдельный лист `Цели` в Numbers описывает именно регулярные/плановые расходы на цели; такие строки, если будут заведены как recurring goal expenses, могут входить в cashflow отдельно.

## Структура Numbers-файла

| Лист | Роль | Backend-модуль |
|---|---|---|
| `Вводные` | Глобальные параметры и входные строки: годы, возраст, инфляция, инвестиционная доходность, доходы, расходы, расходы на цели, пенсионные настройки. | `plans`, `analytics`, `incomes`, `expenses`, `goals`, `pension` |
| `Доходы` | Годовые флаги активности доходов, коэффициенты роста, годовые суммы по monthly/yearly income lines. | `incomes`, `analytics` |
| `Расходы` | Годовые флаги активности расходов, коэффициенты роста, годовые суммы по monthly/yearly expense lines. | `expenses`, `analytics` |
| `Цели` | Регулярные расходы на цели: monthly/yearly goal expense lines, флаги активности, рост, итоговый поток. | `goals`, `analytics` |
| `Баланс` | Снимок текущего года: доходы, расходы и итоговый баланс по monthly/yearly/total. | `analytics` |
| `Сбережения` | Годовые сбережения и накопленный капитал. | `analytics` |
| `Пенсия` | Два пенсионных сценария: жить на проценты и расходовать капитал. | `pension`, `analytics` |

## Базовые предположения файла

Из листа `Вводные`:

- `startYear = 2024`;
- `birthYear = 1993`;
- `monthsPerYear = 12`;
- горизонт в годовых колонках — до `2076`;
- валюта примера — `USD`;
- `investmentReturnPct = 10%` (`0.1` в Numbers);
- инфляция по годам в текущем файле — `0%`;
- retirement age — `50`;
- желаемые пенсионные расходы — `-10000 USD / мес.`.

Ставки в файле Numbers хранятся как десятичные значения (`0.1` = 10%). В API поля с суффиксом `Pct` отдаются как процентные пункты (`10` = 10%), а расчётный код переводит их во внутреннюю decimal-ставку.

## Общий паттерн строк

Доходы, расходы и регулярные расходы на цели используют одинаковый паттерн:

```txt
activeFlag(line, year) = year >= line.startYear && year <= line.endYear ? 1 : 0
factor[line, startYear] = 1 * (1 + growthRate[line, startYear])
factor[line, year] = factor[line, previousYear] * (1 + growthRate[line, year])
annualMonthlyLineValue[line, year] = monthlyAmount * monthsPerYear * factor[line, year] * activeFlag(line, year)
annualYearlyLineValue[line, year] = yearlyAmount * factor[line, year] * activeFlag(line, year)
```

В Numbers расходы заведены отрицательными суммами, поэтому итоги расходов и goal expenses тоже отрицательные. В API удобнее принимать положительные `amount` для расходов, а в расчётах нормализовать знак внутри.

## Доходы

Пример входов:

- monthly incomes: `Income 1 = 10000`, `Income 2 = 2000`, `Income 3 = 1000` USD/мес.;
- `Income 2` активен только в `2024`;
- `Income 1` в `2025` имеет рост `50%`, дальше часть строк следует инфляции;
- yearly incomes в текущем файле равны `0`.

Формулы листа `Доходы`:

```txt
totalMonthlyIncome[year] = sum(monthly income annualized lines)
totalYearlyIncome[year] = sum(yearly income lines)
totalIncome[year] = totalMonthlyIncome[year] + totalYearlyIncome[year]
```

Контрольные значения из файла:

| Year | totalIncome |
|---:|---:|
| 2024 | 156000 |
| 2025 | 192360 |
| 2026 | 198130.8 |
| 2027 | 204074.7 |
| 2028 | 210196.0 |

## Расходы

Пример входов:

- monthly expenses: 8 строк по `-1000 USD / мес.` (`Еда`, `Садик`, `Няня`, `Ипотека`, `Аренда`, `Связь`, `Командировки`, `Прочее`);
- yearly expenses: 7 строк по `-3000 USD / год.`;
- часть расходов имеет конечные годы: ипотека/страховка по ипотеке до `2050`, детский сад/школа до `2040`, отдельные долги до `2025`/`2028`.

Формулы листа `Расходы`:

```txt
totalMonthlyExpenses[year] = sum(monthly expense annualized lines)
totalYearlyExpenses[year] = sum(yearly expense lines)
totalExpenses[year] = totalMonthlyExpenses[year] + totalYearlyExpenses[year]
```

Контрольные значения из файла:

| Year | totalExpenses |
|---:|---:|
| 2024 | -117000 |
| 2025 | -119880 |
| 2026 | -119846.4 |
| 2027 | -122901.8 |
| 2028 | -126048.8 |

## Цели: регулярные расходы vs продуктовые цели

Лист `Цели` в Numbers моделирует **регулярные расходы на цели**, а не прогресс одноразовых целей из UI.

Пример в файле:

- monthly goal expense lines сейчас равны `0`;
- yearly goal expense `Отдых = -20000 USD / год`;
- активность goal expense lines задана периодом `2020..2060`;
- рост goal expenses берётся из соответствующего growth schedule.

Формулы листа `Цели`:

```txt
totalMonthlyGoalExpenses[year] = sum(monthly goal expense annualized lines)
totalYearlyGoalExpenses[year] = sum(yearly goal expense lines)
totalGoalExpenses[year] = totalMonthlyGoalExpenses[year] + totalYearlyGoalExpenses[year]
```

Контрольные значения:

| Year | totalGoalExpenses |
|---:|---:|
| 2024 | -20000 |
| 2025 | -20600 |
| 2026 | -21218 |
| 2027 | -21854.5 |
| 2028 | -22510.2 |

### Продуктовая интерпретация

Для текущих UI-целей backend считает прогнозный прогресс отдельно:

```txt
freeCashflow[year] = totalIncome[year] - normalizedExpenses[year]
# если используются signed Numbers values: freeCashflow = totalIncome + totalExpenses
pool += freeCashflow[year]
for goal in goals sorted by priority, targetYear, id:
  targetCost = currentCost grown by goal.growthPct until targetYear
  allocated = min(pool, targetCost - previouslyAllocatedToGoal)
  projectedSavedAmount += allocated
  pool -= allocated
```

Поэтому:

- `netSavings` в API не должен уменьшаться на одноразовые цели из UI;
- `totalGoalExpenses` нужно использовать только для явных recurring/planned goal expense lines, если они представлены в модели;
- `Goal.savedAmount` остаётся фактическим/ledger-состоянием, а прогнозные поля — отдельные: `projectedTargetCost`, `projectedSavedAmount`, `projectedProgressPct`, `projectedReachable`, `projectedCompletionYear`.

## Баланс текущего года

Лист `Баланс` берёт текущий год из итогов `Доходы`, `Расходы`, `Цели`:

```txt
monthlyBalance = monthlyIncome + monthlyExpenses + monthlyGoalExpenses
yearlyBalance = yearlyIncome + yearlyExpenses + yearlyGoalExpenses
totalBalance = monthlyBalance + yearlyBalance
```

Контрольный снимок для `2024`:

| Metric | Income | Expense | Balance |
|---|---:|---:|---:|
| Monthly | 156000 | -96000 | 60000 |
| Yearly | 0 | -41000 | -41000 |
| Total | 156000 | -137000 | 19000 |

В API это соответствует `GET /plans/{planId}/analytics/balance/current`.

## Сбережения и капитал

Numbers-модель считает сбережения как сумму строк `Доходы`, `Расходы`, `Расходы на цели` с их знаками:

```txt
annualSavings[year] = totalIncome[year] + totalExpenses[year] + totalGoalExpenses[year]
returnFactor[startYear] = 1
returnFactor[year > startYear] = 1 + investmentReturnPct
capitalEndOfYear[startYear] = annualSavings[startYear]
capitalEndOfYear[year] = annualSavings[year] + capitalEndOfYear[previousYear] * returnFactor[year]
```

Контрольные значения:

| Year | annualSavings | capitalEndOfYear |
|---:|---:|---:|
| 2024 | 19000 | 19000 |
| 2025 | 51880 | 70880 |
| 2026 | 57066.4 | 132199.2 |
| 2027 | 59318.4 | 199449.5 |
| 2028 | 61637.9 | 273054.5 |
| 2043 | 114333.8 | 2614402.2 |

Для текущего продукта одноразовые UI-цели не должны входить в эту формулу как `goalExpenses`; они живут в отдельной projection allocation модели выше.

## Пенсия: вариант «жить на проценты»

Лист `Пенсия`, строки `10..21`.

Входы и формулы:

```txt
retirementAge = 50
currentAge = YEAR(TODAY()) - birthYear
retirementYear = YEAR(TODAY()) + (retirementAge - currentAge)
capitalAtRetirement = accumulatedCapital[retirementYear]
averageInflationPct = average(inflationPct from current year to retirementYear)
realReturnPct = (1 + nominalReturnPct) / (1 + averageInflationPct) - 1
annualSpendableAtRetirement = capitalAtRetirement * realReturnPct
annualSpendableCurrentPrices = annualSpendableAtRetirement * discountFactor[retirementYear]
monthlySpendableCurrentPrices = annualSpendableCurrentPrices / 12
```

Контрольный результат текущего файла:

| Metric | Value |
|---|---:|
| retirementYear | 2043 |
| capitalAtRetirement | 2614402.2 |
| nominalReturnPct | 10% |
| averageInflationPct | 0% |
| monthlySpendableCurrentPrices | 3328.4 |

## Пенсия: вариант «расходовать капитал»

Лист `Пенсия`, строки `26..45`.

Формулы:

```txt
desiredAnnualCurrentPrices = desiredMonthlyCurrentPrices * 12
desiredAnnualAtRetirement = desiredAnnualCurrentPrices / discountFactor[retirementYear]
plannedExpense[retirementYear] = desiredAnnualAtRetirement
plannedExpense[nextYear] = plannedExpense[previousYear] * (1 + averageInflationPct)
capitalEndOfYear = (capitalStartOfYear + plannedExpense) * (1 + nominalReturnPct)
retirementYears = count(years where capitalEndOfYear >= 0)
depletionAge = retirementAge + retirementYears
```

`plannedExpense` отрицательный, поэтому в формуле используется сумма `capitalStartOfYear + plannedExpense`.

Контрольный результат:

| Metric | Value |
|---|---:|
| desiredMonthlyCurrentPrices | -10000 |
| desiredAnnualAtRetirement | -220941.8 |
| retirementYears | 13 |
| depletionAge | 63 |

## Контрактные выводы

1. `analytics/cashflow` должен показывать раздельно `totalIncome`, `totalExpenses`, `totalGoalExpenses`, `netSavings`, `capitalEndOfYear`.
2. `netSavings` для текущих одноразовых UI-целей считается без вычитания goal allocation. Если появятся recurring goal expense lines из листа `Цели`, они должны быть отдельным источником `totalGoalExpenses`.
3. `analytics/balance/current` должен зеркалить лист `Баланс` по текущему году.
4. `analytics/projection` и `pension/projection` должны строиться из одного расчётного ядра, а `dashboard`, `health`, `scenarios/compare` — быть сводками поверх него.
5. `Goal` должен разделять фактические поля (`currentCost`, `savedAmount`) и прогнозные поля (`projected*`), чтобы отображение прогресса не портило данные редактирования.
6. Import/export в будущем должен уметь принимать Numbers/Excel-модель как отдельный тип источника, но backend не должен зашивать конкретные годы/строки из этого файла.

## Заметки по реализации backend

Рекомендуемые доменные блоки:

```txt
analytics/
  ModelAssumptions           стартовый год, горизонт, ставки, monthsPerYear
  YearRatePoint              ставка по году
  CashFlowProjectionPoint    годовая строка доходов/расходов/сбережений
  BalanceSnapshot            снимок листа Баланс
  GoalAllocation             прогнозная аллокация free cashflow в UI-цели
  ProjectionCalculator       единое расчётное ядро
  DashboardCalculator        сводки dashboard/health/scenarios
pension/
  PensionProjection          результат двух пенсионных вариантов
  PensionSpendDownPoint      годовая строка расходования капитала
```
