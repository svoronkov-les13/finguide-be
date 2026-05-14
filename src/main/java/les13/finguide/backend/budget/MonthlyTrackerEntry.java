package les13.finguide.backend.budget;

import java.time.Instant;
import java.time.YearMonth;

public record MonthlyTrackerEntry(
        YearMonth month,
        Status status,
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
