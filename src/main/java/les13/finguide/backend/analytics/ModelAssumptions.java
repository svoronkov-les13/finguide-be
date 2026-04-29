package les13.finguide.backend.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * Global assumptions from the workbook sheet "Вводные".
 */
public record ModelAssumptions(
        int startYear,
        Integer projectionEndYear,
        Integer horizonYears,
        Integer birthYear,
        int monthsPerYear,
        String currency,
        BigDecimal initialCapital,
        BigDecimal investmentReturnPct,
        List<YearRatePoint> inflationSchedule,
        String sourceModel
) {
}
