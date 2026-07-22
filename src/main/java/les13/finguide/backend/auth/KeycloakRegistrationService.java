package les13.finguide.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.List;
import java.util.Map;

@Service
public class KeycloakRegistrationService {
    private final RestTemplate restTemplate;
    private final String issuerUri;
    private final String adminRealm;
    private final String adminClientId;
    private final String adminUsername;
    private final String adminPassword;
    private final String passwordResetClientId;
    private final PasswordResetRateLimiter passwordResetRateLimiter;

    public KeycloakRegistrationService(
            RestTemplate restTemplate,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${finguide.keycloak.admin-realm:master}") String adminRealm,
            @Value("${finguide.keycloak.admin-client-id:admin-cli}") String adminClientId,
            @Value("${finguide.keycloak.admin-username:}") String adminUsername,
            @Value("${finguide.keycloak.admin-password:}") String adminPassword,
            @Value("${finguide.keycloak.password-reset-client-id:finguide-web}") String passwordResetClientId,
            PasswordResetRateLimiter passwordResetRateLimiter
    ) {
        this.restTemplate = restTemplate;
        this.issuerUri = trimTrailingSlash(issuerUri);
        this.adminRealm = adminRealm;
        this.adminClientId = adminClientId;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.passwordResetClientId = passwordResetClientId;
        this.passwordResetRateLimiter = passwordResetRateLimiter;
    }

    public void register(RegistrationRequest request) {
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Registration is not configured");
        }

        String accessToken = adminAccessToken();
        createUser(request, accessToken);
    }

    public void requestPasswordReset(PasswordResetRequest request) {
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Password reset is not configured");
        }
        if (!passwordResetRateLimiter.tryAcquire(request.email())) {
            return;
        }

        String accessToken = adminAccessToken();
        findUserIdByEmail(request.email(), accessToken).ifPresent(userId -> sendPasswordResetEmail(userId, accessToken));
    }

    private String adminAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", adminClientId);
        body.add("username", adminUsername);
        body.add("password", adminPassword);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> token = restTemplate.postForObject(
                    "%s/realms/%s/protocol/openid-connect/token".formatted(keycloakBaseUrl(), adminRealm),
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Object accessToken = token == null ? null : token.get("access_token");
            if (!(accessToken instanceof String value) || value.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak admin token response is invalid");
            }
            return value;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak admin token request failed", exception);
        }
    }

    private void createUser(RegistrationRequest request, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> user = Map.of(
                "username", request.email(),
                "email", request.email(),
                "firstName", request.firstName(),
                "lastName", request.lastName(),
                "enabled", true,
                "emailVerified", false,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", request.password(),
                        "temporary", false
                ))
        );

        try {
            restTemplate.postForEntity(
                    "%s/admin/realms/%s/users".formatted(keycloakBaseUrl(), targetRealm()),
                    new HttpEntity<>(user, headers),
                    Void.class
            );
        } catch (HttpClientErrorException.Conflict exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "User already exists", exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak user creation failed", exception);
        }
    }

    private java.util.Optional<String> findUserIdByEmail(String email, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        String url = UriComponentsBuilder
                .fromUriString("%s/admin/realms/%s/users".formatted(keycloakBaseUrl(), targetRealm()))
                .queryParam("email", normalizedEmail)
                .queryParam("exact", "true")
                .build()
                .encode()
                .toUriString();

        try {
            var response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> users = response.getBody();
            if (users == null || users.isEmpty()) {
                return java.util.Optional.empty();
            }
            Object id = users.getFirst().get("id");
            return id instanceof String value && !value.isBlank()
                    ? java.util.Optional.of(value)
                    : java.util.Optional.empty();
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak user lookup failed", exception);
        }
    }

    private void sendPasswordResetEmail(String userId, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = UriComponentsBuilder
                .fromUriString("%s/admin/realms/%s/users/%s/execute-actions-email".formatted(keycloakBaseUrl(), targetRealm(), userId))
                .queryParam("client_id", passwordResetClientId)
                .build()
                .encode()
                .toUriString();

        try {
            restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    new HttpEntity<>(List.of("UPDATE_PASSWORD"), headers),
                    Void.class
            );
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Keycloak password reset email failed", exception);
        }
    }

    private String keycloakBaseUrl() {
        int realmMarker = issuerUri.indexOf("/realms/");
        if (realmMarker < 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak issuer URI is invalid");
        }
        return issuerUri.substring(0, realmMarker);
    }

    private String targetRealm() {
        int realmMarker = issuerUri.indexOf("/realms/");
        if (realmMarker < 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Keycloak issuer URI is invalid");
        }
        return issuerUri.substring(realmMarker + "/realms/".length());
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
