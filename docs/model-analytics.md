# Аналитическая Excel-модель FinGuide

Исходный Excel-файл: `Модель_P---56630d2a-6465-4036-bd42-9117c7dc9bd6.xlsx`<br>
SHA-256: `9f5b900aa95dcb8bb75f60abb3bdbd4a9c3c8cb99154b35d08ac9e89eaf7aff2`

Этот Excel-файл — эталонная финансовая модель для расчётов FinGuide. API и бэкенд должны сохранять фронтенд простым: фронтенд редактирует входные данные и показывает результаты; бэкенд владеет всеми проекциями, итогами, сбережениями, пенсионными расчётами и сценариями.

## Структура Excel-файла

| Лист | Роль | Модуль бэкенда |
|---|---|---|
| `Вводные` | Исходные предположения и пользовательские входные данные: годы, возраст, инфляция, доходность инвестированного капитала, доходы/расходы, расходы на цели, пенсионные настройки. | `plans`, `users`, `incomes`, `expenses`, `goals`, `pension`, `analytics` |
| `Доходы` | Годовые флаги получения дохода, коэффициенты роста, итоги по ежемесячным и ежегодным доходам. | `incomes`, `analytics` |
| `Расходы` | Годовые флаги расходов, коэффициенты роста, итоги по ежемесячным и ежегодным расходам. | `expenses`, `analytics` |
| `Цели` | Плановые расходы на цели как ежемесячные и ежегодные исходящие денежные потоки. | `goals`, `analytics` |
| `Баланс` | Баланс текущего года: доходы и исходящие платежи в разрезе ежемесячных и ежегодных сумм. | `analytics` |
| `Сбережения` | Годовые чистые сбережения и накопленный инвестированный капитал. | `analytics` |
| `Пенсия` | Два пенсионных варианта: сохранение капитала и расходование капитала. | `pension`, `analytics` |

В примере Excel-файла стартовый год — 2024, годовые колонки идут до 2076. Бэкенд не должен зашивать эти годы в код: нужно хранить `startYear`, опционально `projectionEndYear` / `horizonYears` и строить проекцию динамически.

## Входная модель, которую должен поддерживать контракт

### Глобальные предположения

Из листа `Вводные`:

- `startYear` — первый год проекции;
- `birthYear` / вычисленный текущий возраст;
- `monthsPerYear` — обычно `12`;
- `inflationSchedule[]` — ставки инфляции по годам; в примере: 5% в первый год, дальше 3%;
- `investmentReturnPct` — номинальная годовая доходность инвестированного капитала; в примере: `6%`;
- `initialCapital` — текущие инвестированные сбережения / баланс, если есть. В примере Excel-файла лист `Сбережения` фактически стартует с нуля, а профиль продукта уже содержит `initialBalance`.

### Строки доходов и расходов

Excel-модель описывает ежемесячные и ежегодные строки:

- ежемесячный доход: сумма в месяц, год начала/окончания активности, график роста по годам;
- ежегодный доход: сумма в год, год начала/окончания активности, график роста по годам;
- ежемесячный расход: сумма в месяц, год начала/окончания активности, график роста по годам;
- ежегодный расход: сумма в год, год начала/окончания активности, график роста по годам.

Паттерн формул:

```txt
activeFlag(year) = year >= startYear && year <= endYear ? 1 : 0
growthFactor(baseYear) = 1
growthFactor(year) = growthFactor(previousYear) * (1 + growthRate[year])
annualMonthlyLineValue = monthlyAmount * monthsPerYear * growthFactor(year) * activeFlag(year)
annualYearlyLineValue = yearlyAmount * growthFactor(year) * activeFlag(year)
```

Входные данные API должны использовать положительный `amount` и для доходов, и для расходов. Бэкенд сам нормализует знаки внутри. Выходные данные API должны отдавать исходящие платежи положительными значениями (`totalExpenses`, `totalGoalExpenses`), а `netSavings` считать как `income - expenses - goalExpenses`.

### Расходы на цели

Лист `Цели` — это не только список целевых целей. Это отдельный поток плановых расходов:

- ежемесячные расходы на цели;
- ежегодные расходы на цели;
- период активности по каждой строке;
- график роста по каждой строке.

Влияние на контракт: `Goal` должен поддерживать регулярные плановые расходы (`frequency`, `plannedAmount`, `startDate/endDate` или `startYear/endYear`, `growthSchedule`) дополнительно к разовым целям из интерфейса.

## Производные расчёты

### Итоги доходов (`Доходы`)

```txt
totalMonthlyIncome[year] = sum(monthly income line annualized values)
totalYearlyIncome[year] = sum(yearly income line values)
totalIncome[year] = totalMonthlyIncome[year] + totalYearlyIncome[year]
```

### Итоги расходов (`Расходы`)

```txt
totalMonthlyExpenses[year] = sum(monthly expense line annualized values)
totalYearlyExpenses[year] = sum(yearly expense line values)
totalExpenses[year] = totalMonthlyExpenses[year] + totalYearlyExpenses[year]
```

### Итоги расходов на цели (`Цели`)

```txt
totalMonthlyGoalExpenses[year] = sum(monthly goal expense annualized values)
totalYearlyGoalExpenses[year] = sum(yearly goal expense values)
totalGoalExpenses[year] = totalMonthlyGoalExpenses[year] + totalYearlyGoalExpenses[year]
```

### Баланс текущего года (`Баланс`)

Excel-модель отдаёт итоги текущего года:

```txt
monthlyBalance = monthlyIncome - monthlyExpenses - monthlyGoalExpenses
yearlyBalance = yearlyIncome - yearlyExpenses - yearlyGoalExpenses
totalBalance = monthlyBalance + yearlyBalance
```

Влияние на контракт: нужен метод API текущего баланса, чтобы дашборд мог показать разрез доходов и исходящих платежей как в Excel-модели без дублирования формул на фронтенде.

### Сбережения и накопленный капитал (`Сбережения`)

Паттерн Excel-модели:

```txt
annualSavings[year] = totalIncome[year] - totalExpenses[year] - totalGoalExpenses[year]
capitalEndOfYear[startYear] = initialCapital * (1 + investmentReturnPct[startYear]) + annualSavings[startYear]
capitalEndOfYear[year] = capitalEndOfYear[previousYear] * (1 + investmentReturnPct[year]) + annualSavings[year]
```

В примере Excel-файла используется постоянная номинальная доходность `6%`.

### Пенсионный вариант «сохранение капитала» (`Пенсия`, строки 10-21)

Входные данные:

- возраст выхода на пенсию;
- год выхода на пенсию;
- прогнозный капитал на момент выхода на пенсию;
- номинальная доходность инвестиций;
- средняя инфляция до выхода на пенсию.

Паттерн формул:

```txt
retirementYear = currentYear + (retirementAge - currentAge)
capitalAtRetirement = accumulatedCapital[retirementYear]
averageInflationPct = average(inflationPct from currentYear to retirementYear)
realReturnPct = (1 + nominalReturnPct) / (1 + averageInflationPct) - 1
annualSpendableAtRetirement = capitalAtRetirement * realReturnPct
annualSpendableCurrentPrices = annualSpendableAtRetirement * discountFactorToCurrentPrices
monthlySpendableCurrentPrices = annualSpendableCurrentPrices / 12
```

Этот вариант отвечает на вопрос: «Если не тратить основной капитал, сколько можно тратить с реальной доходности?»

### Пенсионный вариант «расходование капитала» (`Пенсия`, строки 26-45)

Входные данные:

- желаемый месячный расход на пенсии в текущих ценах;
- тот же пенсионный капитал, доходность и предположения по инфляции.

Паттерн формул:

```txt
desiredAnnualCurrentPrices = desiredMonthlyCurrentPrices * 12
desiredAnnualAtRetirement = desiredAnnualCurrentPrices / discountFactorToCurrentPrices
plannedExpense[retirementYear] = desiredAnnualAtRetirement
plannedExpense[nextYear] = plannedExpense[previousYear] * (1 + averageInflationPct)
capitalEndOfYear = (capitalStartOfYear - plannedExpense) * (1 + nominalReturnPct)
retirementYears = count(years where capitalEndOfYear >= 0)
depletionAge = retirementAge + retirementYears
```

Этот вариант отвечает на вопрос: «Если тратить целевой уровень расходов, в каком возрасте закончится капитал?»

## Требуемые изменения контракта

1. Добавить предположения модели в состояние плана: стартовый год, горизонт/год окончания, `monthsPerYear`, график инфляции, инвестиционную доходность, стартовый капитал.
2. Добавить опциональный `growthSchedule[]` по годам и границы активных лет в доходы, расходы и регулярные цели.
3. Добавить подробный метод API годового денежного потока с доходами, расходами, расходами на цели, годовыми сбережениями и накопленным капиталом.
4. Добавить метод API текущего баланса, который зеркалит лист `Баланс`.
5. Добавить метод API пенсионной проекции с двумя вариантами: сохранение капитала и расходование капитала.
6. `dashboard`, `health` и сравнение сценариев должны быть сводками поверх одного расчётного движка.
7. Импорт/экспорт должен поддерживать Excel-модель (`type=excel_model`) дополнительно к выгрузкам JSON/CSV/PDF.

## Заметки по реализации бэкенда

Рекомендуемое разделение пакета `analytics`:

```txt
analytics/
  ModelAssumptions           глобальные предположения и графики
  YearRatePoint              ставка по году; в API — процентные пункты
  CashFlowProjectionPoint    годовая строка денежного потока в стиле Excel-модели
  BalanceSnapshot            результат текущего года из листа Баланс
  ProjectionCalculator       временная шкала доходов, расходов, целей и сбережений
  DashboardCalculator        карточки-сводки из проекции
pension/
  PensionProjection          результат вариантов сохранения и расходования капитала
  PensionSpendDownPoint      годовая строка расходования пенсионного капитала
```

Соглашение по ставкам: поля API с суффиксом `Pct` — процентные пункты (`6` значит 6%). Расчётный код должен переводить их в десятичную ставку (`0.06`) внутри.
