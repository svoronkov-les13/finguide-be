package les13.finguide.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "finguide.security.demo-mode=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DemoModeAuthIntegrationTests {
    private static final String SEED_PLAN_ID = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anonymousDemoModeStillReadsSeedPlan() throws Exception {
        mockMvc.perform(get("/api/v1/plans/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(SEED_PLAN_ID))
                .andExpect(jsonPath("$.data.profile.name").value("Александр Петров"));
    }

    @Test
    void authenticatedUserWithoutOwnPlanGetsClonedUserOwnedPlan() throws Exception {
        String body = mockMvc.perform(get("/api/v1/plans/current").with(jwt().jwt(token -> token
                        .subject("new-keycloak-user")
                        .audience(List.of("finguide-api"))
                        .claim("email", "new-user@example.com")
                        .claim("name", "Новый Пользователь")
                        .claim("preferred_username", "new-user")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", not(SEED_PLAN_ID)))
                .andExpect(jsonPath("$.data.profile.email").value("new-user@example.com"))
                .andExpect(jsonPath("$.data.profile.name").value("Новый Пользователь"))
                .andExpect(jsonPath("$.data.incomes", hasSize(3)))
                .andExpect(jsonPath("$.data.expenses", hasSize(3)))
                .andExpect(jsonPath("$.data.goals", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        String userPlanId = objectMapper.readTree(body).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}/dashboard", userPlanId).with(jwt().jwt(token -> token
                        .subject("new-keycloak-user")
                        .audience(List.of("finguide-api"))
                        .claim("email", "new-user@example.com")
                        .claim("name", "Новый Пользователь")
                        .claim("preferred_username", "new-user")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMonthlyIncome").value(345000));
    }

    @Test
    void authenticatedCurrentPlanCreationIsIdempotent() throws Exception {
        String first = currentPlanId("idempotent-user", "idempotent@example.com", "Идем Потент");
        String second = currentPlanId("idempotent-user", "idempotent@example.com", "Идем Потент");

        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }


    @Test
    void parallelFirstCurrentPlanAccessIsIdempotentForNewUser() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> request = () -> currentPlanId("parallel-first-user", "parallel@example.com", "Параллельный Пользователь");
            Future<String> first = executor.submit(request);
            Future<String> second = executor.submit(request);

            org.assertj.core.api.Assertions.assertThat(first.get()).isEqualTo(second.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void twoAuthenticatedUsersReceiveSeparatePlans() throws Exception {
        String first = currentPlanId("first-user", "first@example.com", "Первый Пользователь");
        String second = currentPlanId("second-user", "second@example.com", "Второй Пользователь");

        org.assertj.core.api.Assertions.assertThat(first).isNotEqualTo(SEED_PLAN_ID);
        org.assertj.core.api.Assertions.assertThat(second).isNotEqualTo(SEED_PLAN_ID);
        org.assertj.core.api.Assertions.assertThat(first).isNotEqualTo(second);
    }

    @Test
    void authenticatedNonOwnerCannotAccessSeedPlanInDemoMode() throws Exception {
        mockMvc.perform(get("/api/v1/plans/{planId}/dashboard", SEED_PLAN_ID).with(jwt().jwt(token -> token
                        .subject("non-owner-user")
                        .audience(List.of("finguide-api"))
                        .claim("email", "non-owner@example.com")
                        .claim("name", "Не Владелец")
                        .claim("preferred_username", "non-owner")
                )))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousDemoModeCannotMutateSeedPlan() throws Exception {
        mockMvc.perform(post("/api/v1/plans/{planId}/incomes", SEED_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Anonymous bonus",
                                  "amount": 50000,
                                  "currency": "RUB",
                                  "frequency": "yearly",
                                  "growthType": "manual",
                                  "growthPct": 3,
                                  "startDate": "2026-01-01"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/plans/{planId}/incomes", SEED_PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void authenticatedMutationDoesNotAffectAnotherUserOrSeedPlan() throws Exception {
        String firstPlan = currentPlanId("mutation-user-one", "one@example.com", "Первый");
        String secondPlan = currentPlanId("mutation-user-two", "two@example.com", "Второй");

        mockMvc.perform(post("/api/v1/plans/{planId}/incomes", firstPlan).with(jwt().jwt(token -> token
                        .subject("mutation-user-one")
                        .audience(List.of("finguide-api"))
                        .claim("email", "one@example.com")
                        .claim("name", "Первый")
                        .claim("preferred_username", "one")
                ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Бонус",
                                  "amount": 50000,
                                  "currency": "RUB",
                                  "frequency": "yearly",
                                  "growthType": "manual",
                                  "growthPct": 3,
                                  "startDate": "2026-01-01"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/plans/{planId}/incomes", firstPlan).with(jwt().jwt(token -> token
                        .subject("mutation-user-one")
                        .audience(List.of("finguide-api"))
                        .claim("email", "one@example.com")
                        .claim("name", "Первый")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)));

        mockMvc.perform(get("/api/v1/plans/{planId}/incomes", secondPlan).with(jwt().jwt(token -> token
                        .subject("mutation-user-two")
                        .audience(List.of("finguide-api"))
                        .claim("email", "two@example.com")
                        .claim("name", "Второй")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

        mockMvc.perform(get("/api/v1/plans/{planId}/incomes", SEED_PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void authenticatedMeUsesJwtProfile() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
                        .subject("new-keycloak-user")
                        .audience(List.of("finguide-api"))
                        .claim("email", "new-user@example.com")
                        .claim("name", "Стас Воронков")
                        .claim("preferred_username", "new-user")
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("new-user@example.com"))
                .andExpect(jsonPath("$.data.name").value("Стас Воронков"));
    }

    private String currentPlanId(String subject, String email, String name) throws Exception {
        String body = mockMvc.perform(get("/api/v1/plans/current").with(jwt().jwt(token -> token
                        .subject(subject)
                        .audience(List.of("finguide-api"))
                        .claim("email", email)
                        .claim("name", name)
                        .claim("preferred_username", subject)
                )))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }
}
