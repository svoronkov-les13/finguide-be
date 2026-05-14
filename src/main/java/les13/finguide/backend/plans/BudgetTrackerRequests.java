package les13.finguide.backend.plans;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BudgetTrackerRequests {
    private BudgetTrackerRequests() {
    }

    public record BudgetRequest(
            String method,
            List<EnvelopeRequest> envelopes,
            Map<UUID, String> classifications
    ) {
    }

    public record EnvelopeRequest(
            UUID id,
            String name,
            BigDecimal limit,
            String icon,
            String color
    ) {
    }

    public record MonthlyTrackerRequest(
            String month,
            String status,
            String note
    ) {
    }

    public record OperationJournalEntryRequest(
            String date,
            String title,
            BigDecimal amount,
            String type,
            String status
    ) {
    }
}
