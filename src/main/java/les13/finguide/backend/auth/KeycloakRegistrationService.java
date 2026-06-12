package les13.finguide.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

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

    public KeycloakRegistrationService(
            RestTemplate restTemplate,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${finguide.keycloak.admin-realm:master}") String adminRealm,
            @Value("${finguide.keycloak.admin-client-id:admin-cli}") String adminClientId,
            @Value("${finguide.keycloak.admin-username:}") String adminUsername,
            @Value("${finguide.keycloak.admin-password:}") String adminPassword
    ) {
        this.restTemplate = restTemplate;
        this.issuerUri = trimTrailingSlash(issuerUri);
        this.adminRealm = adminRealm;
        this.adminClientId = adminClientId;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    public void register(RegistrationRequest request) {
        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Registration is not configured");
        }

        String accessToken = adminAccessToken();
        createUser(request, accessToken);
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
