package les13.finguide.backend.plans;

import les13.finguide.backend.auth.PlanAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class PlanManagementService {
    private final PlanStateRepository repository;
    private final PlanAccessService accessService;

    public PlanManagementService(PlanStateRepository repository, PlanAccessService accessService) {
        this.repository = repository;
        this.accessService = accessService;
    }

    public record PlanNameRequest(String name) {
    }

    public record CurrentPlanRequest(UUID planId) {
    }

    public List<PlanStateRepository.PlanSummary> plans() {
        UUID ownerId = accessService.currentProfile().id();
        repository.findOrCreateCurrentForOwner(ownerId);
        return repository.findPlansForOwner(ownerId);
    }

    @Transactional
    public PlanStateRepository.PlanSummary createBlank(PlanNameRequest request) {
        UUID ownerId = accessService.currentProfile().id();
        PlanState state = repository.createBlankPlanForOwner(ownerId, validateName(request));
        return summary(state, true);
    }

    @Transactional
    public PlanStateRepository.PlanSummary copy(UUID sourcePlanId, PlanNameRequest request) {
        PlanState source = accessService.requireWritablePlan(sourcePlanId);
        UUID ownerId = accessService.currentProfile().id();
        PlanState state = repository.copyPlanForOwner(ownerId, source.plan().id(), validateName(request));
        return summary(state, true);
    }

    @Transactional
    public PlanStateRepository.PlanSummary switchCurrent(CurrentPlanRequest request) {
        if (request == null || request.planId() == null) {
            throw badRequest("planId is required");
        }
        PlanState state = accessService.requireWritablePlan(request.planId());
        UUID ownerId = accessService.currentProfile().id();
        if (!repository.setCurrentPlanForOwner(ownerId, state.plan().id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Plan is not available for current user");
        }
        return summary(state, true);
    }

    private static PlanStateRepository.PlanSummary summary(PlanState state, boolean current) {
        return new PlanStateRepository.PlanSummary(
                state.plan().id(),
                state.plan().name(),
                current,
                state.plan().createdAt(),
                state.plan().updatedAt()
        );
    }

    private static String validateName(PlanNameRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw badRequest("name is required");
        }
        String name = request.name().trim();
        if (name.length() > 255) {
            throw badRequest("name must be 255 characters or less");
        }
        return name;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
