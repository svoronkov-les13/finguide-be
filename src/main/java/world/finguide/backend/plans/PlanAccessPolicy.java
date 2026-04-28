package world.finguide.backend.plans;

import org.springframework.stereotype.Component;
import world.finguide.backend.auth.CurrentUser;

import java.util.UUID;

@Component
public class PlanAccessPolicy {
    public boolean canRead(CurrentUser user, FinancialPlan plan) {
        return ownsPlan(user, plan) || user.hasRole("ADMIN") || user.hasRole("ADVISOR");
    }

    public boolean canWrite(CurrentUser user, FinancialPlan plan) {
        return ownsPlan(user, plan) || user.hasRole("ADMIN");
    }

    private boolean ownsPlan(CurrentUser user, FinancialPlan plan) {
        UUID userId = UUID.nameUUIDFromBytes(user.keycloakSubject().getBytes());
        return plan.ownerUserId().equals(userId);
    }
}
