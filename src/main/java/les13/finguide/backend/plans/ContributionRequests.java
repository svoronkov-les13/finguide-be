package les13.finguide.backend.plans;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class ContributionRequests {
    private ContributionRequests() {
    }

    public record ContributionRequest(
            UUID goalId,
            BigDecimal amount,
            String currency,
            LocalDate date,
            String note
    ) {
    }
}
