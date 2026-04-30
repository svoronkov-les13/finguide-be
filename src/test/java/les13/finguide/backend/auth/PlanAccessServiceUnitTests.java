package les13.finguide.backend.auth;

import les13.finguide.backend.plans.FinancialPlan;
import les13.finguide.backend.plans.PlanState;
import les13.finguide.backend.plans.PlanStateRepository;
import les13.finguide.backend.users.UserProfile;
import les13.finguide.backend.users.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;

@ExtendWith(MockitoExtension.class)
class PlanAccessServiceUnitTests {
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_USER_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");

    @Mock
    private PlanStateRepository planRepository;

    @Mock
    private UserProfileRepository userProfiles;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedDemoModeDoesNotBypassPlanOwnership() {
        var service = new PlanAccessService(planRepository, userProfiles, currentUserProvider, true);
        var auth = new TestingAuthenticationToken("user", "token", "ROLE_user");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        CurrentUser currentUser = new CurrentUser("other-subject", "other@example.com", "Other", Set.of("user"));
        PlanState state = planState(OWNER_ID);
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser);
        when(userProfiles.findOrCreateFrom(currentUser)).thenReturn(profile(OTHER_USER_ID, "other-subject"));
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service.requirePlan(PLAN_ID))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(FORBIDDEN);
    }

    @Test
    void authenticatedCurrentPlanIsCreatedForCurrentProfile() {
        var service = new PlanAccessService(planRepository, userProfiles, currentUserProvider, true);
        var auth = new TestingAuthenticationToken("user", "token", "ROLE_user");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        CurrentUser currentUser = new CurrentUser("owner-subject", "owner@example.com", "Owner", Set.of("user"));
        UserProfile profile = profile(OWNER_ID, "owner-subject");
        PlanState state = planState(OWNER_ID);
        when(currentUserProvider.requireCurrentUser()).thenReturn(currentUser);
        when(userProfiles.findOrCreateFrom(currentUser)).thenReturn(profile);
        when(planRepository.findOrCreateCurrentForOwner(OWNER_ID)).thenReturn(state);

        assertThat(service.requireCurrentPlan()).isSameAs(state);
        verify(planRepository).findOrCreateCurrentForOwner(OWNER_ID);
    }

    private static PlanState planState(UUID ownerId) {
        return new PlanState(
                new FinancialPlan(PLAN_ID, ownerId, "Основной план", "RUB", Instant.now(), Instant.now()),
                null,
                null,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                null,
                null,
                Instant.now()
        );
    }

    private static UserProfile profile(UUID id, String subject) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new UserProfile(id, subject, subject + "@example.com", "User", null, null, null, null, BigDecimal.ZERO, now, now);
    }
}
