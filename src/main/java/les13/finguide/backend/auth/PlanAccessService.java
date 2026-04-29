package les13.finguide.backend.auth;

import les13.finguide.backend.plans.PlanState;
import les13.finguide.backend.plans.PlanStateRepository;
import les13.finguide.backend.users.UserProfile;
import les13.finguide.backend.users.UserProfileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PlanAccessService {
    private final PlanStateRepository planRepository;
    private final UserProfileRepository userProfiles;
    private final CurrentUserProvider currentUserProvider;
    private final boolean demoMode;

    public PlanAccessService(
            PlanStateRepository planRepository,
            UserProfileRepository userProfiles,
            CurrentUserProvider currentUserProvider,
            @Value("${finguide.security.demo-mode:false}") boolean demoMode
    ) {
        this.planRepository = planRepository;
        this.userProfiles = userProfiles;
        this.currentUserProvider = currentUserProvider;
        this.demoMode = demoMode;
    }

    public PlanState requireCurrentPlan() {
        if (demoBypass()) {
            return planRepository.findCurrent().orElseThrow(() -> notFound("Current plan was not found"));
        }
        UserProfile profile = currentProfile();
        return planRepository.findCurrentForOwner(profile.id())
                .or(() -> demoMode ? planRepository.findCurrent() : java.util.Optional.empty())
                .orElseThrow(() -> notFound("Current plan was not found"));
    }

    public PlanState requirePlan(UUID planId) {
        PlanState state = planRepository.findById(planId).orElseThrow(() -> notFound("Plan was not found"));
        if (demoBypass()) {
            return state;
        }
        CurrentUser user = currentUserProvider.requireCurrentUser();
        UserProfile profile = userProfiles.findOrCreateFrom(user);
        if (profile.id().equals(state.plan().ownerUserId()) || user.hasRole("admin") || demoMode) {
            return state;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Plan is not available for current user");
    }

    @Transactional
    public UserProfile currentProfile() {
        if (demoBypass()) {
            return requireCurrentPlan().profile();
        }
        return userProfiles.findOrCreateFrom(currentUserProvider.requireCurrentUser());
    }

    private boolean demoBypass() {
        if (!demoMode) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
