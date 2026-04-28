package les13.finguide.backend.analytics;

import java.math.BigDecimal;

public record YearlyProjectionPoint(
        int year,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal goalsCost,
        BigDecimal netSavings
) {
}
