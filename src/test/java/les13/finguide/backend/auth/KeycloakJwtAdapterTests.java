package les13.finguide.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtAdapterTests {
    private final KeycloakJwtAdapter adapter = new KeycloakJwtAdapter();

    @Test
    void mapsKeycloakClaimsToCurrentUser() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("keycloak-sub")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("email", "user@example.com")
                .claim("name", " Стас Воронков ")
                .claim("preferred_username", "stas")
                .claim("realm_access", Map.of("roles", List.of("user", "admin", "")))
                .build();

        CurrentUser user = adapter.from(jwt);

        assertThat(user.keycloakSubject()).isEqualTo("keycloak-sub");
        assertThat(user.email()).isEqualTo("user@example.com");
        assertThat(user.name()).isEqualTo("Стас Воронков");
        assertThat(user.roles()).containsExactlyInAnyOrder("user", "admin");
        assertThat(user.hasRole("admin")).isTrue();
    }

    @Test
    void buildsDisplayNameFromRegistrationNamePartsBeforeUsernameFallback() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("keycloak-sub")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claim("email", "user@example.com")
                .claim("given_name", "Стас")
                .claim("family_name", "Воронков")
                .claim("preferred_username", "stas")
                .build();

        CurrentUser user = adapter.from(jwt);

        assertThat(user.name()).isEqualTo("Стас Воронков");
    }
}
