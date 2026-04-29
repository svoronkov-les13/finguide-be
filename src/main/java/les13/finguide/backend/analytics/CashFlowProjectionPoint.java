package les13.finguide.backend.analytics;

import java.math.BigDecimal;

/**
 * Yearly projection row derived from workbook sheets "Доходы", "Расходы", "Цели" and "Сбережения".
 */
public record CashFlowProjectionPoint(
        int year,
        Integer age,
        int periodNo,
        BigDecimal monthlyIncome,
        BigDecimal yearlyIncome,
        BigDecimal totalIncome,
        BigDecimal monthlyExpenses,
        BigDecimal yearlyExpenses,
        BigDecimal totalExpenses,
        BigDecimal monthlyGoalExpenses,
        BigDecimal yearlyGoalExpenses,
        BigDecimal totalGoalExpenses,
        BigDecimal netSavings,
        BigDecimal investmentReturnPct,
        BigDecimal capitalStartOfYear,
        BigDecimal capitalEndOfYear
) {
}
