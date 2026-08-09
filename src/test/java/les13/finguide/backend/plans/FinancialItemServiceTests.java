package les13.finguide.backend.plans;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static java.util.Collections.emptyList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FinancialItemServiceTests {
    private UUID planId;

    @Autowired
    private FinancialItemService service;

    @Autowired
    private PlanReadService planReadService;

    @BeforeEach
    void authenticateAndCreateOwnedPlan() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("financial-item-service-test")
                .audience(List.of("finguide-api"))
                .claim("email", "financial-item-service@example.com")
                .claim("name", "Financial Item Service")
                .claim("preferred_username", "financial-item-service-test")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, emptyList()));
        planId = planReadService.currentPlan().plan().id();
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsIncomeAndDerivedDashboardUsesPersistedState() {
        BigDecimal before = (BigDecimal) planReadService.dashboard(planId).get("totalMonthlyIncome");

        var created = service.createIncome(planId, new FinancialItemRequests.IncomeRequest(
                null,
                "Тестовый доход",
                BigDecimal.valueOf(10_000),
                "RUB",
                "monthly",
                "manual",
                BigDecimal.valueOf(2),
                List.of(),
                null,
                LocalDate.parse("2026-01-01"),
                null
        ));

        assertThat(service.income(planId, created.id()).name()).isEqualTo("Тестовый доход");
        BigDecimal after = (BigDecimal) planReadService.dashboard(planId).get("totalMonthlyIncome");
        assertThat(after).isEqualByComparingTo(before.add(BigDecimal.valueOf(10_000)));
    }

    @Test
    void rejectsNegativeAmountsBeforePersistence() {
        var request = new FinancialItemRequests.ExpenseRequest(
                null,
                "Некорректный расход",
                BigDecimal.valueOf(-1),
                "RUB",
                "monthly",
                "manual",
                BigDecimal.ZERO,
                List.of(),
                null,
                "needs",
                LocalDate.parse("2026-01-01"),
                null
        );

        assertThatThrownBy(() -> service.createExpense(planId, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(BAD_REQUEST);
    }

    @Test
    void reordersGoalsAndPersistsPriorities() {
        List<UUID> reversedIds = service.goals(planId).stream()
                .map(goal -> goal.id())
                .sorted(java.util.Comparator.reverseOrder())
                .toList();

        var reordered = service.reorderGoals(planId, new FinancialItemRequests.GoalReorderRequest(reversedIds));

        assertThat(reordered).extracting(goal -> goal.id()).containsExactlyElementsOf(reversedIds);
        assertThat(reordered).extracting(goal -> goal.priority()).containsExactly(1, 2, 3);
    }
}
