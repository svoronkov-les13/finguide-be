package les13.finguide.backend.api;

import les13.finguide.backend.analytics.BalanceSnapshot;
import les13.finguide.backend.analytics.CashFlowProjectionPoint;
import les13.finguide.backend.analytics.HealthScore;
import les13.finguide.backend.analytics.GoalAllocation;
import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.analytics.YearlyProjectionPoint;
import les13.finguide.backend.budget.BudgetEnvelope;
import les13.finguide.backend.budget.BudgetSettings;
import les13.finguide.backend.budget.MonthlyTrackerEntry;
import les13.finguide.backend.budget.OperationJournalEntry;
import les13.finguide.backend.contributions.Contribution;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.pension.PensionProjection;
import les13.finguide.backend.pension.PensionSpendDownPoint;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.plans.PlanStateRepository;
import les13.finguide.backend.plans.PlanState;
import les13.finguide.backend.scenarios.Scenario;
import les13.finguide.backend.scenarios.ScenarioComparison;
import les13.finguide.backend.scenarios.ScenarioService;
import les13.finguide.backend.users.UserProfile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class PlanApiMapper {
    public Map<String, Object> planState(PlanState state) {
        return planState(state, Map.of());
    }

    public Map<String, Object> planState(PlanState state, Map<UUID, GoalAllocation> goalAllocations) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(state.plan().id()));
        result.put("profile", profile(state.profile()));
        result.put("pension", pension(state.pension()));
        result.put("incomes", state.incomes().stream().map(this::income).toList());
        result.put("expenses", state.expenses().stream().map(this::expense).toList());
        result.put("goals", state.goals().stream().map(goal -> goal(goal, goalAllocations.get(goal.id()))).toList());
        result.put("contributions", state.contributions().stream().map(this::contribution).toList());
        result.put("budget", budget(state.budget()));
        result.put("modelAssumptions", assumptions(state.modelAssumptions()));
        result.put("updatedAt", state.updatedAt());
        return result;
    }

    public Map<String, Object> planSummary(PlanStateRepository.PlanSummary summary) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(summary.id()));
        result.put("name", summary.name());
        result.put("current", summary.current());
        result.put("createdAt", summary.createdAt());
        result.put("updatedAt", summary.updatedAt());
        return result;
    }

    public Map<String, Object> profile(UserProfile profile) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(profile.id()));
        result.put("name", profile.name());
        result.put("email", profile.email());
        result.put("phone", profile.phone());
        result.put("avatarUrl", profile.avatarUrl());
        result.put("age", profile.age());
        result.put("gender", apiValue(profile.gender()));
        result.put("initialBalance", profile.initialBalance());
        result.put("createdAt", profile.createdAt());
        result.put("updatedAt", profile.updatedAt());
        return result;
    }

    public Map<String, Object> pension(PensionSettings pension) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentAge", pension.currentAge());
        result.put("retirementAge", pension.retirementAge());
        result.put("monthlyExpenses", pension.monthlyExpenses());
        result.put("desiredMonthlyExpensesCurrentPrices", pension.desiredMonthlyExpensesCurrentPrices());
        result.put("currency", pension.currency());
        result.put("expectedReturnPct", pension.expectedReturnPct());
        result.put("inflationPct", pension.inflationPct());
        result.put("withdrawalStrategy", apiValue(pension.withdrawalStrategy()));
        result.put("statePensionEnabled", pension.statePensionEnabled());
        result.put("statePensionMonthly", pension.statePensionMonthly());
        return result;
    }

    public Map<String, Object> assumptions(ModelAssumptions assumptions) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startYear", assumptions.startYear());
        result.put("projectionEndYear", assumptions.projectionEndYear());
        result.put("horizonYears", assumptions.horizonYears());
        result.put("birthYear", assumptions.birthYear());
        result.put("monthsPerYear", assumptions.monthsPerYear());
        result.put("currency", assumptions.currency());
        result.put("initialCapital", assumptions.initialCapital());
        result.put("investmentReturnPct", assumptions.investmentReturnPct());
        result.put("inflationSchedule", assumptions.inflationSchedule().stream()
                .map(point -> Map.<String, Object>of("year", point.year(), "ratePct", point.ratePct()))
                .toList());
        result.put("sourceModel", assumptions.sourceModel());
        return result;
    }

    public Map<String, Object> income(IncomeSource income) {
        Map<String, Object> result = baseCashItem(income.id(), income.name(), income.amount(), income.currency(), income.frequency(), income.growthType(), income.growthPct(), income.startDate(), income.endDate(), income.createdAt(), income.updatedAt());
        return result;
    }

    public Map<String, Object> expense(ExpenseItem expense) {
        Map<String, Object> result = baseCashItem(expense.id(), expense.name(), expense.amount(), expense.currency(), expense.frequency(), expense.growthType(), expense.growthPct(), expense.startDate(), expense.endDate(), expense.createdAt(), expense.updatedAt());
        result.put("growthLabel", expense.growthLabel());
        result.put("budgetClass", apiValue(expense.budgetClass()));
        return result;
    }

    public Map<String, Object> goal(Goal goal) {
        return goal(goal, null);
    }

    public Map<String, Object> goal(Goal goal, GoalAllocation allocation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(goal.id()));
        result.put("name", goal.name());
        result.put("icon", goal.icon());
        result.put("currentCost", goal.currentCost());
        result.put("savedAmount", goal.savedAmount());
        result.put("currency", goal.currency());
        result.put("targetYear", goal.targetYear());
        result.put("targetMonth", goal.targetMonth());
        result.put("type", apiValue(goal.type()));
        result.put("growthType", apiValue(goal.growthType()));
        result.put("growthPct", goal.growthPct());
        result.put("indexLabel", goal.indexLabel());
        result.put("priority", goal.priority());
        if (allocation != null) {
            result.put("projectedTargetCost", allocation.targetCost());
            result.put("projectedSavedAmount", allocation.savedAmount());
            result.put("projectedProgressPct", allocation.progressPct());
            result.put("projectedReachable", allocation.reachable());
            result.put("projectedCompletionYear", allocation.completionYear());
        }
        result.put("createdAt", goal.createdAt());
        result.put("updatedAt", goal.updatedAt());
        return result;
    }

    public Map<String, Object> contribution(Contribution contribution) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(contribution.id()));
        result.put("goalId", string(contribution.goalId()));
        result.put("amount", contribution.amount());
        result.put("currency", contribution.currency());
        result.put("date", contribution.date());
        result.put("note", contribution.note());
        result.put("createdAt", contribution.createdAt());
        result.put("updatedAt", contribution.updatedAt());
        return result;
    }

    public Map<String, Object> budget(BudgetSettings budget) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("method", budget.method() == BudgetSettings.Method.RULE_50_30_20 ? "503020" : apiValue(budget.method()));
        result.put("envelopes", budget.envelopes().stream().map(this::budgetEnvelope).toList());
        Map<String, Object> classifications = new LinkedHashMap<>();
        budget.classifications().forEach((id, value) -> classifications.put(string(id), apiValue(value)));
        result.put("classifications", classifications);
        return result;
    }

    public Map<String, Object> budgetEnvelope(BudgetEnvelope envelope) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(envelope.id()));
        result.put("name", envelope.name());
        result.put("limit", envelope.limit());
        result.put("icon", envelope.icon());
        result.put("color", envelope.color());
        result.put("spent", envelope.spent());
        result.put("remaining", envelope.remaining());
        result.put("pct", envelope.pct());
        result.put("isOver", envelope.overLimit());
        return result;
    }

    public Map<String, Object> monthlyTrackerEntry(MonthlyTrackerEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("month", entry.month().toString());
        result.put("status", apiValue(entry.status()));
        result.put("amount", entry.amount());
        result.put("note", entry.note());
        result.put("updatedAt", entry.updatedAt());
        return result;
    }

    public Map<String, Object> operationJournalEntry(OperationJournalEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(entry.id()));
        result.put("date", entry.date());
        result.put("title", entry.title());
        result.put("amount", entry.amount());
        result.put("type", apiValue(entry.type()));
        result.put("status", apiValue(entry.status()));
        result.put("createdAt", entry.createdAt());
        result.put("updatedAt", entry.updatedAt());
        return result;
    }

    public Map<String, Object> scenario(Scenario scenario) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", ScenarioService.stableScenarioId(scenario));
        result.put("basePlanId", string(scenario.basePlanId()));
        result.put("name", scenario.name());
        result.put("emoji", scenario.emoji());
        result.put("description", scenario.description());
        result.put("isBase", scenario.base());
        result.put("base", scenario.base());
        result.put("adjustments", scenarioAdjustments(scenario.adjustments()));
        result.put("createdAt", scenario.createdAt());
        result.put("updatedAt", scenario.updatedAt());
        return result;
    }

    public Map<String, Object> scenarioAdjustments(Scenario.Adjustments adjustments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("incomeAdjPct", adjustments.incomeAdjPct());
        result.put("expenseAdjPct", adjustments.expenseAdjPct());
        result.put("returnAdjPct", adjustments.returnAdjPct());
        result.put("inflationAdjPct", adjustments.inflationAdjPct());
        result.put("retirementAgeShift", adjustments.retirementAgeShift());
        result.put("goalsCostAdjPct", adjustments.goalsCostAdjPct());
        return result;
    }

    public Map<String, Object> scenarioComparison(ScenarioComparison comparison) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarios", comparison.scenarios().stream().map(this::scenarioComparisonResult).toList());
        return result;
    }

    private Map<String, Object> scenarioComparisonResult(ScenarioComparison.Result comparison) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenarioId", comparison.scenarioId());
        result.put("name", comparison.name());
        result.put("finalCapital", comparison.finalCapital());
        result.put("minCapital", comparison.minCapital());
        result.put("retirementYear", comparison.retirementYear());
        result.put("capitalAtRetirement", comparison.capitalAtRetirement());
        result.put("goalCoveragePct", comparison.goalCoveragePct());
        result.put("projection", comparison.projection().stream().map(this::cashflow).toList());
        return result;
    }

    public Map<String, Object> dashboard(Map<String, Object> dashboard) {
        return dashboard;
    }

    public Map<String, Object> yearlyProjection(YearlyProjectionPoint point) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", point.year());
        result.put("income", point.income());
        result.put("expenses", point.expenses());
        result.put("goalsCost", point.goalsCost());
        result.put("netSavings", point.netSavings());
        return result;
    }

    public Map<String, Object> balance(BalanceSnapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", snapshot.year());
        result.put("monthlyIncome", snapshot.monthlyIncome());
        result.put("yearlyIncome", snapshot.yearlyIncome());
        result.put("totalIncome", snapshot.totalIncome());
        result.put("monthlyExpenses", snapshot.monthlyExpenses());
        result.put("yearlyExpenses", snapshot.yearlyExpenses());
        result.put("monthlyGoalExpenses", snapshot.monthlyGoalExpenses());
        result.put("yearlyGoalExpenses", snapshot.yearlyGoalExpenses());
        result.put("goalExpenses", snapshot.goalExpenses());
        result.put("totalOutflow", snapshot.totalOutflow());
        result.put("netSavings", snapshot.netSavings());
        return result;
    }

    public Map<String, Object> pensionProjection(PensionProjection projection) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentAge", projection.currentAge());
        result.put("retirementAge", projection.retirementAge());
        result.put("retirementYear", projection.retirementYear());
        result.put("capitalAtRetirement", projection.capitalAtRetirement());
        result.put("nominalReturnPct", projection.nominalReturnPct());
        result.put("averageInflationPct", projection.averageInflationPct());
        result.put("realReturnPct", projection.realReturnPct());
        result.put("preserveCapital", Map.of(
                "annualSpendableAtRetirement", projection.preserveCapital().annualSpendableAtRetirement(),
                "annualSpendableCurrentPrices", projection.preserveCapital().annualSpendableCurrentPrices(),
                "monthlySpendableCurrentPrices", projection.preserveCapital().monthlySpendableCurrentPrices()
        ));
        result.put("spendDown", Map.of(
                "desiredMonthlyExpensesCurrentPrices", projection.spendDown().desiredMonthlyExpensesCurrentPrices(),
                "desiredAnnualExpensesAtRetirement", projection.spendDown().desiredAnnualExpensesAtRetirement(),
                "retirementYears", projection.spendDown().retirementYears(),
                "depletionAge", projection.spendDown().depletionAge(),
                "series", projection.spendDown().series().stream().map(this::pensionSpendDown).toList()
        ));
        return result;
    }

    private Map<String, Object> pensionSpendDown(PensionSpendDownPoint point) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", point.year());
        result.put("age", point.age());
        result.put("beginningCapital", point.beginningCapital());
        result.put("plannedExpense", point.plannedExpense());
        result.put("nominalReturnPct", point.nominalReturnPct());
        result.put("endingCapital", point.endingCapital());
        return result;
    }

    public Map<String, Object> cashflow(CashFlowProjectionPoint point) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", point.year());
        result.put("age", point.age());
        result.put("periodNo", point.periodNo());
        result.put("monthlyIncome", point.monthlyIncome());
        result.put("yearlyIncome", point.yearlyIncome());
        result.put("totalIncome", point.totalIncome());
        result.put("monthlyExpenses", point.monthlyExpenses());
        result.put("yearlyExpenses", point.yearlyExpenses());
        result.put("totalExpenses", point.totalExpenses());
        result.put("monthlyGoalExpenses", point.monthlyGoalExpenses());
        result.put("yearlyGoalExpenses", point.yearlyGoalExpenses());
        result.put("totalGoalExpenses", point.totalGoalExpenses());
        result.put("netSavings", point.netSavings());
        result.put("investmentReturnPct", point.investmentReturnPct());
        result.put("capitalStartOfYear", point.capitalStartOfYear());
        result.put("capitalEndOfYear", point.capitalEndOfYear());
        return result;
    }

    public Map<String, Object> health(HealthScore score) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score.score());
        result.put("items", score.items().stream().map(item -> {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("key", item.key());
            mapped.put("label", item.label());
            mapped.put("value", item.value());
            mapped.put("status", apiValue(item.status()));
            mapped.put("hint", item.hint());
            return mapped;
        }).toList());
        return result;
    }

    private Map<String, Object> baseCashItem(UUID id, String name, Object amount, String currency, Enum<?> frequency, Enum<?> growthType, Object growthPct, LocalDate startDate, LocalDate endDate, Instant createdAt, Instant updatedAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(id));
        result.put("name", name);
        result.put("amount", amount);
        result.put("currency", currency);
        result.put("frequency", apiValue(frequency));
        result.put("growthType", apiValue(growthType));
        result.put("growthPct", growthPct);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        result.put("createdAt", createdAt);
        result.put("updatedAt", updatedAt);
        return result;
    }

    private static String apiValue(Enum<?> value) {
        if (value == null) {
            return null;
        }
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static String string(UUID value) {
        return value == null ? null : value.toString();
    }
}
