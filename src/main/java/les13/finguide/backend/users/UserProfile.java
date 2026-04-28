package les13.finguide.backend.users;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserProfile(
        UUID id,
        String keycloakSubject,
        String email,
        String name,
        String phone,
        String avatarUrl,
        Integer age,
        Gender gender,
        BigDecimal initialBalance,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Gender {
        MALE,
        FEMALE,
        OTHER
    }
}
