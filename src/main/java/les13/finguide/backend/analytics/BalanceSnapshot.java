package les13.finguide.backend.analytics;

import java.math.BigDecimal;

/**
 * Current-year balance split matching the workbook sheet "Баланс".
 */
public record BalanceSnapshot(
        int year,
        BigDecimal monthlyIncome,
        BigDecimal yearlyIncome,
        BigDecimal totalIncome,
        BigDecimal monthlyExpenses,
        BigDecimal yearlyExpenses,
        BigDecimal monthlyGoalExpenses,
        BigDecimal yearlyGoalExpenses,
        BigDecimal goalExpenses,
        BigDecimal totalOutflow,
        BigDecimal netSavings
) {
}
