package les13.finguide.backend.analytics;

import org.springframework.stereotype.Service;
import les13.finguide.backend.plans.FinancialPlan;

import java.util.List;

@Service
public class HealthScoreCalculator {
    public HealthScore calculate(FinancialPlan plan) {
        return new HealthScore(0, List.of());
    }
}
