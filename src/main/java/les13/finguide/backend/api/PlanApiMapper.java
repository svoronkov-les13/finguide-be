package les13.finguide.backend.api;

import les13.finguide.backend.analytics.CashFlowProjectionPoint;
import les13.finguide.backend.analytics.HealthScore;
import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.analytics.YearlyProjectionPoint;
import les13.finguide.backend.budget.BudgetEnvelope;
import les13.finguide.backend.budget.BudgetSettings;
import les13.finguide.backend.contributions.Contribution;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.plans.PlanState;
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(state.plan().id()));
        result.put("profile", profile(state.profile()));
        result.put("pension", pension(state.pension()));
        result.put("incomes", state.incomes().stream().map(this::income).toList());
        result.put("expenses", state.expenses().stream().map(this::expense).toList());
        result.put("goals", state.goals().stream().map(this::goal).toList());
        result.put("contributions", state.contributions().stream().map(this::contribution).toList());
        result.put("budget", budget(state.budget()));
        result.put("modelAssumptions", assumptions(state.modelAssumptions()));
        result.put("updatedAt", state.updatedAt());
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
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", string(goal.id()));
        result.put("name", goal.name());
        result.put("icon", goal.icon());
        result.put("currentCost", goal.currentCost());
        result.put("savedAmount", goal.savedAmount());
        result.put("currency", goal.currency());
        result.put("targetYear", goal.targetYear());
        result.put("type", apiValue(goal.type()));
        result.put("growthType", apiValue(goal.growthType()));
        result.put("growthPct", goal.growthPct());
        result.put("indexLabel", goal.indexLabel());
        result.put("priority", goal.priority());
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
