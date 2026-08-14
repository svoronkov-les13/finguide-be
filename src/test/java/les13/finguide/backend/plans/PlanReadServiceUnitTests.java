package les13.finguide.backend.plans;

import les13.finguide.backend.auth.PlanAccessService;
import les13.finguide.backend.analytics.CashFlowProjectionPoint;
import les13.finguide.backend.analytics.GoalAllocation;
import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.users.UserProfile;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanReadServiceUnitTests {
    private static final UUID PLAN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void cashflowUsesRetirementIncomeExpensesAndReturnAfterRetirement() {
        PlanState state = planState();

        List<CashFlowProjectionPoint> points = PlanReadService.cashflow(state, 2);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).year()).isEqualTo(2026);
        assertThat(points.get(0).totalIncome()).isEqualByComparingTo("1200.00");
        assertThat(points.get(0).totalExpenses()).isEqualByComparingTo("360.00");
        assertThat(points.get(0).investmentReturnPct()).isEqualByComparingTo("0");
        assertThat(points.get(0).capitalEndOfYear()).isEqualByComparingTo("100840.00");

        assertThat(points.get(1).year()).isEqualTo(2027);
        assertThat(points.get(1).totalIncome()).isEqualByComparingTo("1440.00");
        assertThat(points.get(1).totalExpenses()).isEqualByComparingTo("960.00");
        assertThat(points.get(1).netSavings()).isEqualByComparingTo("480.00");
        assertThat(points.get(1).investmentReturnPct()).isEqualByComparingTo("10");
        assertThat(points.get(1).capitalEndOfYear()).isEqualByComparingTo("111404.00");
    }

    @Test
    void goalProgressUsesFundingAvailableByTargetMonthNotFullHorizon() {
        Goal lateGoal = goal("22222222-2222-4222-8222-222222222222", "Домик", 2, 2027, 12, "300");
        PlanStateRepository repository = mock(PlanStateRepository.class);
        when(repository.findMonthlyTrackerEntries(any(), anyInt())).thenReturn(List.of());
        PlanReadService service = new PlanReadService(repository, mock(PlanAccessService.class));

        GoalAllocation allocation = service.goalAllocations(goalPlanState(List.of(
                goal("33333333-3333-4333-8333-333333333333", "Обучение", 1, 2026, 10, "100"),
                lateGoal
        ))).get(lateGoal.id());

        assertThat(allocation.savedAmount()).isEqualByComparingTo("140.00");
        assertThat(allocation.progressPct()).isEqualByComparingTo("46.7");
        assertThat(allocation.reachable()).isFalse();
        assertThat(allocation.completionYear()).isEqualTo(2029);
    }

    private static PlanState planState() {
        return new PlanState(
                new FinancialPlan(PLAN_ID, UUID.randomUUID(), "Test", "RUB", NOW, NOW),
                new UserProfile(UUID.randomUUID(), "subject", "test@example.com", "Test", null, null, 45, null, BigDecimal.ZERO, NOW, NOW),
                new PensionSettings(
                        PLAN_ID,
                        45,
                        46,
                        BigDecimal.valueOf(50),
                        BigDecimal.valueOf(50),
                        "RUB",
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        PensionSettings.WithdrawalStrategy.SPEND_DOWN_30Y,
                        true,
                        BigDecimal.valueOf(20)
                ),
                List.of(new IncomeSource(
                        UUID.randomUUID(),
                        PLAN_ID,
                        "Salary",
                        BigDecimal.valueOf(100),
                        "RUB",
                        IncomeSource.Frequency.MONTHLY,
                        IncomeSource.GrowthType.NONE,
                        BigDecimal.ZERO,
                        List.of(),
                        LocalDate.of(2026, 1, 1),
                        null,
                        NOW,
                        NOW
                )),
                List.of(new ExpenseItem(
                        UUID.randomUUID(),
                        PLAN_ID,
                        "Base expenses",
                        BigDecimal.valueOf(30),
                        "RUB",
                        ExpenseItem.Frequency.MONTHLY,
                        ExpenseItem.GrowthType.NONE,
                        BigDecimal.ZERO,
                        List.of(),
                        "No growth",
                        ExpenseItem.BudgetClass.NEEDS,
                        LocalDate.of(2026, 1, 1),
                        null,
                        NOW,
                        NOW
                )),
                List.of(),
                List.of(),
                null,
                new ModelAssumptions(2026, 2027, 2, 1981, 12, "RUB", BigDecimal.valueOf(100000), BigDecimal.ZERO, List.of(), "test"),
                NOW
        );
    }

    private static PlanState goalPlanState(List<Goal> goals) {
        return new PlanState(
                new FinancialPlan(PLAN_ID, UUID.randomUUID(), "Goals", "RUB", NOW, NOW),
                new UserProfile(UUID.randomUUID(), "subject", "test@example.com", "Test", null, null, 45, null, BigDecimal.ZERO, NOW, NOW),
                new PensionSettings(
                        PLAN_ID,
                        45,
                        90,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        "RUB",
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        PensionSettings.WithdrawalStrategy.SPEND_DOWN_30Y,
                        true,
                        BigDecimal.ZERO
                ),
                List.of(new IncomeSource(
                        UUID.randomUUID(),
                        PLAN_ID,
                        "Salary",
                        BigDecimal.TEN,
                        "RUB",
                        IncomeSource.Frequency.MONTHLY,
                        IncomeSource.GrowthType.NONE,
                        BigDecimal.ZERO,
                        List.of(),
                        LocalDate.of(2026, 1, 1),
                        null,
                        NOW,
                        NOW
                )),
                List.of(),
                goals,
                List.of(),
                null,
                new ModelAssumptions(2026, 2029, 4, 1981, 12, "RUB", BigDecimal.ZERO, BigDecimal.ZERO, List.of(), "test"),
                NOW
        );
    }

    private static Goal goal(String id, String name, int priority, int targetYear, int targetMonth, String cost) {
        return new Goal(
                UUID.fromString(id),
                PLAN_ID,
                name,
                "Target",
                new BigDecimal(cost),
                BigDecimal.ZERO,
                "RUB",
                targetYear,
                targetMonth,
                Goal.Type.ONE_TIME,
                Goal.GrowthType.NONE,
                BigDecimal.ZERO,
                null,
                priority,
                NOW,
                NOW
        );
    }
}
