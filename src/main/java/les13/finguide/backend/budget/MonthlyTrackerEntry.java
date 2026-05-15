package les13.finguide.backend.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

public record MonthlyTrackerEntry(
        YearMonth month,
        Status status,
        BigDecimal amount,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        COMPLETED,
        PARTIAL,
        MISSED
    }
}
