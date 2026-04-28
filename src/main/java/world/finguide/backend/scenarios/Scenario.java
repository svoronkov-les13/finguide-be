package world.finguide.backend.scenarios;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Scenario(
        UUID id,
        UUID basePlanId,
        String name,
        String emoji,
        String description,
        boolean base,
        Adjustments adjustments,
        Instant createdAt,
        Instant updatedAt
) {
    public record Adjustments(
            BigDecimal incomeAdjPct,
            BigDecimal expenseAdjPct,
            BigDecimal returnAdjPct,
            BigDecimal inflationAdjPct,
            int retirementAgeShift,
            BigDecimal goalsCostAdjPct
    ) {
    }
}
