package les13.finguide.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtAudienceFilter extends OncePerRequestFilter {
    private final String requiredAudience;
    private final boolean demoMode;
    private final ObjectMapper objectMapper;

    public JwtAudienceFilter(
            @Value("${finguide.security.required-audience:finguide-api}") String requiredAudience,
            @Value("${finguide.security.demo-mode:false}") boolean demoMode,
            ObjectMapper objectMapper
    ) {
        this.requiredAudience = requiredAudience;
        this.demoMode = demoMode;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return demoMode || requiredAudience == null || requiredAudience.isBlank() || !request.getRequestURI().startsWith("/api/v1");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt && !jwt.getAudience().contains(requiredAudience)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            objectMapper.writeValue(response.getWriter(), error(request.getRequestURI()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static Map<String, Object> error(String path) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> details = Map.of("path", path);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "FORBIDDEN");
        error.put("message", "JWT audience is not accepted");
        error.put("details", details);
        error.put("requestId", UUID.randomUUID().toString());
        payload.put("error", error);
        return payload;
    }
}
