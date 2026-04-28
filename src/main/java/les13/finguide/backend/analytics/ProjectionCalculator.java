package les13.finguide.backend.analytics;

import org.springframework.stereotype.Service;
import les13.finguide.backend.plans.FinancialPlan;

import java.util.List;

@Service
public class ProjectionCalculator {
    public List<YearlyProjectionPoint> project(FinancialPlan plan, int years) {
        return List.of();
    }
}
