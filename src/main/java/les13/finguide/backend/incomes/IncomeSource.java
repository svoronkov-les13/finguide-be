package les13.finguide.backend.incomes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record IncomeSource(
        UUID id,
        UUID planId,
        String name,
        BigDecimal amount,
        String currency,
        Frequency frequency,
        GrowthType growthType,
        BigDecimal growthPct,
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
}
