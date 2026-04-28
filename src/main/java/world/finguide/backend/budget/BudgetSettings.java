package world.finguide.backend.budget;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record BudgetSettings(
        UUID planId,
        Method method,
        List<BudgetEnvelope> envelopes,
        Map<UUID, BudgetClass> classifications
) {
    public enum Method {
        RULE_50_30_20,
        ENVELOPE
    }

    public enum BudgetClass {
        NEEDS,
        WANTS,
        SAVINGS
    }
}
