package les13.finguide.backend.pension;

import java.math.BigDecimal;

/**
 * Yearly depletion row from the workbook sheet "Пенсия" spend-down variant.
 */
public record PensionSpendDownPoint(
        int year,
        int age,
        BigDecimal beginningCapital,
        BigDecimal plannedExpense,
        BigDecimal nominalReturnPct,
        BigDecimal endingCapital
) {
}
