package les13.finguide.backend.goals;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Goal(
        UUID id,
        UUID planId,
        String name,
        String icon,
        BigDecimal currentCost,
        BigDecimal savedAmount,
        String currency,
        int targetYear,
        Type type,
        GrowthType growthType,
        BigDecimal growthPct,
        String indexLabel,
        int priority,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Type {
        ONE_TIME,
        RECURRING
    }

    public enum GrowthType {
        MANUAL,
        INFLATION,
        NONE
    }
}
