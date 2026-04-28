package les13.finguide.backend.auth;

import java.util.Set;

public record CurrentUser(
        String keycloakSubject,
        String email,
        String name,
        Set<String> roles
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
