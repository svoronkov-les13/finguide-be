package les13.finguide.backend.plans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FinancialItemServiceTests {
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired
    private FinancialItemService service;

    @Autowired
    private PlanReadService planReadService;

    @Test
    void createsIncomeAndDerivedDashboardUsesPersistedState() {
        BigDecimal before = (BigDecimal) planReadService.dashboard(PLAN_ID).get("totalMonthlyIncome");

        var created = service.createIncome(PLAN_ID, new FinancialItemRequests.IncomeRequest(
                null,
                "Тестовый доход",
                BigDecimal.valueOf(10_000),
                "RUB",
                "monthly",
                "manual",
                BigDecimal.valueOf(2),
                LocalDate.parse("2026-01-01"),
                null
        ));

        assertThat(service.income(PLAN_ID, created.id()).name()).isEqualTo("Тестовый доход");
        BigDecimal after = (BigDecimal) planReadService.dashboard(PLAN_ID).get("totalMonthlyIncome");
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
                null,
                "needs",
                LocalDate.parse("2026-01-01"),
                null
        );

        assertThatThrownBy(() -> service.createExpense(PLAN_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(BAD_REQUEST);
    }

    @Test
    void reordersGoalsAndPersistsPriorities() {
        List<UUID> reversedIds = service.goals(PLAN_ID).stream()
                .map(goal -> goal.id())
                .sorted(java.util.Comparator.reverseOrder())
                .toList();

        var reordered = service.reorderGoals(PLAN_ID, new FinancialItemRequests.GoalReorderRequest(reversedIds));

        assertThat(reordered).extracting(goal -> goal.id()).containsExactlyElementsOf(reversedIds);
        assertThat(reordered).extracting(goal -> goal.priority()).containsExactly(1, 2, 3);
    }
}
