package les13.finguide.backend.plans;

import les13.finguide.backend.auth.PlanAccessService;
import les13.finguide.backend.contributions.Contribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Legacy compatibility service for the historical contributions ledger.
 * Writes are disabled; existing records are only exposed for migration/cleanup.
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
        throw ledgerDisabled();
    }

    @Deprecated(since = "0.1", forRemoval = false)
    @Transactional
    public Contribution updateContribution(UUID planId, UUID contributionId, ContributionRequests.ContributionRequest request) {
        accessService.requireWritablePlan(planId);
        throw ledgerDisabled();
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
        auditLog.info("contribution_deleted planId={} contributionId={} goalId={}", planId, contributionId, current.goalId());
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException ledgerDisabled() {
        return badRequest("goal contribution ledger is disabled; use goal savedAmount and savings tracker instead");
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
