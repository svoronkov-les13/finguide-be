package les13.finguide.backend.plans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ContributionControllerTests {
    private static final String ANONYMOUS_PLAN_ID = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsListsReadsUpdatesAndDeletesContributionForAuthenticatedPlan() throws Exception {
        RequestPostProcessor jwt = userJwt("contribution-owner");
        JsonNode current = currentPlan(jwt);
        String planId = current.at("/data/id").asText();
        String firstGoalId = current.at("/data/goals/0/id").asText();
        String secondGoalId = current.at("/data/goals/1/id").asText();
        org.assertj.core.api.Assertions.assertThat(current.at("/data/goals/0/savedAmount").decimalValue())
                .isEqualByComparingTo("0");

        String createdBody = mockMvc.perform(post("/api/v1/plans/{planId}/contributions", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contributionJson(firstGoalId, 1000, "2026-05-14", "Initial deposit")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.goalId").value(firstGoalId))
                .andExpect(jsonPath("$.data.amount").value(1000))
                .andExpect(jsonPath("$.data.currency").value("RUB"))
                .andExpect(jsonPath("$.data.note").value("Initial deposit"))
                .andReturn().getResponse().getContentAsString();
        String contributionId = objectMapper.readTree(createdBody).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}/contributions", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(contributionId));

        mockMvc.perform(get("/api/v1/plans/{planId}/contributions/{id}", planId, contributionId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.note").value("Initial deposit"));

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].savedAmount").value(1000));

        mockMvc.perform(patch("/api/v1/plans/{planId}/contributions/{id}", planId, contributionId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contributionJson(secondGoalId, 2500, "2026-05-15", "Moved deposit")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goalId").value(secondGoalId))
                .andExpect(jsonPath("$.data.amount").value(2500))
                .andExpect(jsonPath("$.data.date").value("2026-05-15"))
                .andExpect(jsonPath("$.data.note").value("Moved deposit"));

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].savedAmount").value(0))
                .andExpect(jsonPath("$.data.goals[1].savedAmount").value(2500));

        mockMvc.perform(delete("/api/v1/plans/{planId}/contributions/{id}", planId, contributionId).with(jwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[1].savedAmount").value(0));

        mockMvc.perform(get("/api/v1/plans/{planId}/contributions/{id}", planId, contributionId).with(jwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void capsManualContributionOverflowWhenThereIsNoNextGoal() throws Exception {
        RequestPostProcessor jwt = userJwt("contribution-overflow-last-owner");
        JsonNode current = currentPlan(jwt);
        String planId = current.at("/data/id").asText();
        String lastGoalId = current.at("/data/goals/2/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/contributions", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contributionJson(lastGoalId, 4000000, "2026-05-14", "Over target")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[2].savedAmount").value(3500000));
    }

    @Test
    void deletingGoalAlsoDeletesItsContributions() throws Exception {
        RequestPostProcessor jwt = userJwt("contribution-goal-delete-owner");
        JsonNode current = currentPlan(jwt);
        String planId = current.at("/data/id").asText();
        String goalId = current.at("/data/goals/0/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/contributions", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contributionJson(goalId, 1000, "2026-05-14", "Before goal delete")))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/plans/{planId}/goals/{id}", planId, goalId).with(jwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/{planId}/contributions", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void rejectsInvalidContributionRequest() throws Exception {
        RequestPostProcessor jwt = userJwt("contribution-invalid-owner");
        String planId = currentPlan(jwt).at("/data/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/contributions", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount": -1,
                                  "currency": "12!",
                                  "note": "bad"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownContribution() throws Exception {
        RequestPostProcessor jwt = userJwt("contribution-not-found-owner");
        String planId = currentPlan(jwt).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}/contributions/{id}", planId, UUID.randomUUID()).with(jwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsContributionMutationForAnonymousDemoPlan() throws Exception {
        String goalId = currentPlan(null).at("/data/goals/0/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/contributions", ANONYMOUS_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contributionJson(goalId, 1000, "2026-05-14", "Anonymous deposit")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsContributionAccessForAnotherUsersPlan() throws Exception {
        String planId = currentPlan(userJwt("contribution-owner-a")).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}/contributions", planId)
                        .with(userJwt("contribution-owner-b")))
                .andExpect(status().isForbidden());
    }

    private JsonNode currentPlan(RequestPostProcessor jwt) throws Exception {
        var request = get("/api/v1/plans/current");
        if (jwt != null) {
            request.with(jwt);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static String contributionJson(String goalId, int amount, String date, String note) {
        return """
                {
                  "goalId": "%s",
                  "amount": %d,
                  "currency": "RUB",
                  "date": "%s",
                  "note": "%s"
                }
                """.formatted(goalId, amount, date, note);
    }

    private static RequestPostProcessor userJwt(String subject) {
        return jwt().jwt(token -> token
                .subject(subject)
                .audience(List.of("finguide-api"))
                .claim("email", subject + "@example.com")
                .claim("name", "Contribution Owner")
                .claim("preferred_username", subject)
        );
    }
}
