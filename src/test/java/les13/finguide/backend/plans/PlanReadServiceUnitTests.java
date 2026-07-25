package les13.finguide.backend.plans;

import les13.finguide.backend.analytics.CashFlowProjectionPoint;
import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.expenses.ExpenseItem;
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
}
