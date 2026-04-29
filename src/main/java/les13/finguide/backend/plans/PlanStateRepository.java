package les13.finguide.backend.plans;

import java.util.Optional;
import java.util.UUID;

public interface PlanStateRepository {
    Optional<PlanState> findCurrent();

    Optional<PlanState> findById(UUID planId);
}
