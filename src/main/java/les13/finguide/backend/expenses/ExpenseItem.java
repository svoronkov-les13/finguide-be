package les13.finguide.backend.expenses;

import les13.finguide.backend.analytics.YearRatePoint;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExpenseItem(
        UUID id,
        UUID planId,
        String name,
        BigDecimal amount,
        String currency,
        Frequency frequency,
        GrowthType growthType,
        BigDecimal growthPct,
        List<YearRatePoint> growthSchedule,
        String growthLabel,
        BudgetClass budgetClass,
        LocalDate startDate,
        LocalDate endDate,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Frequency {
        MONTHLY,
        YEARLY,
        ONE_TIME
    }

    public enum GrowthType {
        MANUAL,
        INFLATION,
        NONE
    }

    public enum BudgetClass {
        NEEDS,
        WANTS,
        SAVINGS
    }
}
