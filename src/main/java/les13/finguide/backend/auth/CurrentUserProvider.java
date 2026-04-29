package les13.finguide.backend.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUserProvider {
    private final KeycloakJwtAdapter jwtAdapter;

    public CurrentUserProvider(KeycloakJwtAdapter jwtAdapter) {
        this.jwtAdapter = jwtAdapter;
    }

    public CurrentUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated JWT principal is required");
        }
        return jwtAdapter.from(jwt);
    }
}
