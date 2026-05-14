package les13.finguide.backend.scenarios;

import java.math.BigDecimal;
import java.util.List;

public final class ScenarioRequests {
    private ScenarioRequests() {
    }

    public record ScenarioRequest(
            String name,
            String emoji,
            String description,
            AdjustmentsRequest adjustments
    ) {
    }

    public record AdjustmentsRequest(
            BigDecimal incomeAdjPct,
            BigDecimal expenseAdjPct,
            BigDecimal returnAdjPct,
            BigDecimal inflationAdjPct,
            Integer retirementAgeShift,
            BigDecimal goalsCostAdjPct
    ) {
    }

    public record CompareRequest(List<String> scenarioIds) {
    }
}
