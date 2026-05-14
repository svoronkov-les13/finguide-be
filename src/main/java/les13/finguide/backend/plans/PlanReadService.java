package les13.finguide.backend.plans;

import les13.finguide.backend.analytics.BalanceSnapshot;
import les13.finguide.backend.analytics.CashFlowProjectionPoint;
import les13.finguide.backend.analytics.HealthScore;
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
import java.time.Year;
import java.util.ArrayList;
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

    public PlanState plan(UUID planId) {
        return accessService.requirePlan(planId);
    }

    public Map<String, Object> dashboard(UUID planId) {
        PlanState state = plan(planId);
        BigDecimal monthlyIncome = monthlyIncome(state);
        BigDecimal yearlyIncome = yearlyIncome(state);
        BigDecimal monthlyExpenses = monthlyExpenses(state);
        BigDecimal yearlyExpenses = yearlyExpenses(state);
        BigDecimal netMonthly = monthlyIncome.subtract(monthlyExpenses);
        BigDecimal netYearly = yearlyIncome.subtract(yearlyExpenses);
        BigDecimal totalGoalsCost = state.goals().stream().map(Goal::currentCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGoalsSaved = state.goals().stream().map(Goal::savedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGoalsRemaining = totalGoalsCost.subtract(totalGoalsSaved).max(BigDecimal.ZERO);
        BigDecimal savingsRate = yearlyIncome.signum() == 0
                ? BigDecimal.ZERO
                : netYearly.multiply(HUNDRED).divide(yearlyIncome, 1, RoundingMode.HALF_UP);
        int currentYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        int latestGoalYear = state.goals().stream().mapToInt(Goal::targetYear).max().orElse(currentYear + 1);
        int monthsToGoal = Math.max(1, (latestGoalYear - currentYear + 1) * state.modelAssumptions().monthsPerYear());
        BigDecimal monthlyGoalContribution = totalGoalsRemaining.divide(BigDecimal.valueOf(monthsToGoal), 0, RoundingMode.HALF_UP);
        BigDecimal availableForPension = netMonthly.subtract(monthlyGoalContribution).max(BigDecimal.ZERO);
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
        List<Map<String, Object>> yearlyProjection = cashflow(planId).stream()
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
        return cashflow(plan(planId), 12);
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
        BigDecimal yearlyGoalExpenses = goalsForYear(state, year);
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
        return cashflow(plan(planId), years).stream()
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

        int retirementYears = 30;
        BigDecimal desiredAnnualAtRetirement = inflate(pension.desiredMonthlyExpensesCurrentPrices().multiply(TWELVE), averageInflationPct, yearsToRetirement);
        List<PensionSpendDownPoint> series = spendDownSeries(retirementYear, pension.retirementAge(), capitalAtRetirement, desiredAnnualAtRetirement, nominalReturnPct, averageInflationPct, retirementYears);
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

    public List<Map<String, Object>> scenarios(UUID basePlanId) {
        PlanState state = plan(basePlanId);
        return List.of(
                scenario("base", "Основной план", "📊", "Текущий финансовый план", true, state.plan().id(), Map.of("incomeAdjPct", 0, "expenseAdjPct", 0, "returnAdjPct", 0, "inflationAdjPct", 0, "retirementAgeShift", 0, "goalsCostAdjPct", 0), state.updatedAt()),
                scenario("optimistic", "Оптимистичный", "🚀", "Доходы растут быстрее, расходы медленнее", false, state.plan().id(), Map.of("incomeAdjPct", 15, "expenseAdjPct", 5, "returnAdjPct", 1, "inflationAdjPct", -1, "retirementAgeShift", -2, "goalsCostAdjPct", 0), state.updatedAt()),
                scenario("pessimistic", "Пессимистичный", "⚡", "Стресс-тест: снижение доходов и рост расходов", false, state.plan().id(), Map.of("incomeAdjPct", -10, "expenseAdjPct", 12, "returnAdjPct", -2, "inflationAdjPct", 2, "retirementAgeShift", 3, "goalsCostAdjPct", 15), state.updatedAt())
        );
    }

    private Map<String, Object> scenario(String id, String name, String emoji, String description, boolean isBase, UUID basePlanId, Map<String, Object> adjustments, java.time.Instant updatedAt) {
        return Map.of(
                "id", id,
                "name", name,
                "emoji", emoji,
                "description", description,
                "isBase", isBase,
                "basePlanId", basePlanId.toString(),
                "adjustments", adjustments,
                "createdAt", updatedAt,
                "updatedAt", updatedAt
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
        return cashflow(state, yearsFromStart).get(yearsFromStart - 1).capitalEndOfYear();
    }

    private static List<CashFlowProjectionPoint> cashflow(PlanState state, int horizon) {
        int startYear = Math.max(Year.now().getValue(), state.modelAssumptions().startYear());
        BigDecimal capital = state.modelAssumptions().initialCapital();
        List<CashFlowProjectionPoint> result = new ArrayList<>();
        for (int offset = 0; offset < horizon; offset++) {
            int year = startYear + offset;
            BigDecimal monthlyIncome = grow(monthlyIncome(state), averageIncomeGrowth(state), offset);
            BigDecimal yearlyIncome = grow(yearlyOneTimeIncome(state), averageIncomeGrowth(state), offset);
            BigDecimal totalIncome = monthlyIncome.multiply(TWELVE).add(yearlyIncome);
            BigDecimal monthlyExpenses = grow(monthlyExpenses(state), averageExpenseGrowth(state), offset);
            BigDecimal yearlyExpenses = grow(yearlyOneTimeExpenses(state), averageExpenseGrowth(state), offset);
            BigDecimal totalExpenses = monthlyExpenses.multiply(TWELVE).add(yearlyExpenses);
            BigDecimal yearlyGoalExpenses = goalsForYear(state, year);
            BigDecimal netSavings = totalIncome.subtract(totalExpenses).subtract(yearlyGoalExpenses);
            BigDecimal capitalStart = capital;
            BigDecimal investmentReturn = state.modelAssumptions().investmentReturnPct();
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

    private static BigDecimal averageInflation(PlanState state) {
        List<BigDecimal> values = state.modelAssumptions().inflationSchedule().stream().map(point -> point.ratePct()).toList();
        return values.isEmpty() ? state.pension().inflationPct() : average(values).setScale(2, RoundingMode.HALF_UP);
    }

    private static List<PensionSpendDownPoint> spendDownSeries(int startYear, int startAge, BigDecimal initialCapital, BigDecimal initialExpense, BigDecimal returnPct, BigDecimal inflationPct, int years) {
        List<PensionSpendDownPoint> result = new ArrayList<>();
        BigDecimal capital = initialCapital;
        for (int offset = 0; offset < years; offset++) {
            BigDecimal expense = inflate(initialExpense, inflationPct, offset);
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
        BigDecimal multiplier = BigDecimal.ONE.add(inflationPct.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        BigDecimal denominator = BigDecimal.ONE;
        for (int i = 0; i < years; i++) {
            denominator = denominator.multiply(multiplier);
        }
        return value.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal compound(BigDecimal value, BigDecimal ratePct, int years) {
        BigDecimal result = value;
        BigDecimal multiplier = BigDecimal.ONE.add(ratePct.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        for (int i = 0; i < years; i++) {
            result = result.multiply(multiplier);
        }
        return result.setScale(2, RoundingMode.HALF_UP);
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
        return state.incomes().stream()
                .filter(item -> item.frequency() == IncomeSource.Frequency.MONTHLY)
                .map(IncomeSource::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal yearlyIncome(PlanState state) {
        return monthlyIncome(state).multiply(TWELVE).add(yearlyOneTimeIncome(state));
    }

    private static BigDecimal yearlyOneTimeIncome(PlanState state) {
        return state.incomes().stream()
                .filter(item -> item.frequency() == IncomeSource.Frequency.YEARLY || item.frequency() == IncomeSource.Frequency.ONE_TIME)
                .map(IncomeSource::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal monthlyExpenses(PlanState state) {
        return state.expenses().stream()
                .filter(item -> item.frequency() == ExpenseItem.Frequency.MONTHLY)
                .map(ExpenseItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal yearlyExpenses(PlanState state) {
        return monthlyExpenses(state).multiply(TWELVE).add(yearlyOneTimeExpenses(state));
    }

    private static BigDecimal yearlyOneTimeExpenses(PlanState state) {
        return state.expenses().stream()
                .filter(item -> item.frequency() == ExpenseItem.Frequency.YEARLY || item.frequency() == ExpenseItem.Frequency.ONE_TIME)
                .map(ExpenseItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal averageIncomeGrowth(PlanState state) {
        return average(state.incomes().stream().map(IncomeSource::growthPct).toList());
    }

    private static BigDecimal averageExpenseGrowth(PlanState state) {
        return average(state.expenses().stream().map(ExpenseItem::growthPct).toList());
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal grow(BigDecimal value, BigDecimal growthPct, int years) {
        BigDecimal result = value;
        BigDecimal multiplier = BigDecimal.ONE.add(growthPct.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        for (int i = 0; i < years; i++) {
            result = result.multiply(multiplier);
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal goalsForYear(PlanState state, int year) {
        return state.goals().stream()
                .filter(goal -> goal.targetYear() == year)
                .map(goal -> goal.currentCost().subtract(goal.savedAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
