package les13.finguide.backend.users;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import les13.finguide.backend.api.ApiEnvelope;
import les13.finguide.backend.auth.PlanAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {
    private final PlanAccessService accessService;

    public UserProfileController(PlanAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/me")
    public ApiEnvelope<Map<String, Object>> me() {
        UserProfile profile = accessService.currentProfile();
        return ApiEnvelope.of(Map.ofEntries(
                Map.entry("id", profile.id()),
                Map.entry("keycloakSubject", profile.keycloakSubject()),
                Map.entry("email", profile.email()),
                Map.entry("name", profile.name()),
                Map.entry("phone", profile.phone() == null ? "" : profile.phone()),
                Map.entry("avatarUrl", profile.avatarUrl() == null ? "" : profile.avatarUrl()),
                Map.entry("age", profile.age() == null ? 0 : profile.age()),
                Map.entry("initialBalance", profile.initialBalance()),
                Map.entry("createdAt", profile.createdAt()),
                Map.entry("updatedAt", profile.updatedAt())
        ));
    }
}
