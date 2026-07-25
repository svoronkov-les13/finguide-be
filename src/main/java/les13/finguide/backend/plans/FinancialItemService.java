package les13.finguide.backend.plans;

import les13.finguide.backend.auth.PlanAccessService;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import les13.finguide.backend.analytics.YearRatePoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class FinancialItemService {
    private static final Logger auditLog = LoggerFactory.getLogger("les13.finguide.audit");

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String DEFAULT_CURRENCY = "RUB";
    private static final int MIN_TARGET_YEAR = 2024;

    private final PlanStateRepository repository;
    private final PlanAccessService accessService;

    public FinancialItemService(PlanStateRepository repository, PlanAccessService accessService) {
        this.repository = repository;
        this.accessService = accessService;
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
        requireWritablePlan(planId);
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
                validatedGrowthSchedule(request.growthSchedule()),
                required(request.startDate(), "startDate"),
                request.endDate(),
                now,
                now
        );
        validateDateRange(income.startDate(), income.endDate());
        IncomeSource created = repository.createIncome(income);
        auditLog.info("financial_item_created type=income planId={} itemId={}", planId, created.id());
        return created;
    }

    @Transactional
    public IncomeSource updateIncome(UUID planId, UUID incomeId, FinancialItemRequests.IncomeRequest request) {
        requireWritablePlan(planId);
        IncomeSource current = repository.findIncome(planId, incomeId)
                .orElseThrow(() -> notFound("Income was not found"));
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
                request.growthSchedule() == null ? current.growthSchedule() : validatedGrowthSchedule(request.growthSchedule()),
                valueOrCurrent(request.startDate(), current.startDate()),
                request.endDate() == null ? current.endDate() : request.endDate(),
                current.createdAt(),
                now
        );
        validateDateRange(next.startDate(), next.endDate());
        IncomeSource updated = repository.updateIncome(next);
        auditLog.info("financial_item_updated type=income planId={} itemId={}", planId, updated.id());
        return updated;
    }

    @Transactional
    public void deleteIncome(UUID planId, UUID incomeId) {
        requireWritablePlan(planId);
        if (!repository.deleteIncome(planId, incomeId)) {
            throw notFound("Income was not found");
        }
        auditLog.info("financial_item_deleted type=income planId={} itemId={}", planId, incomeId);
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
        requireWritablePlan(planId);
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
                validatedGrowthSchedule(request.growthSchedule()),
                request.growthLabel(),
                enumValueOrDefault(ExpenseItem.BudgetClass.class, request.budgetClass(), ExpenseItem.BudgetClass.NEEDS, "budgetClass"),
                required(request.startDate(), "startDate"),
                request.endDate(),
                now,
                now
        );
        validateDateRange(expense.startDate(), expense.endDate());
        ExpenseItem created = repository.createExpense(expense);
        auditLog.info("financial_item_created type=expense planId={} itemId={}", planId, created.id());
        return created;
    }

    @Transactional
    public ExpenseItem updateExpense(UUID planId, UUID expenseId, FinancialItemRequests.ExpenseRequest request) {
        requireWritablePlan(planId);
        ExpenseItem current = repository.findExpense(planId, expenseId)
                .orElseThrow(() -> notFound("Expense was not found"));
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
                request.growthSchedule() == null ? current.growthSchedule() : validatedGrowthSchedule(request.growthSchedule()),
                request.growthLabel() == null ? current.growthLabel() : request.growthLabel(),
                enumValueOrCurrent(ExpenseItem.BudgetClass.class, request.budgetClass(), current.budgetClass(), "budgetClass"),
                valueOrCurrent(request.startDate(), current.startDate()),
                request.endDate() == null ? current.endDate() : request.endDate(),
                current.createdAt(),
                now
        );
        validateDateRange(next.startDate(), next.endDate());
        ExpenseItem updated = repository.updateExpense(next);
        auditLog.info("financial_item_updated type=expense planId={} itemId={}", planId, updated.id());
        return updated;
    }

    @Transactional
    public void deleteExpense(UUID planId, UUID expenseId) {
        requireWritablePlan(planId);
        if (!repository.deleteExpense(planId, expenseId)) {
            throw notFound("Expense was not found");
        }
        auditLog.info("financial_item_deleted type=expense planId={} itemId={}", planId, expenseId);
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
        requireWritablePlan(planId);
        Instant now = Instant.now();
        int priority = request.priority() == null ? repository.findGoals(planId).size() + 1 : positiveInteger(request.priority(), "priority");
        Goal.GrowthType growthType = enumValue(Goal.GrowthType.class, request.growthType(), "growthType");
        BigDecimal growthPct = resolveGrowthPct(planId, growthType, request.growthPct());
        Goal goal = new Goal(
                UUID.randomUUID(),
                planId,
                requiredText(request.name(), "name"),
                request.icon(),
                nonNegative(required(request.currentCost(), "currentCost"), "currentCost"),
                nonNegative(defaultBigDecimal(request.savedAmount(), ZERO), "savedAmount"),
                currency(request.currency()),
                targetYear(required(request.targetYear(), "targetYear")),
                targetMonth(request.targetMonth()),
                enumValue(Goal.Type.class, request.type(), "type"),
                growthType,
                percent(growthPct, "growthPct"),
                request.indexLabel(),
                priority,
                now,
                now
        );
        Goal created = repository.createGoal(goal);
        auditLog.info("financial_item_created type=goal planId={} itemId={}", planId, created.id());
        return created;
    }

    @Transactional
    public Goal updateGoal(UUID planId, UUID goalId, FinancialItemRequests.GoalRequest request) {
        requireWritablePlan(planId);
        Goal current = repository.findGoal(planId, goalId)
                .orElseThrow(() -> notFound("Goal was not found"));
        Instant now = Instant.now();
        Goal next = new Goal(
                current.id(),
                current.planId(),
                textOrCurrent(request.name(), current.name(), "name"),
                request.icon() == null ? current.icon() : request.icon(),
                nonNegative(valueOrCurrent(request.currentCost(), current.currentCost()), "currentCost"),
                nonNegative(valueOrCurrent(request.savedAmount(), current.savedAmount()), "savedAmount"),
                request.currency() == null ? current.currency() : currency(request.currency()),
                request.targetYear() == null ? current.targetYear() : targetYear(request.targetYear()),
                request.targetMonth() == null ? current.targetMonth() : targetMonth(request.targetMonth()),
                enumValueOrCurrent(Goal.Type.class, request.type(), current.type(), "type"),
                enumValueOrCurrent(Goal.GrowthType.class, request.growthType(), current.growthType(), "growthType"),
                percent(valueOrCurrent(request.growthPct(), current.growthPct()), "growthPct"),
                request.indexLabel() == null ? current.indexLabel() : request.indexLabel(),
                request.priority() == null ? current.priority() : positiveInteger(request.priority(), "priority"),
                current.createdAt(),
                now
        );
        Goal updated = repository.updateGoal(next);
        auditLog.info("financial_item_updated type=goal planId={} itemId={}", planId, updated.id());
        return updated;
    }

    @Transactional
    public void deleteGoal(UUID planId, UUID goalId) {
        requireWritablePlan(planId);
        if (!repository.deleteGoal(planId, goalId)) {
            throw notFound("Goal was not found");
        }
        auditLog.info("financial_item_deleted type=goal planId={} itemId={}", planId, goalId);
    }

    @Transactional
    public List<Goal> reorderGoals(UUID planId, FinancialItemRequests.GoalReorderRequest request) {
        requireWritablePlan(planId);
        List<UUID> goalIds = required(request.goalIds(), "goalIds");
        List<Goal> currentGoals = repository.findGoals(planId);
        Set<UUID> currentIds = currentGoals.stream().map(Goal::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> requestedIds = new HashSet<>(goalIds);
        if (goalIds.size() != currentGoals.size() || requestedIds.size() != goalIds.size() || !requestedIds.equals(currentIds)) {
            throw badRequest("goalIds must contain each current goal id exactly once");
        }
        List<Goal> reordered = repository.reorderGoals(planId, goalIds);
        auditLog.info("financial_items_reordered type=goal planId={} count={}", planId, reordered.size());
        return reordered;
    }

    private BigDecimal resolveGrowthPct(UUID planId, Goal.GrowthType growthType, BigDecimal requested) {
        if (requested != null || growthType != Goal.GrowthType.INFLATION) {
            return defaultBigDecimal(requested, ZERO);
        }
        int currentYear = Year.now().getValue();
        List<YearRatePoint> rates = repository.findInflationRates(planId);
        return rates.stream()
                .filter(p -> p.year() == currentYear)
                .map(YearRatePoint::ratePct)
                .findFirst()
                .or(() -> rates.stream().findFirst().map(YearRatePoint::ratePct))
                .orElse(ZERO);
    }

    private static List<YearRatePoint> validatedGrowthSchedule(List<YearRatePoint> schedule) {
        if (schedule == null) {
            return List.of();
        }
        schedule.forEach(point -> {
            if (point == null) {
                throw badRequest("growthSchedule contains an empty point");
            }
            if (point.year() < 1900 || point.year() > 2200) {
                throw badRequest("growthSchedule.year must be between 1900 and 2200");
            }
            percent(point.ratePct(), "growthSchedule.ratePct");
        });
        return List.copyOf(schedule);
    }

    private void requirePlan(UUID planId) {
        accessService.requirePlan(planId);
    }

    private void requireWritablePlan(UUID planId) {
        accessService.requireWritablePlan(planId);
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

    private static int targetYear(Integer value) {
        if (value == null || value < MIN_TARGET_YEAR) {
            throw badRequest("targetYear must be >= " + MIN_TARGET_YEAR);
        }
        return value;
    }

    private static int targetMonth(Integer value) {
        if (value == null) {
            return 12;
        }
        if (value < 1 || value > 12) {
            throw badRequest("targetMonth must be between 1 and 12");
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
