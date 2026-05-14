package les13.finguide.backend.plans;

import les13.finguide.backend.auth.PlanAccessService;
import les13.finguide.backend.budget.BudgetEnvelope;
import les13.finguide.backend.budget.BudgetSettings;
import les13.finguide.backend.budget.MonthlyTrackerEntry;
import les13.finguide.backend.expenses.ExpenseItem;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BudgetTrackerService {
    private final PlanStateRepository repository;
    private final PlanAccessService accessService;

    public BudgetTrackerService(PlanStateRepository repository, PlanAccessService accessService) {
        this.repository = repository;
        this.accessService = accessService;
    }

    public BudgetSettings budget(UUID planId) {
        accessService.requirePlan(planId);
        return repository.findBudget(planId);
    }

    @Transactional
    public BudgetSettings updateBudget(UUID planId, BudgetTrackerRequests.BudgetRequest request) {
        accessService.requireWritablePlan(planId);
        BudgetSettings.Method method = parseMethod(requiredText(request.method(), "method"));
        List<BudgetEnvelope> envelopes = new ArrayList<>();
        for (BudgetTrackerRequests.EnvelopeRequest envelope : request.envelopes() == null ? List.<BudgetTrackerRequests.EnvelopeRequest>of() : request.envelopes()) {
            BigDecimal limit = nonNegative(required(envelope.limit(), "limit"), "limit");
            envelopes.add(new BudgetEnvelope(
                    envelope.id() == null ? UUID.randomUUID() : envelope.id(),
                    planId,
                    requiredText(envelope.name(), "name"),
                    limit,
                    requiredText(envelope.icon(), "icon"),
                    requiredText(envelope.color(), "color"),
                    BigDecimal.ZERO,
                    limit,
                    BigDecimal.ZERO,
                    false
            ));
        }
        Map<UUID, BudgetSettings.BudgetClass> classifications = new LinkedHashMap<>();
        if (request.classifications() != null) {
            request.classifications().forEach((expenseId, value) -> {
                if (repository.findExpense(planId, expenseId).isEmpty()) {
                    throw badRequest("classification expenseId must belong to the plan");
                }
                classifications.put(expenseId, parseBudgetClass(value));
            });
        }
        return repository.replaceBudget(planId, new BudgetSettings(planId, method, envelopes, classifications));
    }

    @Transactional
    public BudgetSettings autogenerateEnvelopes(UUID planId) {
        accessService.requireWritablePlan(planId);
        List<BudgetEnvelope> envelopes = repository.findExpenses(planId).stream()
                .map(expense -> new BudgetEnvelope(
                        UUID.randomUUID(),
                        planId,
                        expense.name(),
                        nonNegative(expense.amount(), "amount"),
                        icon(expense.budgetClass()),
                        color(expense.budgetClass()),
                        BigDecimal.ZERO,
                        expense.amount(),
                        BigDecimal.ZERO,
                        false
                ))
                .toList();
        return repository.replaceBudgetEnvelopes(planId, envelopes);
    }

    public List<MonthlyTrackerEntry> monthlyTracker(UUID planId, Integer year) {
        accessService.requirePlan(planId);
        int requestedYear = year == null ? YearMonth.now(ZoneOffset.UTC).getYear() : year;
        if (requestedYear < 1900 || requestedYear > 3000) {
            throw badRequest("year is out of range");
        }
        return repository.findMonthlyTrackerEntries(planId, requestedYear);
    }

    @Transactional
    public void upsertMonthlyTracker(UUID planId, BudgetTrackerRequests.MonthlyTrackerRequest request) {
        accessService.requireWritablePlan(planId);
        YearMonth month = parseMonth(requiredText(request.month(), "month"));
        MonthlyTrackerEntry.Status status = parseStatus(requiredText(request.status(), "status"));
        Instant now = Instant.now();
        repository.upsertMonthlyTrackerEntry(planId, new MonthlyTrackerEntry(month, status, request.note(), now, now));
    }

    private static BudgetSettings.Method parseMethod(String value) {
        return switch (value) {
            case "503020" -> BudgetSettings.Method.RULE_50_30_20;
            case "envelope" -> BudgetSettings.Method.ENVELOPE;
            default -> throw badRequest("method is invalid");
        };
    }

    private static BudgetSettings.BudgetClass parseBudgetClass(String value) {
        if (value == null) {
            throw badRequest("budget class is required");
        }
        try {
            return BudgetSettings.BudgetClass.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw badRequest("budget class is invalid");
        }
    }

    private static MonthlyTrackerEntry.Status parseStatus(String value) {
        try {
            return MonthlyTrackerEntry.Status.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw badRequest("status is invalid");
        }
    }

    private static YearMonth parseMonth(String value) {
        if (!value.matches("^[0-9]{4}-[0-9]{2}$")) {
            throw badRequest("month must use YYYY-MM format");
        }
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException ignored) {
            throw badRequest("month is invalid");
        }
    }

    private static String icon(ExpenseItem.BudgetClass budgetClass) {
        return switch (budgetClass) {
            case NEEDS -> "home";
            case WANTS -> "sparkles";
            case SAVINGS -> "piggy-bank";
        };
    }

    private static String color(ExpenseItem.BudgetClass budgetClass) {
        return switch (budgetClass) {
            case NEEDS -> "#2563EB";
            case WANTS -> "#A855F7";
            case SAVINGS -> "#16A34A";
        };
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw badRequest(field + " must be non-negative");
        }
        return value;
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw badRequest(field + " is required");
        }
        return value;
    }

    private static String requiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw badRequest(field + " is required");
        }
        return value;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
