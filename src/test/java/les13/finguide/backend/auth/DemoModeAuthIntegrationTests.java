package les13.finguide.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "finguide.security.demo-mode=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DemoModeAuthIntegrationTests {
    private static final String PLAN_ID = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void authenticatedDemoUserWithoutOwnPlanCanUseSeedPlan() throws Exception {
        mockMvc.perform(get("/api/v1/plans/current").with(jwt().jwt(token -> token
                        .subject("new-keycloak-user")
                        .audience(List.of("finguide-api"))
                        .claim("email", "new-user@example.com")
                        .claim("preferred_username", "new-user")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PLAN_ID));

        mockMvc.perform(get("/api/v1/plans/{planId}/dashboard", PLAN_ID).with(jwt().jwt(token -> token
                        .subject("new-keycloak-user")
                        .audience(List.of("finguide-api"))
                        .claim("email", "new-user@example.com")
                        .claim("preferred_username", "new-user")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMonthlyIncome").value(345000));
    }
}
