package les13.finguide.backend.users;

import les13.finguide.backend.auth.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(UserProfileRepository.class)
class UserProfileRepositoryTests {
    @Autowired
    private UserProfileRepository repository;

    @Test
    void syncsExistingProfileNameFromCurrentRegisteredUser() {
        CurrentUser user = new CurrentUser(
                "demo-user",
                "stas@example.com",
                "Стас Воронков",
                Set.of("user")
        );

        UserProfile profile = repository.findOrCreateFrom(user);

        assertThat(profile.keycloakSubject()).isEqualTo("demo-user");
        assertThat(profile.email()).isEqualTo("stas@example.com");
        assertThat(profile.name()).isEqualTo("Стас Воронков");
        assertThat(profile.name()).isNotEqualTo("Александр Петров");
    }

    @Test
    void createsSafeFallbackNameWhenRegisteredNameIsMissing() {
        CurrentUser user = new CurrentUser(
                "keycloak-subject-42",
                "fallback@example.com",
                null,
                Set.of("user")
        );

        UserProfile profile = repository.findOrCreateFrom(user);

        assertThat(profile.name()).isEqualTo("fallback");
        assertThat(profile.name()).isNotEqualTo("Александр Петров");
    }
}
