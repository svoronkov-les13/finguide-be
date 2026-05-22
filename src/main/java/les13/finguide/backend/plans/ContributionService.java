package les13.finguide.backend.plans;

import les13.finguide.backend.auth.PlanAccessService;
import les13.finguide.backend.contributions.Contribution;
import les13.finguide.backend.goals.Goal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Legacy compatibility service for the historical contributions ledger.
 * Current UI writes factual goal outflows through the operation journal.
 */
@Service
@Transactional(readOnly = true)
public class ContributionService {
    private static final Logger auditLog = LoggerFactory.getLogger("les13.finguide.audit");

    private final PlanStateRepository repository;
    private final PlanAccessService accessService;

    public ContributionService(PlanStateRepository repository, PlanAccessService accessService) {
        this.repository = repository;
        this.accessService = accessService;
    }

    @Deprecated(since = "0.1", forRemoval = false)
    public List<Contribution> contributions(UUID planId) {
        accessService.requirePlan(planId);
        return repository.findContributions(planId);
    }

    @Deprecated(since = "0.1", forRemoval = false)
    public Contribution contribution(UUID planId, UUID contributionId) {
        accessService.requirePlan(planId);
        return repository.findContribution(planId, contributionId)
                .orElseThrow(() -> notFound("Contribution was not found"));
    }

    @Deprecated(since = "0.1", forRemoval = false)
    @Transactional
    public Contribution createContribution(UUID planId, ContributionRequests.ContributionRequest request) {
        accessService.requireWritablePlan(planId);
        UUID goalId = required(request.goalId(), "goalId");
        requireGoal(planId, goalId);
        Instant now = Instant.now();
        Contribution contribution = new Contribution(
                UUID.randomUUID(),
                goalId,
                nonNegative(required(request.amount(), "amount"), "amount"),
                currency(requiredText(request.currency(), "currency")),
                required(request.date(), "date"),
                request.note(),
                now,
                now
        );
        Contribution created = repository.createContribution(planId, contribution);
        repository.recalculateGoalSavedAmount(planId, created.goalId());
        auditLog.info("contribution_created planId={} contributionId={} goalId={}", planId, created.id(), created.goalId());
        return created;
    }

    @Deprecated(since = "0.1", forRemoval = false)
    @Transactional
    public Contribution updateContribution(UUID planId, UUID contributionId, ContributionRequests.ContributionRequest request) {
        accessService.requireWritablePlan(planId);
        Contribution current = repository.findContribution(planId, contributionId)
                .orElseThrow(() -> notFound("Contribution was not found"));
        UUID nextGoalId = request.goalId() == null ? current.goalId() : request.goalId();
        requireGoal(planId, nextGoalId);
        Contribution next = new Contribution(
                current.id(),
                nextGoalId,
                request.amount() == null ? current.amount() : nonNegative(request.amount(), "amount"),
                request.currency() == null ? current.currency() : currency(request.currency()),
                request.date() == null ? current.date() : request.date(),
                request.note() == null ? current.note() : request.note(),
                current.createdAt(),
                Instant.now()
        );
        Contribution updated = repository.updateContribution(planId, next);
        repository.recalculateGoalSavedAmount(planId, current.goalId());
        if (!current.goalId().equals(updated.goalId())) {
            repository.recalculateGoalSavedAmount(planId, updated.goalId());
        }
        auditLog.info("contribution_updated planId={} contributionId={} goalId={}", planId, updated.id(), updated.goalId());
        return updated;
    }

    @Deprecated(since = "0.1", forRemoval = false)
    @Transactional
    public void deleteContribution(UUID planId, UUID contributionId) {
        accessService.requireWritablePlan(planId);
        Contribution current = repository.findContribution(planId, contributionId)
                .orElseThrow(() -> notFound("Contribution was not found"));
        if (!repository.deleteContribution(planId, contributionId)) {
            throw notFound("Contribution was not found");
        }
        repository.recalculateGoalSavedAmount(planId, current.goalId());
        auditLog.info("contribution_deleted planId={} contributionId={} goalId={}", planId, contributionId, current.goalId());
    }

    private Goal requireGoal(UUID planId, UUID goalId) {
        return repository.findGoal(planId, goalId)
                .orElseThrow(() -> notFound("Goal was not found"));
    }

    private static String currency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            throw badRequest("currency must be a 3-letter uppercase code");
        }
        return value;
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

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
