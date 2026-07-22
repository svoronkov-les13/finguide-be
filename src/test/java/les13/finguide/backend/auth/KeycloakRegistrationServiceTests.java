package les13.finguide.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakRegistrationServiceTests {
    @Test
    void createsEnabledKeycloakUserWithPasswordCredential() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        KeycloakRegistrationService service = service(restTemplate);

        server.expect(requestTo("https://finguide.les13.tech/auth/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=password")))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://finguide.les13.tech/auth/admin/realms/finguide/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "username": "stas@example.com",
                          "email": "stas@example.com",
                          "firstName": "Стас",
                          "lastName": "Воронков",
                          "enabled": true,
                          "emailVerified": false,
                          "credentials": [
                            {
                              "type": "password",
                              "value": "correct-horse-battery",
                              "temporary": false
                            }
                          ]
                        }
                        """))
                .andRespond(withCreatedEntity(java.net.URI.create("https://finguide.les13.tech/auth/admin/realms/finguide/users/user-id")));

        service.register(new RegistrationRequest("Стас", "Воронков", "stas@example.com", "correct-horse-battery"));

        server.verify();
    }

    @Test
    void failsWhenAdminCredentialsAreMissing() {
        KeycloakRegistrationService service = new KeycloakRegistrationService(
                new RestTemplate(),
                "https://finguide.les13.tech/auth/realms/finguide",
                "master",
                "admin-cli",
                "",
                "",
                "finguide-web",
                new PasswordResetRateLimiter(3, Duration.ofHours(1), java.time.Clock.systemUTC())
        );

        assertThatThrownBy(() -> service.register(new RegistrationRequest("Стас", "Воронков", "stas@example.com", "correct-horse-battery")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Registration is not configured");
    }

    @Test
    void sendsUpdatePasswordEmailForExistingUser() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        KeycloakRegistrationService service = service(restTemplate);

        server.expect(requestTo("https://finguide.les13.tech/auth/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("grant_type=password")))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://finguide.les13.tech/auth/admin/realms/finguide/users?email=stas@example.com&exact=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andRespond(withSuccess("[{\"id\":\"user-id\",\"email\":\"stas@example.com\"}]", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://finguide.les13.tech/auth/admin/realms/finguide/users/user-id/execute-actions-email?client_id=finguide-web"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(content().json("[\"UPDATE_PASSWORD\"]"))
                .andRespond(withSuccess());

        service.requestPasswordReset(new PasswordResetRequest("stas@example.com"));

        server.verify();
    }

    @Test
    void doesNotRevealMissingUserDuringPasswordReset() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        KeycloakRegistrationService service = service(restTemplate);

        server.expect(requestTo("https://finguide.les13.tech/auth/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://finguide.les13.tech/auth/admin/realms/finguide/users?email=missing@example.com&exact=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.requestPasswordReset(new PasswordResetRequest("missing@example.com"));

        server.verify();
    }

    @Test
    void rateLimitedPasswordResetDoesNotCallKeycloakAgain() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(1, Duration.ofHours(1), java.time.Clock.systemUTC());
        KeycloakRegistrationService service = service(restTemplate, limiter);

        server.expect(requestTo("https://finguide.les13.tech/auth/realms/master/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://finguide.les13.tech/auth/admin/realms/finguide/users?email=stas@example.com&exact=true"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        service.requestPasswordReset(new PasswordResetRequest("stas@example.com"));
        server.verify();
        server.reset();

        service.requestPasswordReset(new PasswordResetRequest("stas@example.com"));
        server.verify();
    }

    private static KeycloakRegistrationService service(RestTemplate restTemplate) {
        return service(restTemplate, new PasswordResetRateLimiter(3, Duration.ofHours(1), java.time.Clock.systemUTC()));
    }

    private static KeycloakRegistrationService service(RestTemplate restTemplate, PasswordResetRateLimiter limiter) {
        return new KeycloakRegistrationService(
                restTemplate,
                "https://finguide.les13.tech/auth/realms/finguide",
                "master",
                "admin-cli",
                "admin",
                "secret",
                "finguide-web",
                limiter
        );
    }
}
