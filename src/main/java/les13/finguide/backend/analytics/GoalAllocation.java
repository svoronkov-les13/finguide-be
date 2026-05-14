package les13.finguide.backend.analytics;

import java.math.BigDecimal;

public record GoalAllocation(
        BigDecimal targetCost,
        BigDecimal savedAmount,
        BigDecimal progressPct,
        boolean reachable,
        Integer completionYear
) {
}
