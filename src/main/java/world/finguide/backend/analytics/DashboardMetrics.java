package world.finguide.backend.analytics;

import java.math.BigDecimal;
import java.util.List;

public record DashboardMetrics(
        BigDecimal totalMonthlyIncome,
        BigDecimal totalYearlyIncome,
        BigDecimal totalMonthlyExpenses,
        BigDecimal totalYearlyExpenses,
        BigDecimal netMonthlyBalance,
        BigDecimal netYearlyBalance,
        BigDecimal savingsRatePct,
        BigDecimal projectedPensionCapital,
        int yearsToRetirement,
        List<YearlyProjectionPoint> yearlyProjection
) {
}
