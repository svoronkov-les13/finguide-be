package world.finguide.backend.analytics;

import org.springframework.stereotype.Service;
import world.finguide.backend.plans.FinancialPlan;

import java.util.List;

@Service
public class ProjectionCalculator {
    public List<YearlyProjectionPoint> project(FinancialPlan plan, int years) {
        return List.of();
    }
}
