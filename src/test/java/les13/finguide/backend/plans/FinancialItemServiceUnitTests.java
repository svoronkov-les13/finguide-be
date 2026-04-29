package les13.finguide.backend.plans;

import les13.finguide.backend.goals.Goal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@ExtendWith(MockitoExtension.class)
class FinancialItemServiceUnitTests {
    private static final UUID PLAN_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Mock
    private PlanStateRepository repository;

    @InjectMocks
    private FinancialItemService service;

    @Test
    void rejectsCreateIncomeWhenEndDateIsBeforeStartDate() {
        when(repository.findById(PLAN_ID)).thenReturn(Optional.of(mock(PlanState.class)));

        var request = new FinancialItemRequests.IncomeRequest(
                null,
                "Некорректный доход",
                BigDecimal.TEN,
                "RUB",
                "monthly",
                "manual",
                BigDecimal.ZERO,
                LocalDate.parse("2026-12-31"),
                LocalDate.parse("2026-01-01")
        );

        assertThatThrownBy(() -> service.createIncome(PLAN_ID, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(BAD_REQUEST);
    }

    @Test
    void rejectsGoalReorderWhenListDoesNotMatchCurrentGoals() {
        Goal first = goal("33333333-3333-4333-8333-333333333333", 1);
        Goal second = goal("44444444-4444-4444-8444-444444444444", 2);
        when(repository.findById(PLAN_ID)).thenReturn(Optional.of(mock(PlanState.class)));
        when(repository.findGoals(PLAN_ID)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.reorderGoals(PLAN_ID, new FinancialItemRequests.GoalReorderRequest(List.of(first.id()))))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(BAD_REQUEST);
    }

    private static Goal goal(String id, int priority) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new Goal(
                UUID.fromString(id),
                PLAN_ID,
                "Goal " + priority,
                "target",
                BigDecimal.valueOf(1000),
                BigDecimal.ZERO,
                "RUB",
                2030,
                Goal.Type.ONE_TIME,
                Goal.GrowthType.MANUAL,
                BigDecimal.ZERO,
                null,
                priority,
                now,
                now
        );
    }
}
