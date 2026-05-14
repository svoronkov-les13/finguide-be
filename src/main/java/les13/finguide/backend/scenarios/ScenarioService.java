package les13.finguide.backend.scenarios;

import les13.finguide.backend.analytics.CashFlowProjectionPoint;
import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.analytics.YearRatePoint;
import les13.finguide.backend.auth.PlanAccessService;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.plans.PlanReadService;
import les13.finguide.backend.plans.PlanState;
import les13.finguide.backend.plans.PlanStateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ScenarioService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MIN_PCT = BigDecimal.valueOf(-100);
    private static final BigDecimal MAX_PCT = BigDecimal.valueOf(1000);
    private static final int MAX_CUSTOM_SCENARIOS = 10;
    private static final int MAX_COMPARE_SCENARIOS = 10;
    private static final int COMPARE_HORIZON_YEARS = 53;

    private final PlanReadService planReadService;
    private final PlanStateRepository repository;
    private final PlanAccessService accessService;

    public ScenarioService(PlanReadService planReadService, PlanStateRepository repository, PlanAccessService accessService) {
        this.planReadService = planReadService;
        this.repository = repository;
        this.accessService = accessService;
    }

    public List<Scenario> scenarios() {
        PlanState state = planReadService.currentPlan();
        List<Scenario> result = new ArrayList<>(builtIns(state.plan().id(), state.updatedAt()));
        result.addAll(repository.findScenarios(state.plan().id()));
        return result;
    }

    public Scenario scenario(String scenarioId) {
        PlanState state = planReadService.currentPlan();
        return resolveScenario(state.plan().id(), state.updatedAt(), scenarioId)
                .orElseThrow(() -> notFound("Scenario was not found"));
    }

    @Transactional
    public Scenario createScenario(ScenarioRequests.ScenarioRequest request) {
        if (request == null) {
            throw badRequest("scenario body is required");
        }
        PlanState state = planReadService.currentPlan();
        UUID planId = state.plan().id();
        accessService.requireWritablePlan(planId);
        if (repository.findScenarios(planId).size() >= MAX_CUSTOM_SCENARIOS) {
            throw badRequest("Custom scenario limit exceeded");
        }
        Instant now = Instant.now();
        Scenario scenario = new Scenario(
                UUID.randomUUID(),
                planId,
                requiredText(request.name(), "name", 120),
                optionalText(request.emoji(), "emoji", 16),
                optionalText(request.description(), "description", 1024),
                false,
                adjustments(request.adjustments(), null),
                now,
                now
        );
        return repository.createScenario(scenario);
    }

    @Transactional
    public Scenario updateScenario(String scenarioId, ScenarioRequests.ScenarioRequest request) {
        if (request == null) {
            throw badRequest("scenario body is required");
        }
        PlanState state = planReadService.currentPlan();
        UUID planId = state.plan().id();
        if (resolveBuiltIn(planId, state.updatedAt(), scenarioId).isPresent()) {
            throw forbidden("Built-in scenarios are read-only");
        }
        UUID id = parseUuid(scenarioId);
        Scenario current = repository.findScenario(planId, id).orElseThrow(() -> notFound("Scenario was not found"));
        accessService.requireWritablePlan(current.basePlanId());
        Scenario updated = new Scenario(
                current.id(),
                current.basePlanId(),
                request.name() == null ? current.name() : requiredText(request.name(), "name", 120),
                request.emoji() == null ? current.emoji() : optionalText(request.emoji(), "emoji", 16),
                request.description() == null ? current.description() : optionalText(request.description(), "description", 1024),
                false,
                adjustments(request.adjustments(), current.adjustments()),
                current.createdAt(),
                Instant.now()
        );
        return repository.updateScenario(updated);
    }

    @Transactional
    public void deleteScenario(String scenarioId) {
        PlanState state = planReadService.currentPlan();
        UUID planId = state.plan().id();
        if (resolveBuiltIn(planId, state.updatedAt(), scenarioId).isPresent()) {
            throw forbidden("Built-in scenarios are read-only");
        }
        UUID id = parseUuid(scenarioId);
        Scenario current = repository.findScenario(planId, id).orElseThrow(() -> notFound("Scenario was not found"));
        accessService.requireWritablePlan(current.basePlanId());
        if (!repository.deleteScenario(current.basePlanId(), current.id())) {
            throw notFound("Scenario was not found");
        }
    }

    public ScenarioComparison compare(ScenarioRequests.CompareRequest request) {
        PlanState state = planReadService.currentPlan();
        List<String> scenarioIds = request == null || request.scenarioIds() == null ? List.of() : request.scenarioIds();
        if (scenarioIds.isEmpty() || scenarioIds.size() > MAX_COMPARE_SCENARIOS) {
            throw badRequest("scenarioIds must contain 1..10 items");
        }
        Set<String> seen = new HashSet<>();
        List<ScenarioComparison.Result> results = new ArrayList<>();
        for (String scenarioId : scenarioIds) {
            if (scenarioId == null || scenarioId.isBlank() || !seen.add(scenarioId)) {
                throw badRequest("scenarioIds must be unique and non-blank");
            }
            Scenario scenario = resolveScenario(state.plan().id(), state.updatedAt(), scenarioId)
                    .orElseThrow(() -> notFound("Scenario was not found"));
            results.add(compareOne(state, scenario, stableScenarioId(scenario)));
        }
        return new ScenarioComparison(results);
    }

    private ScenarioComparison.Result compareOne(PlanState state, Scenario scenario, String scenarioId) {
        PlanState adjusted = adjustedState(state, scenario.adjustments());
        List<CashFlowProjectionPoint> projection = PlanReadService.cashflow(adjusted, COMPARE_HORIZON_YEARS);
        BigDecimal finalCapital = projection.isEmpty() ? ZERO : projection.get(projection.size() - 1).capitalEndOfYear();
        BigDecimal minCapital = projection.stream().map(CashFlowProjectionPoint::capitalEndOfYear).min(BigDecimal::compareTo).orElse(ZERO);
        int startYear = Math.max(Year.now().getValue(), adjusted.modelAssumptions().startYear());
        int retirementYear = startYear + Math.max(0, adjusted.pension().retirementAge() - adjusted.pension().currentAge());
        BigDecimal capitalAtRetirement = projection.stream()
                .filter(point -> point.year() >= retirementYear)
                .findFirst()
                .or(() -> projection.isEmpty() ? Optional.empty() : Optional.of(projection.get(projection.size() - 1)))
                .map(CashFlowProjectionPoint::capitalEndOfYear)
                .orElse(ZERO);
        BigDecimal totalGoalCost = adjusted.goals().stream().map(Goal::currentCost).reduce(ZERO, BigDecimal::add);
        BigDecimal goalCoveragePct = totalGoalCost.compareTo(ZERO) == 0
                ? HUNDRED
                : finalCapital.max(ZERO).multiply(HUNDRED).divide(totalGoalCost, 2, RoundingMode.HALF_UP).min(HUNDRED);
        return new ScenarioComparison.Result(scenarioId, scenario.name(), finalCapital, minCapital, retirementYear, capitalAtRetirement, goalCoveragePct, projection);
    }

    private PlanState adjustedState(PlanState state, Scenario.Adjustments adjustments) {
        BigDecimal incomeFactor = factor(adjustments.incomeAdjPct());
        BigDecimal expenseFactor = factor(adjustments.expenseAdjPct());
        BigDecimal goalFactor = factor(adjustments.goalsCostAdjPct());
        ModelAssumptions assumptions = state.modelAssumptions();
        ModelAssumptions adjustedAssumptions = new ModelAssumptions(
                assumptions.startYear(),
                assumptions.projectionEndYear(),
                assumptions.horizonYears(),
                assumptions.birthYear(),
                assumptions.monthsPerYear(),
                assumptions.currency(),
                assumptions.initialCapital(),
                assumptions.investmentReturnPct().add(adjustments.returnAdjPct()),
                assumptions.inflationSchedule().stream()
                        .map(point -> new YearRatePoint(point.year(), point.ratePct().add(adjustments.inflationAdjPct())))
                        .toList(),
                assumptions.sourceModel()
        );
        PensionSettings pension = state.pension();
        PensionSettings adjustedPension = new PensionSettings(
                pension.planId(),
                pension.currentAge(),
                pension.retirementAge() + adjustments.retirementAgeShift(),
                pension.monthlyExpenses().multiply(expenseFactor),
                pension.desiredMonthlyExpensesCurrentPrices().multiply(expenseFactor),
                pension.currency(),
                pension.expectedReturnPct().add(adjustments.returnAdjPct()),
                pension.inflationPct().add(adjustments.inflationAdjPct()),
                pension.withdrawalStrategy(),
                pension.statePensionEnabled(),
                pension.statePensionMonthly()
        );
        return new PlanState(
                state.plan(),
                state.profile(),
                adjustedPension,
                state.incomes().stream().map(income -> new IncomeSource(
                        income.id(), income.planId(), income.name(), income.amount().multiply(incomeFactor), income.currency(),
                        income.frequency(), income.growthType(), income.growthPct().add(adjustments.incomeAdjPct()),
                        income.startDate(), income.endDate(), income.createdAt(), income.updatedAt()
                )).toList(),
                state.expenses().stream().map(expense -> new ExpenseItem(
                        expense.id(), expense.planId(), expense.name(), expense.amount().multiply(expenseFactor), expense.currency(),
                        expense.frequency(), expense.growthType(), expense.growthPct().add(adjustments.expenseAdjPct()), expense.growthLabel(),
                        expense.budgetClass(), expense.startDate(), expense.endDate(), expense.createdAt(), expense.updatedAt()
                )).toList(),
                state.goals().stream().map(goal -> new Goal(
                        goal.id(), goal.planId(), goal.name(), goal.icon(), goal.currentCost().multiply(goalFactor), goal.savedAmount(),
                        goal.currency(), goal.targetYear(), goal.type(), goal.growthType(), goal.growthPct().add(adjustments.goalsCostAdjPct()),
                        goal.indexLabel(), goal.priority(), goal.createdAt(), goal.updatedAt()
                )).toList(),
                state.contributions(),
                state.budget(),
                adjustedAssumptions,
                state.updatedAt()
        );
    }

    private Optional<Scenario> resolveScenario(UUID planId, Instant updatedAt, String scenarioId) {
        Optional<Scenario> builtIn = resolveBuiltIn(planId, updatedAt, scenarioId);
        if (builtIn.isPresent()) {
            return builtIn;
        }
        return repository.findScenario(planId, parseUuid(scenarioId));
    }

    private Optional<Scenario> resolveBuiltIn(UUID planId, Instant updatedAt, String scenarioId) {
        return builtIns(planId, updatedAt).stream()
                .filter(scenario -> stableScenarioId(scenario).equals(scenarioId))
                .findFirst();
    }

    private List<Scenario> builtIns(UUID planId, Instant updatedAt) {
        Instant at = updatedAt == null ? Instant.EPOCH : updatedAt;
        return List.of(
                new Scenario(null, planId, "Основной план", "📊", "Текущий финансовый план", true, zeroAdjustments(), at, at),
                new Scenario(null, planId, "Оптимистичный", "🚀", "Доходы растут быстрее, расходы медленнее", false, new Scenario.Adjustments(BigDecimal.valueOf(15), BigDecimal.valueOf(5), ONE, BigDecimal.valueOf(-1), -2, ZERO), at, at),
                new Scenario(null, planId, "Пессимистичный", "⚡", "Стресс-тест: снижение доходов и рост расходов", false, new Scenario.Adjustments(BigDecimal.valueOf(-10), BigDecimal.valueOf(12), BigDecimal.valueOf(-2), BigDecimal.valueOf(2), 3, BigDecimal.valueOf(15)), at, at)
        );
    }

    public static String stableScenarioId(Scenario scenario) {
        if (scenario.id() != null) {
            return scenario.id().toString();
        }
        return switch (scenario.name()) {
            case "Основной план", "Базовый" -> "base";
            case "Оптимистичный" -> "optimistic";
            case "Пессимистичный" -> "pessimistic";
            default -> scenario.name().toLowerCase();
        };
    }

    private Scenario.Adjustments adjustments(ScenarioRequests.AdjustmentsRequest request, Scenario.Adjustments fallback) {
        Scenario.Adjustments base = fallback == null ? zeroAdjustments() : fallback;
        if (request == null) {
            return base;
        }
        return new Scenario.Adjustments(
                pct(request.incomeAdjPct(), base.incomeAdjPct(), "incomeAdjPct"),
                pct(request.expenseAdjPct(), base.expenseAdjPct(), "expenseAdjPct"),
                pct(request.returnAdjPct(), base.returnAdjPct(), "returnAdjPct"),
                pct(request.inflationAdjPct(), base.inflationAdjPct(), "inflationAdjPct"),
                shift(request.retirementAgeShift(), base.retirementAgeShift()),
                pct(request.goalsCostAdjPct(), base.goalsCostAdjPct(), "goalsCostAdjPct")
        );
    }

    private Scenario.Adjustments zeroAdjustments() {
        return new Scenario.Adjustments(ZERO, ZERO, ZERO, ZERO, 0, ZERO);
    }

    private BigDecimal pct(BigDecimal value, BigDecimal fallback, String field) {
        BigDecimal actual = value == null ? fallback : value;
        if (actual == null || actual.compareTo(MIN_PCT) < 0 || actual.compareTo(MAX_PCT) > 0) {
            throw badRequest(field + " is out of range");
        }
        return actual;
    }

    private int shift(Integer value, int fallback) {
        int actual = value == null ? fallback : value;
        if (actual < -20 || actual > 20) {
            throw badRequest("retirementAgeShift is out of range");
        }
        return actual;
    }

    private BigDecimal factor(BigDecimal pct) {
        return ONE.add(pct.divide(HUNDRED, 8, RoundingMode.HALF_UP));
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            throw notFound("Scenario was not found");
        }
    }

    private String requiredText(String value, String field, int maxLength) {
        String text = optionalText(value, field, maxLength);
        if (text == null || text.isBlank()) {
            throw badRequest(field + " is required");
        }
        return text;
    }

    private String optionalText(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength) {
            throw badRequest(field + " is too long");
        }
        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
