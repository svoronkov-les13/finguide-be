package les13.finguide.backend.plans;

import les13.finguide.backend.analytics.BalanceSnapshot;
import les13.finguide.backend.analytics.CashFlowProjectionPoint;
import les13.finguide.backend.analytics.HealthScore;
import les13.finguide.backend.analytics.GoalAllocation;
import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.analytics.YearlyProjectionPoint;
import les13.finguide.backend.pension.PensionProjection;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.pension.PensionSpendDownPoint;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.auth.PlanAccessService;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Service
public class PlanReadService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private final PlanStateRepository repository;
    private final PlanAccessService accessService;

    public PlanReadService(PlanStateRepository repository, PlanAccessService accessService) {
        this.repository = repository;
        this.accessService = accessService;
    }

    public PlanState currentPlan() {
        return accessService.requireCurrentPlan();
    }

    public Map<UUID, GoalAllocation> goalAllocations(PlanState state) {
        int horizon = projectionHorizon(state);
        Map<Integer, BigDecimal> actualGoalExpensesByYear = actualGoalExpensesByYear(state, horizon);
        Map<java.time.YearMonth, BigDecimal> monthlyTrackerSavingsByMonth = monthlyTrackerSavingsByMonth(state, horizon);
        return goalAllocationPlan(state, horizon, actualGoalExpensesByYear, monthlyTrackerSavingsByMonth).byGoal();
    }

    public PlanState plan(UUID planId) {
        return accessService.requirePlan(planId);
    }

    public Map<String, Object> dashboard(UUID planId) {
        PlanState state = plan(planId);
        BigDecimal monthlyIncome = monthlyIncome(state);
        BigDecimal yearlyIncome = yearlyIncome(state);
        BigDecimal monthlyExpenses = monthlyExpenses(state);
        BigDecimal yearlyExpenses = yearlyExpenses(state);
        List<CashFlowProjectionPoint> cashflow = cashflow(planId);
        BigDecimal currentYearGoalExpenses = cashflow.isEmpty() ? BigDecimal.ZERO : cashflow.get(0).totalGoalExpenses();
        BigDecimal netMonthly = monthlyIncome.subtract(monthlyExpenses).subtract(currentYearGoalExpenses.divide(TWELVE, 0, RoundingMode.HALF_UP));
        BigDecimal netYearly = yearlyIncome.subtract(yearlyExpenses).subtract(currentYearGoalExpenses);
        BigDecimal totalGoalsCost = state.goals().stream().map(Goal::currentCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGoalsSaved = state.goals().stream().map(Goal::savedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGoalsRemaining = totalGoalsCost.subtract(totalGoalsSaved).max(BigDecimal.ZERO);
        BigDecimal monthlyGoalContribution = recommendedMonthlyGoalContribution(state);
        BigDecimal savingsRate = yearlyIncome.signum() == 0
                ? BigDecimal.ZERO
                : netYearly.multiply(HUNDRED).divide(yearlyIncome, 1, RoundingMode.HALF_UP);
        BigDecimal availableForPension = netMonthly.max(BigDecimal.ZERO);
        int yearsToRetirement = Math.max(0, state.pension().retirementAge() - state.pension().currentAge());
        BigDecimal projectedPensionCapital = state.modelAssumptions().initialCapital()
                .add(availableForPension.multiply(TWELVE).multiply(BigDecimal.valueOf(yearsToRetirement)));
        BigDecimal emergencyFundTarget = monthlyExpenses.multiply(BigDecimal.valueOf(6));
        BigDecimal emergencyFundCurrent = state.goals().stream()
                .filter(goal -> goal.name().toLowerCase().contains("подушка"))
                .map(Goal::savedAmount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        BigDecimal emergencyFundPct = emergencyFundTarget.signum() == 0
                ? BigDecimal.ZERO
                : emergencyFundCurrent.multiply(HUNDRED).divide(emergencyFundTarget, 1, RoundingMode.HALF_UP).min(HUNDRED);
        List<Map<String, Object>> yearlyProjection = cashflow.stream()
                .limit(4)
                .map(point -> Map.<String, Object>of(
                        "year", point.year(),
                        "income", point.totalIncome(),
                        "expenses", point.totalExpenses(),
                        "goalsCost", point.totalGoalExpenses(),
                        "netSavings", point.netSavings()
                ))
                .toList();

        return Map.ofEntries(
                Map.entry("totalMonthlyIncome", monthlyIncome),
                Map.entry("totalYearlyIncome", yearlyIncome),
                Map.entry("totalMonthlyExpenses", monthlyExpenses),
                Map.entry("totalYearlyExpenses", yearlyExpenses),
                Map.entry("netMonthlyBalance", netMonthly),
                Map.entry("netYearlyBalance", netYearly),
                Map.entry("savingsRatePct", savingsRate),
                Map.entry("totalGoalsCost", totalGoalsCost),
                Map.entry("totalGoalsSaved", totalGoalsSaved),
                Map.entry("totalGoalsRemaining", totalGoalsRemaining),
                Map.entry("monthlyGoalContribution", monthlyGoalContribution),
                Map.entry("availableForPension", availableForPension),
                Map.entry("projectedPensionCapital", projectedPensionCapital),
                Map.entry("yearsToRetirement", yearsToRetirement),
                Map.entry("emergencyFundTarget", emergencyFundTarget),
                Map.entry("emergencyFundCurrent", emergencyFundCurrent),
                Map.entry("emergencyFundPct", emergencyFundPct),
                Map.entry("yearlyProjection", yearlyProjection)
        );
    }

    public HealthScore health(UUID planId) {
        Map<String, Object> dashboard = dashboard(planId);
        BigDecimal savingsRate = (BigDecimal) dashboard.get("savingsRatePct");
        BigDecimal emergencyFundPct = (BigDecimal) dashboard.get("emergencyFundPct");
        PlanState state = plan(planId);
        int incomeSources = state.incomes().size();
        int score = Math.min(100, savingsRate.intValue() + emergencyFundPct.divide(BigDecimal.valueOf(4), 0, RoundingMode.HALF_UP).intValue() + incomeSources * 5);
        return new HealthScore(score, List.of(
                new HealthScore.Item("savings_rate", "Норма сбережений", savingsRate, status(savingsRate, BigDecimal.valueOf(20), BigDecimal.valueOf(10)), "Цель — держать норму сбережений выше 20%"),
                new HealthScore.Item("emergency_fund", "Подушка безопасности", emergencyFundPct, status(emergencyFundPct, BigDecimal.valueOf(100), BigDecimal.valueOf(50)), "Резерв на 6 месяцев расходов"),
                new HealthScore.Item("diversification", "Диверсификация", incomeSources, incomeSources >= 3 ? HealthScore.Status.GOOD : HealthScore.Status.WARNING, "Добавьте 1-2 источника дохода для снижения риска")
        ));
    }

    public List<CashFlowProjectionPoint> cashflow(UUID planId) {
        PlanState state = plan(planId);
        int horizon = projectionHorizon(state);
        return cashflow(state, horizon, actualGoalExpensesByYear(state, horizon), monthlyTrackerSavingsByMonth(state, horizon));
    }

    public List<Map<String, Object>> monthlyCashflow(UUID planId) {
        PlanState state = plan(planId);
        return monthlyCashflow(state, 12, actualGoalExpensesByYear(state, 12), monthlyTrackerSavingsByMonth(state, 12));
    }

    private List<CashFlowProjectionPoint> cashflow(UUID planId, int horizon) {
        PlanState state = plan(planId);
        return cashflow(state, horizon, actualGoalExpensesByYear(state, horizon), monthlyTrackerSavingsByMonth(state, horizon));
    }

    public ModelAssumptions assumptions(UUID planId) {
        return plan(planId).modelAssumptions();
    }

    public ModelAssumptions updateAssumptions(UUID planId, ModelAssumptions request) {
        accessService.requireWritablePlan(planId);
        ModelAssumptions assumptions = validateAssumptions(request);
        return repository.updateModelAssumptions(planId, assumptions);
    }

    public PensionSettings pension(UUID planId) {
        return plan(planId).pension();
    }

    public PensionSettings updatePension(UUID planId, PensionSettings request) {
        accessService.requireWritablePlan(planId);
        PensionSettings pension = validatePensionSettings(request);
        return repository.updatePensionSettings(planId, pension);
    }

    public BalanceSnapshot currentBalance(UUID planId) {
        PlanState state = plan(planId);
        int year = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        BigDecimal monthlyIncome = monthlyIncome(state);
        BigDecimal yearlyIncome = yearlyOneTimeIncome(state);
        BigDecimal totalIncome = monthlyIncome.multiply(TWELVE).add(yearlyIncome);
        BigDecimal monthlyExpenses = monthlyExpenses(state);
        BigDecimal yearlyExpenses = yearlyOneTimeExpenses(state);
        BigDecimal monthlyGoalExpenses = BigDecimal.ZERO;
        Map<Integer, BigDecimal> actualGoalExpensesByYear = actualGoalExpensesByYear(state, 1);
        BigDecimal yearlyGoalExpenses = plannedGoalExpensesByYear(state, 1).getOrDefault(year, BigDecimal.ZERO)
                .add(actualGoalExpensesByYear.getOrDefault(year, BigDecimal.ZERO));
        BigDecimal goalExpenses = monthlyGoalExpenses.multiply(TWELVE).add(yearlyGoalExpenses);
        BigDecimal totalOutflow = monthlyExpenses.multiply(TWELVE).add(yearlyExpenses).add(goalExpenses);
        return new BalanceSnapshot(
                year,
                monthlyIncome,
                yearlyIncome,
                totalIncome,
                monthlyExpenses,
                yearlyExpenses,
                monthlyGoalExpenses,
                yearlyGoalExpenses,
                goalExpenses,
                totalOutflow,
                totalIncome.subtract(totalOutflow)
        );
    }

    public List<YearlyProjectionPoint> projection(UUID planId, int years) {
        if (years < 1 || years > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "years must be between 1 and 60");
        }
        return cashflow(planId, years).stream()
                .map(point -> new YearlyProjectionPoint(point.year(), point.totalIncome(), point.totalExpenses(), point.totalGoalExpenses(), point.netSavings()))
                .toList();
    }

    public PensionProjection pensionProjection(UUID planId) {
        PlanState state = plan(planId);
        PensionSettings pension = state.pension();
        int currentYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        int yearsToRetirement = Math.max(0, pension.retirementAge() - pension.currentAge());
        int retirementYear = currentYear + yearsToRetirement;
        BigDecimal capitalAtRetirement = capitalAtYearEnd(state, yearsToRetirement);
        BigDecimal nominalReturnPct = pension.expectedReturnPct();
        BigDecimal averageInflationPct = averageInflation(state);
        BigDecimal realReturnPct = nominalReturnPct.subtract(averageInflationPct).setScale(2, RoundingMode.HALF_UP);
        BigDecimal statePensionAnnual = pension.statePensionEnabled() ? pension.statePensionMonthly().multiply(TWELVE) : BigDecimal.ZERO;

        BigDecimal annualSpendableAtRetirement = capitalAtRetirement.multiply(nominalReturnPct).divide(HUNDRED, 2, RoundingMode.HALF_UP).add(statePensionAnnual);
        BigDecimal annualSpendableCurrentPrices = discount(annualSpendableAtRetirement, averageInflationPct, yearsToRetirement);
        PensionProjection.PreserveCapital preserveCapital = new PensionProjection.PreserveCapital(
                annualSpendableAtRetirement,
                annualSpendableCurrentPrices,
                annualSpendableCurrentPrices.divide(TWELVE, 2, RoundingMode.HALF_UP)
        );

        int retirementYears = retirementSpendDownYears(state, yearsToRetirement);
        BigDecimal desiredAnnualAtRetirement = inflate(pension.desiredMonthlyExpensesCurrentPrices().multiply(TWELVE), averageInflationPct, yearsToRetirement);
        List<PensionSpendDownPoint> series = spendDownSeries(state, retirementYear, pension.retirementAge(), capitalAtRetirement, desiredAnnualAtRetirement, nominalReturnPct, averageInflationPct, yearsToRetirement, retirementYears);
        int depletionAge = series.stream()
                .filter(point -> point.endingCapital().signum() <= 0)
                .map(PensionSpendDownPoint::age)
                .findFirst()
                .orElse(pension.retirementAge() + retirementYears);
        PensionProjection.SpendDown spendDown = new PensionProjection.SpendDown(
                pension.desiredMonthlyExpensesCurrentPrices(),
                desiredAnnualAtRetirement,
                retirementYears,
                depletionAge,
                series
        );

        return new PensionProjection(
                pension.currentAge(),
                pension.retirementAge(),
                retirementYear,
                capitalAtRetirement,
                nominalReturnPct,
                averageInflationPct,
                realReturnPct,
                preserveCapital,
                spendDown
        );
    }

    private static ModelAssumptions validateAssumptions(ModelAssumptions assumptions) {
        if (assumptions == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assumptions body is required");
        }
        if (assumptions.monthsPerYear() < 1 || assumptions.monthsPerYear() > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "monthsPerYear must be between 1 and 12");
        }
        if (assumptions.horizonYears() != null && (assumptions.horizonYears() < 1 || assumptions.horizonYears() > 80)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "horizonYears must be between 1 and 80");
        }
        if (assumptions.currency() == null || assumptions.currency().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency is required");
        }
        if (assumptions.initialCapital() == null || assumptions.investmentReturnPct() == null || assumptions.inflationSchedule() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "initialCapital, investmentReturnPct and inflationSchedule are required");
        }
        return assumptions;
    }

    private static PensionSettings validatePensionSettings(PensionSettings pension) {
        if (pension == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pension body is required");
        }
        if (pension.currentAge() < 16 || pension.currentAge() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currentAge must be between 16 and 100");
        }
        if (pension.retirementAge() < 40 || pension.retirementAge() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "retirementAge must be between 40 and 80");
        }
        requireNonNegative(pension.monthlyExpenses(), "monthlyExpenses");
        requireNonNegative(pension.desiredMonthlyExpensesCurrentPrices(), "desiredMonthlyExpensesCurrentPrices");
        requireNonNegative(pension.statePensionMonthly(), "statePensionMonthly");
        requirePercent(pension.expectedReturnPct(), "expectedReturnPct");
        requirePercent(pension.inflationPct(), "inflationPct");
        if (pension.currency() == null || !pension.currency().matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "currency must be a 3-letter uppercase code");
        }
        if (pension.withdrawalStrategy() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "withdrawalStrategy is required");
        }
        return pension;
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be non-negative");
        }
    }

    private static void requirePercent(BigDecimal value, String field) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(30)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be between 0 and 30");
        }
    }

    private BigDecimal capitalAtYearEnd(PlanState state, int yearsFromStart) {
        if (yearsFromStart <= 0) {
            return state.modelAssumptions().initialCapital();
        }
        return cashflow(state.plan().id(), yearsFromStart).get(yearsFromStart - 1).capitalEndOfYear();
    }

    public static List<CashFlowProjectionPoint> cashflow(PlanState state, int horizon) {
        return cashflow(state, horizon, Map.of(), Map.of());
    }

    private static List<CashFlowProjectionPoint> cashflow(PlanState state, int horizon, Map<Integer, BigDecimal> actualGoalExpensesByYear, Map<java.time.YearMonth, BigDecimal> monthlyTrackerSavingsByMonth) {
        int startYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        BigDecimal capital = state.modelAssumptions().initialCapital();
        Map<java.time.YearMonth, BigDecimal> plannedGoalExpensesByMonth = plannedGoalExpensesByMonth(state, horizon);
        List<CashFlowProjectionPoint> result = new ArrayList<>();
        for (int offset = 0; offset < horizon; offset++) {
            int year = startYear + offset;
            boolean retired = isRetirementYearOrLater(state, year);
            BigDecimal monthlyIncomeTotal = retired ? retirementMonthlyIncomeTotalForYear(state, year, offset) : monthlyIncomeTotalForYear(state, year, offset);
            BigDecimal monthlyIncome = monthlyIncomeTotal.divide(TWELVE, 2, RoundingMode.HALF_UP);
            BigDecimal yearlyIncome = yearlyOneTimeIncome(state, offset);
            BigDecimal totalIncome = monthlyIncomeTotal.add(yearlyIncome);
            BigDecimal monthlyExpensesTotal = retired ? retirementMonthlyExpensesTotalForYear(state, year, offset) : monthlyExpensesTotalForYear(state, year, offset);
            BigDecimal monthlyExpenses = monthlyExpensesTotal.divide(TWELVE, 2, RoundingMode.HALF_UP);
            BigDecimal yearlyExpenses = yearlyOneTimeExpenses(state, offset);
            BigDecimal totalExpenses = monthlyExpensesTotal.add(yearlyExpenses);
            BigDecimal monthlySavings = BigDecimal.ZERO;
            BigDecimal plannedGoalExpenses = BigDecimal.ZERO;
            for (int month = 1; month <= 12; month++) {
                java.time.YearMonth yearMonth = java.time.YearMonth.of(year, month);
                BigDecimal plannedMonthlySavings = monthlyIncomeForProjectionMonth(state, yearMonth, offset, retired)
                        .subtract(monthlyExpensesForProjectionMonth(state, yearMonth, offset, retired));
                monthlySavings = monthlySavings.add(monthlyTrackerSavingsByMonth.getOrDefault(yearMonth, plannedMonthlySavings));
                plannedGoalExpenses = plannedGoalExpenses.add(plannedGoalExpensesByMonth.getOrDefault(yearMonth, BigDecimal.ZERO));
            }
            BigDecimal yearlyGoalExpenses = plannedGoalExpenses.add(actualGoalExpensesByYear.getOrDefault(year, BigDecimal.ZERO));
            BigDecimal netSavings = yearlyIncome.subtract(yearlyExpenses).add(monthlySavings).subtract(yearlyGoalExpenses);
            BigDecimal capitalStart = capital;
            BigDecimal investmentReturn = investmentReturnPctForYear(state, year);
            capital = capital.add(netSavings).add(capital.max(BigDecimal.ZERO).multiply(investmentReturn).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            result.add(new CashFlowProjectionPoint(
                    year,
                    state.modelAssumptions().birthYear() == null ? null : year - state.modelAssumptions().birthYear(),
                    offset + 1,
                    monthlyIncome,
                    yearlyIncome,
                    totalIncome,
                    monthlyExpenses,
                    yearlyExpenses,
                    totalExpenses,
                    BigDecimal.ZERO,
                    yearlyGoalExpenses,
                    yearlyGoalExpenses,
                    netSavings,
                    investmentReturn,
                    capitalStart,
                    capital
            ));
        }
        return result;
    }

    private static List<Map<String, Object>> monthlyCashflow(PlanState state, int horizon, Map<Integer, BigDecimal> actualGoalExpensesByYear, Map<java.time.YearMonth, BigDecimal> monthlyTrackerSavingsByMonth) {
        int startYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        BigDecimal capital = state.modelAssumptions().initialCapital();
        Map<java.time.YearMonth, BigDecimal> plannedGoalExpensesByMonth = plannedGoalExpensesByMonth(state, horizon);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int offset = 0; offset < horizon; offset++) {
            int year = startYear + offset;
            BigDecimal yearlyIncome = yearlyOneTimeIncome(state, offset);
            BigDecimal yearlyExpenses = yearlyOneTimeExpenses(state, offset);
            BigDecimal investmentReturn = investmentReturnPctForYear(state, year);
            BigDecimal capitalStartOfYear = capital;
            for (int month = 1; month <= 12; month++) {
                java.time.YearMonth yearMonth = java.time.YearMonth.of(year, month);
                boolean retired = isRetirementYearOrLater(state, year);
                BigDecimal monthlyIncome = monthlyIncomeForProjectionMonth(state, yearMonth, offset, retired);
                BigDecimal monthlyExpenses = monthlyExpensesForProjectionMonth(state, yearMonth, offset, retired);
                BigDecimal plannedMonthlySavings = monthlyIncome.subtract(monthlyExpenses);
                BigDecimal income = monthlyIncome.add(month == 12 ? yearlyIncome : BigDecimal.ZERO);
                BigDecimal expenses = monthlyExpenses.add(month == 12 ? yearlyExpenses : BigDecimal.ZERO);
                BigDecimal savings = monthlyTrackerSavingsByMonth.getOrDefault(yearMonth, plannedMonthlySavings);
                BigDecimal goalExpenses = plannedGoalExpensesByMonth.getOrDefault(yearMonth, BigDecimal.ZERO)
                        .add(month == 1 ? actualGoalExpensesByYear.getOrDefault(year, BigDecimal.ZERO) : BigDecimal.ZERO);
                BigDecimal netSavings = savings.add(month == 12 ? yearlyIncome.subtract(yearlyExpenses) : BigDecimal.ZERO).subtract(goalExpenses);
                BigDecimal returnAmount = month == 12
                        ? capitalStartOfYear.max(BigDecimal.ZERO).multiply(investmentReturn).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                BigDecimal capitalStart = capital;
                capital = capital.add(netSavings).add(returnAmount);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("month", yearMonth.toString());
                row.put("year", year);
                row.put("monthNumber", month);
                row.put("age", state.modelAssumptions().birthYear() == null ? null : year - state.modelAssumptions().birthYear());
                row.put("income", income);
                row.put("expenses", expenses);
                row.put("goalExpenses", goalExpenses);
                row.put("netSavings", netSavings);
                row.put("investmentReturnPct", investmentReturn);
                row.put("capitalStartOfMonth", capitalStart);
                row.put("capitalEndOfMonth", capital);
                result.add(row);
            }
        }
        return result;
    }

    private static BigDecimal averageInflation(PlanState state) {
        List<BigDecimal> values = state.modelAssumptions().inflationSchedule().stream().map(point -> point.ratePct()).toList();
        return values.isEmpty() ? state.pension().inflationPct() : average(values).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<PensionSpendDownPoint> spendDownSeries(PlanState state, int startYear, int startAge, BigDecimal initialCapital, BigDecimal initialExpense, BigDecimal returnPct, BigDecimal inflationPct, int yearsToRetirement, int years) {
        List<PensionSpendDownPoint> result = new ArrayList<>();
        BigDecimal capital = initialCapital;
        for (int offset = 0; offset < years; offset++) {
            int projectionOffset = yearsToRetirement + offset;
            BigDecimal pensionExpense = inflate(initialExpense, inflationPct, offset);
            BigDecimal totalIncome = retirementIncomeTotalForYear(state, startYear + offset, projectionOffset);
            BigDecimal totalExpenses = yearlyOneTimeExpenses(state, projectionOffset)
                    .add(monthlyExpensesTotalForYear(state, startYear + offset, projectionOffset))
                    .add(pensionExpense);
            BigDecimal expense = totalExpenses.subtract(totalIncome).setScale(2, RoundingMode.HALF_UP);
            BigDecimal beginningCapital = capital;
            BigDecimal investmentReturn = beginningCapital.max(BigDecimal.ZERO).multiply(returnPct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            capital = beginningCapital.add(investmentReturn).subtract(expense).setScale(2, RoundingMode.HALF_UP);
            result.add(new PensionSpendDownPoint(startYear + offset, startAge + offset, beginningCapital, expense, returnPct, capital));
        }
        return result;
    }

    private static BigDecimal inflate(BigDecimal value, BigDecimal inflationPct, int years) {
        return compound(value, inflationPct, years);
    }

    private static BigDecimal discount(BigDecimal value, BigDecimal inflationPct, int years) {
        if (years <= 0) return value;
        BigDecimal multiplier = BigDecimal.ONE.add(inflationPct.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        return value.divide(multiplier.pow(years), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal compound(BigDecimal value, BigDecimal ratePct, int years) {
        if (years <= 0) return value.setScale(2, RoundingMode.HALF_UP);
        BigDecimal multiplier = BigDecimal.ONE.add(ratePct.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        return value.multiply(multiplier.pow(years)).setScale(2, RoundingMode.HALF_UP);
    }

    private static HealthScore.Status status(BigDecimal value, BigDecimal good, BigDecimal warning) {
        if (value.compareTo(good) >= 0) {
            return HealthScore.Status.GOOD;
        }
        if (value.compareTo(warning) >= 0) {
            return HealthScore.Status.WARNING;
        }
        return HealthScore.Status.BAD;
    }

    private static BigDecimal monthlyIncome(PlanState state) {
        return monthlyIncome(state, 0);
    }

    private static BigDecimal monthlyIncome(PlanState state, int offset) {
        return state.incomes().stream()
                .filter(item -> item.frequency() == IncomeSource.Frequency.MONTHLY)
                .filter(item -> activeInProjectionYear(item.startDate(), item.endDate(), projectionYear(state, offset)))
                .map(item -> grow(item.amount(), incomeGrowthPct(state, item), incomeGrowthSchedule(state, item), state.modelAssumptions().startYear(), offset))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal monthlyIncome(PlanState state, java.time.YearMonth month, int offset) {
        return state.incomes().stream()
                .filter(item -> item.frequency() == IncomeSource.Frequency.MONTHLY)
                .filter(item -> activeInProjectionMonth(item.startDate(), item.endDate(), month))
                .map(item -> grow(item.amount(), incomeGrowthPct(state, item), incomeGrowthSchedule(state, item), state.modelAssumptions().startYear(), offset))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal monthlyIncomeForProjectionMonth(PlanState state, java.time.YearMonth month, int offset, boolean retired) {
        BigDecimal income = monthlyIncome(state, month, offset);
        if (!retired || !state.pension().statePensionEnabled()) {
            return income;
        }
        return income.add(grow(state.pension().statePensionMonthly(), state.pension().inflationPct(), offset));
    }

    private static BigDecimal monthlyIncomeTotalForYear(PlanState state, int year, int offset) {
        BigDecimal total = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            total = total.add(monthlyIncome(state, java.time.YearMonth.of(year, month), offset));
        }
        return total;
    }

    private static BigDecimal retirementMonthlyIncomeTotalForYear(PlanState state, int year, int offset) {
        BigDecimal total = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            total = total.add(monthlyIncomeForProjectionMonth(state, java.time.YearMonth.of(year, month), offset, true));
        }
        return total;
    }

    private static BigDecimal retirementIncomeTotalForYear(PlanState state, int year, int offset) {
        return retirementMonthlyIncomeTotalForYear(state, year, offset).add(yearlyOneTimeIncome(state, offset));
    }

    private static BigDecimal yearlyIncome(PlanState state) {
        return monthlyIncome(state).multiply(TWELVE).add(yearlyOneTimeIncome(state));
    }

    private static BigDecimal yearlyOneTimeIncome(PlanState state) {
        return yearlyOneTimeIncome(state, 0);
    }

    private static BigDecimal yearlyOneTimeIncome(PlanState state, int offset) {
        return state.incomes().stream()
                .filter(item -> item.frequency() == IncomeSource.Frequency.YEARLY || item.frequency() == IncomeSource.Frequency.ONE_TIME)
                .filter(item -> activeInProjectionYear(item.startDate(), item.endDate(), projectionYear(state, offset)))
                .map(item -> grow(item.amount(), incomeGrowthPct(state, item), incomeGrowthSchedule(state, item), state.modelAssumptions().startYear(), offset))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal monthlyExpenses(PlanState state) {
        return monthlyExpenses(state, 0);
    }

    private static BigDecimal monthlyExpenses(PlanState state, int offset) {
        return state.expenses().stream()
                .filter(item -> item.frequency() == ExpenseItem.Frequency.MONTHLY)
                .filter(item -> activeInProjectionYear(item.startDate(), item.endDate(), projectionYear(state, offset)))
                .map(item -> grow(item.amount(), expenseGrowthPct(state, item), expenseGrowthSchedule(state, item), state.modelAssumptions().startYear(), offset))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal monthlyExpenses(PlanState state, java.time.YearMonth month, int offset) {
        return state.expenses().stream()
                .filter(item -> item.frequency() == ExpenseItem.Frequency.MONTHLY)
                .filter(item -> activeInProjectionMonth(item.startDate(), item.endDate(), month))
                .map(item -> grow(item.amount(), expenseGrowthPct(state, item), expenseGrowthSchedule(state, item), state.modelAssumptions().startYear(), offset))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal monthlyExpensesForProjectionMonth(PlanState state, java.time.YearMonth month, int offset, boolean retired) {
        BigDecimal expenses = monthlyExpenses(state, month, offset);
        if (!retired) {
            return expenses;
        }
        return expenses.add(grow(retirementMonthlyExpenses(state), state.pension().inflationPct(), offset));
    }

    private static BigDecimal monthlyExpensesTotalForYear(PlanState state, int year, int offset) {
        BigDecimal total = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            total = total.add(monthlyExpenses(state, java.time.YearMonth.of(year, month), offset));
        }
        return total;
    }

    private static BigDecimal retirementMonthlyExpensesTotalForYear(PlanState state, int year, int offset) {
        BigDecimal total = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            total = total.add(monthlyExpensesForProjectionMonth(state, java.time.YearMonth.of(year, month), offset, true));
        }
        return total;
    }

    private static BigDecimal retirementMonthlyExpenses(PlanState state) {
        BigDecimal desired = state.pension().desiredMonthlyExpensesCurrentPrices();
        return desired == null ? state.pension().monthlyExpenses() : desired;
    }

    private static BigDecimal yearlyExpenses(PlanState state) {
        return monthlyExpenses(state).multiply(TWELVE).add(yearlyOneTimeExpenses(state));
    }

    private static BigDecimal yearlyOneTimeExpenses(PlanState state) {
        return yearlyOneTimeExpenses(state, 0);
    }

    private static BigDecimal yearlyOneTimeExpenses(PlanState state, int offset) {
        return state.expenses().stream()
                .filter(item -> item.frequency() == ExpenseItem.Frequency.YEARLY || item.frequency() == ExpenseItem.Frequency.ONE_TIME)
                .filter(item -> activeInProjectionYear(item.startDate(), item.endDate(), projectionYear(state, offset)))
                .map(item -> grow(item.amount(), expenseGrowthPct(state, item), expenseGrowthSchedule(state, item), state.modelAssumptions().startYear(), offset))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int projectionYear(PlanState state, int offset) {
        return Math.max(Year.now().getValue(), state.modelAssumptions().startYear()) + offset;
    }

    private static int retirementYear(PlanState state) {
        int startYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        return startYear + Math.max(0, state.pension().retirementAge() - state.pension().currentAge());
    }

    private static boolean isRetirementYearOrLater(PlanState state, int year) {
        return year >= retirementYear(state);
    }

    private static BigDecimal investmentReturnPctForYear(PlanState state, int year) {
        return isRetirementYearOrLater(state, year)
                ? state.pension().expectedReturnPct()
                : state.modelAssumptions().investmentReturnPct();
    }

    private static int retirementSpendDownYears(PlanState state, int yearsToRetirement) {
        return Math.max(1, projectionHorizon(state) - yearsToRetirement);
    }

    private static boolean activeInProjectionYear(LocalDate startDate, LocalDate endDate, int year) {
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        boolean started = startDate == null || !startDate.isAfter(yearEnd);
        boolean notEnded = endDate == null || !endDate.isBefore(yearStart);
        return started && notEnded;
    }

    private static boolean activeInProjectionMonth(LocalDate startDate, LocalDate endDate, java.time.YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        boolean started = startDate == null || !startDate.isAfter(monthEnd);
        boolean notEnded = endDate == null || !endDate.isBefore(monthStart);
        return started && notEnded;
    }

    private static BigDecimal incomeGrowthPct(PlanState state, IncomeSource item) {
        return item.growthType() == IncomeSource.GrowthType.INFLATION ? state.pension().inflationPct() : item.growthPct();
    }

    private static List<les13.finguide.backend.analytics.YearRatePoint> incomeGrowthSchedule(PlanState state, IncomeSource item) {
        return item.growthType() == IncomeSource.GrowthType.INFLATION && (item.growthSchedule() == null || item.growthSchedule().isEmpty())
                ? state.modelAssumptions().inflationSchedule()
                : item.growthSchedule();
    }

    private static BigDecimal expenseGrowthPct(PlanState state, ExpenseItem item) {
        return item.growthType() == ExpenseItem.GrowthType.INFLATION ? state.pension().inflationPct() : item.growthPct();
    }

    private static List<les13.finguide.backend.analytics.YearRatePoint> expenseGrowthSchedule(PlanState state, ExpenseItem item) {
        return item.growthType() == ExpenseItem.GrowthType.INFLATION && (item.growthSchedule() == null || item.growthSchedule().isEmpty())
                ? state.modelAssumptions().inflationSchedule()
                : item.growthSchedule();
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal grow(BigDecimal value, BigDecimal growthPct, int years) {
        return grow(value, growthPct, List.of(), 0, years);
    }

    private static BigDecimal grow(BigDecimal value, BigDecimal growthPct, List<les13.finguide.backend.analytics.YearRatePoint> schedule, int startYear, int years) {
        BigDecimal result = value;
        for (int i = 0; i < years; i++) {
            BigDecimal ratePct = growthRateForYear(schedule, startYear + i, growthPct);
            BigDecimal multiplier = BigDecimal.ONE.add(ratePct.divide(HUNDRED, 8, RoundingMode.HALF_UP));
            result = result.multiply(multiplier);
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal growthRateForYear(List<les13.finguide.backend.analytics.YearRatePoint> schedule, int year, BigDecimal fallback) {
        if (schedule == null || schedule.isEmpty()) {
            return fallback;
        }
        return schedule.stream()
                .filter(point -> point.year() == year)
                .map(les13.finguide.backend.analytics.YearRatePoint::ratePct)
                .findFirst()
                .orElse(fallback);
    }

    private static BigDecimal goalsForYear(PlanState state, int year) {
        return state.goals().stream()
                .filter(goal -> goal.targetYear() == year)
                .map(goal -> goal.currentCost().subtract(goal.savedAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal recommendedMonthlyGoalContribution(PlanState state) {
        return yearlyIncome(state)
                .subtract(yearlyExpenses(state))
                .max(BigDecimal.ZERO)
                .divide(TWELVE, 0, RoundingMode.HALF_UP);
    }

    private static int projectionHorizon(PlanState state) {
        Integer horizonYears = state.modelAssumptions().horizonYears();
        if (horizonYears != null) {
            return Math.max(1, horizonYears);
        }
        int currentYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        int latestGoalYear = state.goals().stream().mapToInt(Goal::targetYear).max().orElse(currentYear);
        return Math.max(1, latestGoalYear - currentYear + 1);
    }

    private Map<Integer, BigDecimal> actualGoalExpensesByYear(PlanState state, int horizon) {
        return Map.of();
    }

    private Map<java.time.YearMonth, BigDecimal> monthlyTrackerSavingsByMonth(PlanState state, int horizon) {
        int startYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        Map<java.time.YearMonth, BigDecimal> byMonth = new HashMap<>();
        for (int offset = 0; offset < horizon; offset++) {
            int year = startYear + offset;
            repository.findMonthlyTrackerEntries(state.plan().id(), year).forEach(entry ->
                    byMonth.put(entry.month(), entry.amount() == null ? BigDecimal.ZERO : entry.amount().max(BigDecimal.ZERO))
            );
        }
        return byMonth;
    }

    private static Map<Integer, BigDecimal> plannedGoalExpensesByYear(PlanState state, int horizon) {
        Map<Integer, BigDecimal> byYear = new LinkedHashMap<>();
        plannedGoalExpensesByMonth(state, horizon).forEach((month, amount) -> byYear.merge(month.getYear(), amount, BigDecimal::add));
        return byYear;
    }

    private static Map<java.time.YearMonth, BigDecimal> plannedGoalExpensesByMonth(PlanState state, int horizon) {
        int startYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        int endYear = startYear + horizon - 1;
        Map<java.time.YearMonth, BigDecimal> byMonth = new LinkedHashMap<>();
        for (Goal goal : state.goals()) {
            if (goal.targetYear() < startYear || goal.targetYear() > endYear) {
                continue;
            }
            BigDecimal targetCost = targetCost(state, goal, startYear);
            BigDecimal remaining = targetCost.subtract(goal.savedAmount()).max(BigDecimal.ZERO);
            byMonth.merge(java.time.YearMonth.of(goal.targetYear(), goal.targetMonth()), remaining, BigDecimal::add);
        }
        return byMonth;
    }

    private static GoalAllocationPlan goalAllocationPlan(PlanState state, int horizon) {
        return goalAllocationPlan(state, horizon, Map.of(), Map.of());
    }

    private static GoalAllocationPlan goalAllocationPlan(PlanState state, int horizon, Map<Integer, BigDecimal> actualGoalExpensesByYear) {
        return goalAllocationPlan(state, horizon, actualGoalExpensesByYear, Map.of());
    }

    private static GoalAllocationPlan goalAllocationPlan(PlanState state, int horizon, Map<Integer, BigDecimal> actualGoalExpensesByYear, Map<java.time.YearMonth, BigDecimal> monthlyTrackerSavingsByMonth) {
        int startYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        List<Goal> goals = state.goals().stream()
                .sorted(Comparator.comparingInt(Goal::priority).thenComparingInt(Goal::targetYear).thenComparingInt(Goal::targetMonth).thenComparing(Goal::id))
                .toList();

        Map<UUID, BigDecimal> targetCosts = new LinkedHashMap<>();
        Map<UUID, BigDecimal> projectedAllocatedByGoal = new LinkedHashMap<>();
        Map<UUID, BigDecimal> totalFundedByGoal = new LinkedHashMap<>();
        Map<UUID, Integer> completionYears = new HashMap<>();
        Map<UUID, Boolean> reachableByGoal = new HashMap<>();
        for (Goal goal : goals) {
            BigDecimal targetCost = targetCost(state, goal, startYear);
            targetCosts.put(goal.id(), targetCost);
            projectedAllocatedByGoal.put(goal.id(), BigDecimal.ZERO);
            totalFundedByGoal.put(goal.id(), goal.savedAmount().max(BigDecimal.ZERO));
            if (goal.savedAmount().compareTo(targetCost) >= 0) {
                completionYears.put(goal.id(), startYear);
                reachableByGoal.put(goal.id(), true);
            }
        }

        Map<Integer, BigDecimal> allocatedByYear = new LinkedHashMap<>();
        BigDecimal pool = state.modelAssumptions().initialCapital().max(BigDecimal.ZERO);
        int monthsPerYear = state.modelAssumptions().monthsPerYear();
        for (int monthIndex = 1; monthIndex <= horizon * monthsPerYear; monthIndex++) {
            int offset = (monthIndex - 1) / monthsPerYear;
            int month = ((monthIndex - 1) % monthsPerYear) + 1;
            int year = startYear + offset;
            java.time.YearMonth yearMonth = java.time.YearMonth.of(year, month);
            boolean retired = isRetirementYearOrLater(state, year);
            BigDecimal yearlyFreeCashflowShare = yearlyOneTimeIncome(state, offset)
                    .subtract(yearlyOneTimeExpenses(state, offset))
                    .divide(BigDecimal.valueOf(monthsPerYear), 2, RoundingMode.HALF_UP);
            BigDecimal plannedMonthlyFreeCashflow = monthlyIncomeForProjectionMonth(state, yearMonth, offset, retired)
                    .subtract(monthlyExpensesForProjectionMonth(state, yearMonth, offset, retired))
                    .add(yearlyFreeCashflowShare);
            BigDecimal monthlyFreeCashflow = monthlyTrackerSavingsByMonth.getOrDefault(yearMonth, plannedMonthlyFreeCashflow);
            BigDecimal actualGoalExpenses = month == 1 ? actualGoalExpensesByYear.getOrDefault(year, BigDecimal.ZERO) : BigDecimal.ZERO;
            pool = pool.add(monthlyFreeCashflow.subtract(actualGoalExpenses));
            BigDecimal monthlyAllocation = BigDecimal.ZERO;
            for (Goal goal : goals) {
                BigDecimal remaining = targetCosts.get(goal.id()).subtract(totalFundedByGoal.get(goal.id())).max(BigDecimal.ZERO);
                if (remaining.signum() == 0 || pool.signum() <= 0) {
                    continue;
                }
                BigDecimal allocation = pool.min(remaining);
                pool = pool.subtract(allocation);
                monthlyAllocation = monthlyAllocation.add(allocation);
                projectedAllocatedByGoal.put(goal.id(), projectedAllocatedByGoal.get(goal.id()).add(allocation));
                BigDecimal nextTotalFunded = totalFundedByGoal.get(goal.id()).add(allocation);
                totalFundedByGoal.put(goal.id(), nextTotalFunded);
                if (nextTotalFunded.compareTo(targetCosts.get(goal.id())) >= 0) {
                    completionYears.putIfAbsent(goal.id(), year);
                    if (monthIndex <= targetMonthIndex(goal, startYear, monthsPerYear)) {
                        reachableByGoal.putIfAbsent(goal.id(), true);
                    }
                }
            }
            allocatedByYear.merge(year, monthlyAllocation, BigDecimal::add);
        }
        for (int offset = 0; offset < horizon; offset++) {
            int year = startYear + offset;
            allocatedByYear.put(year, allocatedByYear.getOrDefault(year, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        }

        Map<UUID, GoalAllocation> byGoal = new LinkedHashMap<>();
        for (Goal goal : goals) {
            BigDecimal targetCost = targetCosts.get(goal.id()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal projectedAmount = projectedAllocatedByGoal.get(goal.id()).min(targetCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalFunded = goal.savedAmount().max(BigDecimal.ZERO).add(projectedAmount).min(targetCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal progressPct = targetCost.signum() == 0
                    ? HUNDRED
                    : totalFunded.multiply(HUNDRED).divide(targetCost, 1, RoundingMode.HALF_UP).min(HUNDRED);
            boolean reachable = reachableByGoal.getOrDefault(goal.id(), false);
            byGoal.put(goal.id(), new GoalAllocation(
                    targetCost,
                    projectedAmount,
                    progressPct,
                    reachable,
                    completionYears.get(goal.id())
            ));
        }
        return new GoalAllocationPlan(byGoal, allocatedByYear);
    }

    private static BigDecimal freeCashflow(PlanState state, int offset) {
        BigDecimal monthlyIncome = monthlyIncome(state, offset);
        BigDecimal yearlyIncome = yearlyOneTimeIncome(state, offset);
        BigDecimal totalIncome = monthlyIncome.multiply(TWELVE).add(yearlyIncome);
        BigDecimal monthlyExpenses = monthlyExpenses(state, offset);
        BigDecimal yearlyExpenses = yearlyOneTimeExpenses(state, offset);
        BigDecimal totalExpenses = monthlyExpenses.multiply(TWELVE).add(yearlyExpenses);
        return totalIncome.subtract(totalExpenses).max(BigDecimal.ZERO);
    }

    private static int targetMonthIndex(Goal goal, int startYear, int monthsPerYear) {
        return Math.max(1, (goal.targetYear() - startYear) * monthsPerYear + goal.targetMonth());
    }

    private static BigDecimal targetCost(PlanState state, Goal goal, int currentYear) {
        if (goal.growthType() == Goal.GrowthType.NONE) {
            return goal.currentCost().setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal growthPct = goal.growthType() == Goal.GrowthType.INFLATION ? state.pension().inflationPct() : goal.growthPct();
        return grow(goal.currentCost(), growthPct, Math.max(0, goal.targetYear() - currentYear));
    }

    private record GoalAllocationPlan(Map<UUID, GoalAllocation> byGoal, Map<Integer, BigDecimal> byYear) {
    }

}
