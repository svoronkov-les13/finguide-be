package world.finguide.backend.contributions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record Contribution(
        UUID id,
        UUID goalId,
        BigDecimal amount,
        String currency,
        LocalDate date,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
