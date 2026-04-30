package les13.finguide.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.not;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "finguide.security.demo-mode=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthIntegrationTests {
    private static final String PLAN_ID = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsProtectedApiWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/plans/current"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedDemoUserJwtGetsOwnPlanAndMapsCurrentProfile() throws Exception {
        mockMvc.perform(get("/api/v1/plans/current").with(ownerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", not(PLAN_ID)))
                .andExpect(jsonPath("$.data.profile.email").value("stas@example.com"))
                .andExpect(jsonPath("$.data.profile.name").value("Стас Воронков"));

        mockMvc.perform(get("/api/v1/me").with(ownerJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keycloakSubject").value("demo-user"))
                .andExpect(jsonPath("$.data.email").value("stas@example.com"))
                .andExpect(jsonPath("$.data.name").value("Стас Воронков"));
    }

    @Test
    void createsCurrentProfileWithRegisteredFullNameFromJwt() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
                        .subject("registered-user")
                        .audience(List.of("finguide-api"))
                        .claim("email", "registered@example.com")
                        .claim("given_name", "Иван")
                        .claim("family_name", "Сидоров")
                        .claim("preferred_username", "ivan")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keycloakSubject").value("registered-user"))
                .andExpect(jsonPath("$.data.email").value("registered@example.com"))
                .andExpect(jsonPath("$.data.name").value("Иван Сидоров"));
    }

    @Test
    void rejectsJwtWithWrongAudience() throws Exception {
        mockMvc.perform(get("/api/v1/plans/current").with(jwt().jwt(token -> token
                        .subject("demo-user")
                        .audience(List.of("other-api"))
                        .claim("email", "alex.petrov@example.com")
                )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void deniesPlanAccessForDifferentUserAndAllowsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/plans/{planId}/dashboard", PLAN_ID).with(jwt().jwt(token -> token
                        .subject("other-user")
                        .audience(List.of("finguide-api"))
                        .claim("email", "other@example.com")
                        .claim("preferred_username", "other")
                )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/plans/{planId}/dashboard", PLAN_ID).with(jwt().jwt(token -> token
                        .subject("other-admin")
                        .audience(List.of("finguide-api"))
                        .claim("email", "admin@example.com")
                        .claim("preferred_username", "admin")
                        .claim("realm_access", Map.of("roles", List.of("admin")))
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMonthlyIncome").value(345000));
    }

    @Test
    void swaggerDocumentsBearerAuthSecurityScheme() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/plans/current'].get.security[0].bearerAuth").exists());
    }

    private static RequestPostProcessor ownerJwt() {
        return jwt().jwt(token -> token
                .subject("demo-user")
                .audience(List.of("finguide-api"))
                .claim("email", "stas@example.com")
                .claim("name", "Стас Воронков")
                .claim("realm_access", Map.of("roles", List.of("user")))
        );
    }
}
