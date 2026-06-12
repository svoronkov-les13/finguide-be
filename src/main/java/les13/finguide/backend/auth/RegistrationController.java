package les13.finguide.backend.auth;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import les13.finguide.backend.api.ApiEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {
    private final KeycloakRegistrationService registrationService;

    public RegistrationController(KeycloakRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @ApiResponse(responseCode = "201", description = "User registered in Keycloak")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> register(@Valid @RequestBody RegistrationRequest request) {
        registrationService.register(request);
        return ResponseEntity.created(URI.create("/api/v1/auth/register"))
                .body(ApiEnvelope.of(Map.of("email", request.email())));
    }
}
