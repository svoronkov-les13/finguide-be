package les13.finguide.backend.pension;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pension analytics with both workbook variants: preserve capital and spend down capital.
 */
public record PensionProjection(
        int currentAge,
        int retirementAge,
        int retirementYear,
        BigDecimal capitalAtRetirement,
        BigDecimal nominalReturnPct,
        BigDecimal averageInflationPct,
        BigDecimal realReturnPct,
        PreserveCapital preserveCapital,
        SpendDown spendDown
) {
    public record PreserveCapital(
            BigDecimal annualSpendableAtRetirement,
            BigDecimal annualSpendableCurrentPrices,
            BigDecimal monthlySpendableCurrentPrices
    ) {
    }

    public record SpendDown(
            BigDecimal desiredMonthlyExpensesCurrentPrices,
            BigDecimal desiredAnnualExpensesAtRetirement,
            int retirementYears,
            int depletionAge,
            List<PensionSpendDownPoint> series
    ) {
    }
}
