package les13.finguide.backend.scenarios;

import les13.finguide.backend.analytics.CashFlowProjectionPoint;

import java.math.BigDecimal;
import java.util.List;

public record ScenarioComparison(
        List<Result> scenarios
) {
    public record Result(
            String scenarioId,
            String name,
            BigDecimal finalCapital,
            BigDecimal minCapital,
            int retirementYear,
            BigDecimal capitalAtRetirement,
            BigDecimal goalCoveragePct,
            List<CashFlowProjectionPoint> projection
    ) {
    }
}
