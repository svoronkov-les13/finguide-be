package les13.finguide.backend.plans;

import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FinancialItemService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEFAULT_CURRENCY = "RUB";

    private final PlanStateRepository repository;

    public FinancialItemService(PlanStateRepository repository) {
        this.repository = repository;
    }

    public List<IncomeSource> incomes(UUID planId) {
        requirePlan(planId);
        return repository.findIncomes(planId);
    }

    public IncomeSource income(UUID planId, UUID incomeId) {
        requirePlan(planId);
        return repository.findIncome(planId, incomeId)
                .orElseThrow(() -> notFound("Income was not found"));
    }

    @Transactional
    public IncomeSource createIncome(UUID planId, FinancialItemRequests.IncomeRequest request) {
        requirePlan(planId);
        Instant now = Instant.now();
        IncomeSource income = new IncomeSource(
                UUID.randomUUID(),
                planId,
                requiredText(request.name(), "name"),
                nonNegative(required(request.amount(), "amount"), "amount"),
                currency(request.currency()),
                enumValue(IncomeSource.Frequency.class, request.frequency(), "frequency"),
                enumValue(IncomeSource.GrowthType.class, request.growthType(), "growthType"),
                percent(defaultBigDecimal(request.growthPct(), ZERO), "growthPct"),
                required(request.startDate(), "startDate"),
                request.endDate(),
                now,
                now
        );
        validateDateRange(income.startDate(), income.endDate());
        return repository.createIncome(income);
    }

    @Transactional
    public IncomeSource updateIncome(UUID planId, UUID incomeId, FinancialItemRequests.IncomeRequest request) {
        IncomeSource current = income(planId, incomeId);
        Instant now = Instant.now();
        IncomeSource next = new IncomeSource(
                current.id(),
                current.planId(),
                textOrCurrent(request.name(), current.name(), "name"),
                nonNegative(valueOrCurrent(request.amount(), current.amount()), "amount"),
                request.currency() == null ? current.currency() : currency(request.currency()),
                enumValueOrCurrent(IncomeSource.Frequency.class, request.frequency(), current.frequency(), "frequency"),
                enumValueOrCurrent(IncomeSource.GrowthType.class, request.growthType(), current.growthType(), "growthType"),
                percent(valueOrCurrent(request.growthPct(), current.growthPct()), "growthPct"),
                valueOrCurrent(request.startDate(), current.startDate()),
                request.endDate() == null ? current.endDate() : request.endDate(),
                current.createdAt(),
                now
        );
        validateDateRange(next.startDate(), next.endDate());
        return repository.updateIncome(next);
    }

    @Transactional
    public void deleteIncome(UUID planId, UUID incomeId) {
        requirePlan(planId);
        if (!repository.deleteIncome(planId, incomeId)) {
            throw notFound("Income was not found");
        }
    }

    public List<ExpenseItem> expenses(UUID planId) {
        requirePlan(planId);
        return repository.findExpenses(planId);
    }

    public ExpenseItem expense(UUID planId, UUID expenseId) {
        requirePlan(planId);
        return repository.findExpense(planId, expenseId)
                .orElseThrow(() -> notFound("Expense was not found"));
    }

    @Transactional
    public ExpenseItem createExpense(UUID planId, FinancialItemRequests.ExpenseRequest request) {
        requirePlan(planId);
        Instant now = Instant.now();
        ExpenseItem expense = new ExpenseItem(
                UUID.randomUUID(),
                planId,
                requiredText(request.name(), "name"),
                nonNegative(required(request.amount(), "amount"), "amount"),
                currency(request.currency()),
                enumValue(ExpenseItem.Frequency.class, request.frequency(), "frequency"),
                enumValue(ExpenseItem.GrowthType.class, request.growthType(), "growthType"),
                percent(defaultBigDecimal(request.growthPct(), ZERO), "growthPct"),
                request.growthLabel(),
                enumValueOrDefault(ExpenseItem.BudgetClass.class, request.budgetClass(), ExpenseItem.BudgetClass.NEEDS, "budgetClass"),
                required(request.startDate(), "startDate"),
                request.endDate(),
                now,
                now
        );
        validateDateRange(expense.startDate(), expense.endDate());
        return repository.createExpense(expense);
    }

    @Transactional
    public ExpenseItem updateExpense(UUID planId, UUID expenseId, FinancialItemRequests.ExpenseRequest request) {
        ExpenseItem current = expense(planId, expenseId);
        Instant now = Instant.now();
        ExpenseItem next = new ExpenseItem(
                current.id(),
                current.planId(),
                textOrCurrent(request.name(), current.name(), "name"),
                nonNegative(valueOrCurrent(request.amount(), current.amount()), "amount"),
                request.currency() == null ? current.currency() : currency(request.currency()),
                enumValueOrCurrent(ExpenseItem.Frequency.class, request.frequency(), current.frequency(), "frequency"),
                enumValueOrCurrent(ExpenseItem.GrowthType.class, request.growthType(), current.growthType(), "growthType"),
                percent(valueOrCurrent(request.growthPct(), current.growthPct()), "growthPct"),
                request.growthLabel() == null ? current.growthLabel() : request.growthLabel(),
                enumValueOrCurrent(ExpenseItem.BudgetClass.class, request.budgetClass(), current.budgetClass(), "budgetClass"),
                valueOrCurrent(request.startDate(), current.startDate()),
                request.endDate() == null ? current.endDate() : request.endDate(),
                current.createdAt(),
                now
        );
        validateDateRange(next.startDate(), next.endDate());
        return repository.updateExpense(next);
    }

    @Transactional
    public void deleteExpense(UUID planId, UUID expenseId) {
        requirePlan(planId);
        if (!repository.deleteExpense(planId, expenseId)) {
            throw notFound("Expense was not found");
        }
    }

    public List<Goal> goals(UUID planId) {
        requirePlan(planId);
        return repository.findGoals(planId);
    }

    public Goal goal(UUID planId, UUID goalId) {
        requirePlan(planId);
        return repository.findGoal(planId, goalId)
                .orElseThrow(() -> notFound("Goal was not found"));
    }

    @Transactional
    public Goal createGoal(UUID planId, FinancialItemRequests.GoalRequest request) {
        requirePlan(planId);
        Instant now = Instant.now();
        int priority = request.priority() == null ? repository.findGoals(planId).size() + 1 : positiveInteger(request.priority(), "priority");
        Goal goal = new Goal(
                UUID.randomUUID(),
                planId,
                requiredText(request.name(), "name"),
                request.icon(),
                nonNegative(required(request.currentCost(), "currentCost"), "currentCost"),
                nonNegative(defaultBigDecimal(request.savedAmount(), ZERO), "savedAmount"),
                currency(request.currency()),
                positiveInteger(required(request.targetYear(), "targetYear"), "targetYear"),
                enumValue(Goal.Type.class, request.type(), "type"),
                enumValue(Goal.GrowthType.class, request.growthType(), "growthType"),
                percent(defaultBigDecimal(request.growthPct(), ZERO), "growthPct"),
                request.indexLabel(),
                priority,
                now,
                now
        );
        return repository.createGoal(goal);
    }

    @Transactional
    public Goal updateGoal(UUID planId, UUID goalId, FinancialItemRequests.GoalRequest request) {
        Goal current = goal(planId, goalId);
        Instant now = Instant.now();
        Goal next = new Goal(
                current.id(),
                current.planId(),
                textOrCurrent(request.name(), current.name(), "name"),
                request.icon() == null ? current.icon() : request.icon(),
                nonNegative(valueOrCurrent(request.currentCost(), current.currentCost()), "currentCost"),
                nonNegative(valueOrCurrent(request.savedAmount(), current.savedAmount()), "savedAmount"),
                request.currency() == null ? current.currency() : currency(request.currency()),
                positiveInteger(valueOrCurrent(request.targetYear(), current.targetYear()), "targetYear"),
                enumValueOrCurrent(Goal.Type.class, request.type(), current.type(), "type"),
                enumValueOrCurrent(Goal.GrowthType.class, request.growthType(), current.growthType(), "growthType"),
                percent(valueOrCurrent(request.growthPct(), current.growthPct()), "growthPct"),
                request.indexLabel() == null ? current.indexLabel() : request.indexLabel(),
                request.priority() == null ? current.priority() : positiveInteger(request.priority(), "priority"),
                current.createdAt(),
                now
        );
        return repository.updateGoal(next);
    }

    @Transactional
    public void deleteGoal(UUID planId, UUID goalId) {
        requirePlan(planId);
        if (!repository.deleteGoal(planId, goalId)) {
            throw notFound("Goal was not found");
        }
    }

    @Transactional
    public List<Goal> reorderGoals(UUID planId, FinancialItemRequests.GoalReorderRequest request) {
        requirePlan(planId);
        List<UUID> goalIds = required(request.goalIds(), "goalIds");
        List<Goal> currentGoals = repository.findGoals(planId);
        Set<UUID> currentIds = currentGoals.stream().map(Goal::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(goalIds);
        if (goalIds.size() != currentGoals.size() || requestedIds.size() != goalIds.size() || !requestedIds.equals(currentIds)) {
            throw badRequest("goalIds must contain each current goal id exactly once");
        }
        return repository.reorderGoals(planId, goalIds);
    }

    private void requirePlan(UUID planId) {
        repository.findById(planId).orElseThrow(() -> notFound("Plan was not found"));
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw badRequest(field + " is required");
        }
        return value.trim();
    }

    private static String textOrCurrent(String value, String current, String field) {
        return value == null ? current : requiredText(value, field);
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw badRequest(field + " is required");
        }
        return value;
    }

    private static BigDecimal defaultBigDecimal(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static <T> T valueOrCurrent(T value, T current) {
        return value == null ? current : value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value.compareTo(ZERO) < 0) {
            throw badRequest(field + " must be positive or zero");
        }
        return value;
    }

    private static BigDecimal percent(BigDecimal value, String field) {
        nonNegative(value, field);
        if (value.compareTo(HUNDRED) > 0) {
            throw badRequest(field + " must be <= 100");
        }
        return value;
    }

    private static int positiveInteger(Integer value, String field) {
        if (value == null || value < 1) {
            throw badRequest(field + " must be >= 1");
        }
        return value;
    }

    private static String currency(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_CURRENCY : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw badRequest("currency must be ISO-4217 alpha-3 code");
        }
        return normalized;
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate != null && endDate.isBefore(startDate)) {
            throw badRequest("endDate must be greater than or equal to startDate");
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
        return enumValueOrDefault(type, value, null, field);
    }

    private static <T extends Enum<T>> T enumValueOrCurrent(Class<T> type, String value, T current, String field) {
        return value == null ? current : enumValue(type, value, field);
    }

    private static <T extends Enum<T>> T enumValueOrDefault(Class<T> type, String value, T fallback, String field) {
        if ((value == null || value.isBlank()) && fallback != null) {
            return fallback;
        }
        if (value == null || value.isBlank()) {
            throw badRequest(field + " is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException ignored) {
            throw badRequest(field + " has unsupported value: " + value);
        }
    }
}
