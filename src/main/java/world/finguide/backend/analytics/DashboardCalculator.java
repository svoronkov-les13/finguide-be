package world.finguide.backend.analytics;

import org.springframework.stereotype.Service;
import world.finguide.backend.plans.FinancialPlan;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DashboardCalculator {
    public DashboardMetrics calculate(FinancialPlan plan) {
        return new DashboardMetrics(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                List.of()
        );
    }
}
