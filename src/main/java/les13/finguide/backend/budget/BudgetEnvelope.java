package les13.finguide.backend.budget;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetEnvelope(
        UUID id,
        UUID planId,
        String name,
        BigDecimal limit,
        String icon,
        String color,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal pct,
        boolean overLimit
) {
}
