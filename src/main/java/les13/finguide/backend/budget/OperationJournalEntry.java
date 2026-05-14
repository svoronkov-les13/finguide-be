package les13.finguide.backend.budget;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OperationJournalEntry(
        UUID id,
        UUID planId,
        LocalDate date,
        String title,
        BigDecimal amount,
        Type type,
        Status status,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Type {
        INCOME,
        EXPENSE,
        GOAL
    }

    public enum Status {
        PLANNED,
        ACTUAL
    }
}
