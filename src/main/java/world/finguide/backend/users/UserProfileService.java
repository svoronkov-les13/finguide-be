package world.finguide.backend.users;

import org.springframework.stereotype.Service;
import world.finguide.backend.auth.CurrentUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class UserProfileService {
    public UserProfile getOrCreate(CurrentUser currentUser) {
        Instant now = Instant.now();
        return new UserProfile(
                UUID.nameUUIDFromBytes(currentUser.keycloakSubject().getBytes()),
                currentUser.keycloakSubject(),
                currentUser.email(),
                currentUser.name(),
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                now,
                now
        );
    }
}
