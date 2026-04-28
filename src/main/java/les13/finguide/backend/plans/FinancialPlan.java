package les13.finguide.backend.plans;

import java.time.Instant;
import java.util.UUID;

public record FinancialPlan(
        UUID id,
        UUID ownerUserId,
        String name,
        String baseCurrency,
        Instant createdAt,
        Instant updatedAt
) {
}
