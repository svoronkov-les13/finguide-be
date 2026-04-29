package les13.finguide.backend.analytics;

import java.math.BigDecimal;

/**
 * Per-year rate from the Excel model. API stores percent points: 6 means 6%.
 */
public record YearRatePoint(
        int year,
        BigDecimal ratePct
) {
}
