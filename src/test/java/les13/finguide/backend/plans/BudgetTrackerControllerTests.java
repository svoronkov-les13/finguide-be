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
class BudgetTrackerControllerTests {
    private static final String ANONYMOUS_PLAN_ID = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getsPatchesAndAutogeneratesBudgetForAuthenticatedPlan() throws Exception {
        RequestPostProcessor jwt = userJwt("budget-owner");
        JsonNode current = currentPlan(jwt);
        String planId = current.at("/data/id").asText();
        String expenseId = current.at("/data/expenses/0/id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}/budget", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("503020"))
                .andExpect(jsonPath("$.data.envelopes", hasSize(0)));

        mockMvc.perform(patch("/api/v1/plans/{planId}/budget", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "envelope",
                                  "envelopes": [
                                    {
                                      "name": "Ипотека",
                                      "limit": 90000,
                                      "icon": "home",
                                      "color": "#2563EB"
                                    }
                                  ],
                                  "classifications": {
                                    "%s": "needs"
                                  }
                                }
                                """.formatted(expenseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("envelope"))
                .andExpect(jsonPath("$.data.envelopes", hasSize(1)))
                .andExpect(jsonPath("$.data.envelopes[0].name").value("Ипотека"))
                .andExpect(jsonPath("$.data.envelopes[0].limit").value(90000))
                .andExpect(jsonPath("$.data.envelopes[0].spent").value(85000))
                .andExpect(jsonPath("$.data.envelopes[0].remaining").value(5000))
                .andExpect(jsonPath("$.data.envelopes[0].isOver").value(false))
                .andExpect(jsonPath("$.data.classifications." + expenseId).value("needs"));

        mockMvc.perform(get("/api/v1/plans/{planId}/budget", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("envelope"))
                .andExpect(jsonPath("$.data.envelopes[0].spent").value(85000));

        mockMvc.perform(post("/api/v1/plans/{planId}/budget/envelopes/autogenerate", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.method").value("envelope"))
                .andExpect(jsonPath("$.data.envelopes", hasSize(3)))
                .andExpect(jsonPath("$.data.envelopes[0].name").value("Ипотека"))
                .andExpect(jsonPath("$.data.envelopes[0].limit").value(85000))
                .andExpect(jsonPath("$.data.envelopes[0].spent").value(85000))
                .andExpect(jsonPath("$.data.envelopes[0].remaining").value(0));
    }

    @Test
    void rejectsInvalidBudgetRequest() throws Exception {
        RequestPostProcessor jwt = userJwt("budget-invalid-owner");
        String planId = currentPlan(jwt).at("/data/id").asText();

        mockMvc.perform(patch("/api/v1/plans/{planId}/budget", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "envelope",
                                  "envelopes": [
                                    { "name": "Bad", "limit": -10, "icon": "x", "color": "#000000" }
                                  ],
                                  "classifications": {}
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/plans/{planId}/budget", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "envelope",
                                  "envelopes": [],
                                  "classifications": {
                                    "%s": "needs"
                                  }
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsDuplicateBudgetEnvelopeIds() throws Exception {
        RequestPostProcessor jwt = userJwt("budget-duplicate-envelope-owner");
        String planId = currentPlan(jwt).at("/data/id").asText();
        UUID envelopeId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/plans/{planId}/budget", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "envelope",
                                  "envelopes": [
                                    { "id": "%s", "name": "A", "limit": 1000, "icon": "home", "color": "#2563EB" },
                                    { "id": "%s", "name": "B", "limit": 2000, "icon": "home", "color": "#2563EB" }
                                  ],
                                  "classifications": {}
                                }
                                """.formatted(envelopeId, envelopeId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingExpenseRemovesBudgetClassification() throws Exception {
        RequestPostProcessor jwt = userJwt("budget-expense-delete-owner");
        JsonNode current = currentPlan(jwt);
        String planId = current.at("/data/id").asText();
        String expenseId = current.at("/data/expenses/0/id").asText();

        mockMvc.perform(patch("/api/v1/plans/{planId}/budget", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "envelope",
                                  "envelopes": [],
                                  "classifications": {
                                    "%s": "needs"
                                  }
                                }
                                """.formatted(expenseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classifications." + expenseId).value("needs"));

        mockMvc.perform(delete("/api/v1/plans/{planId}/expenses/{id}", planId, expenseId).with(jwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/{planId}/budget", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classifications." + expenseId).doesNotExist());
    }

    @Test
    void upsertsAndListsMonthlyTrackerEntries() throws Exception {
        RequestPostProcessor jwt = userJwt("tracker-owner");
        String planId = currentPlan(jwt).at("/data/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "2026-05",
                                  "status": "completed",
                                  "note": "On track"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/{planId}/calendar/monthly-tracker?year=2026", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].month").value("2026-05"))
                .andExpect(jsonPath("$.data[0].status").value("completed"))
                .andExpect(jsonPath("$.data[0].note").value("On track"))
                .andExpect(jsonPath("$.data[0].updatedAt").exists());

        mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "2026-05",
                                  "status": "partial",
                                  "note": "Half done"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/{planId}/calendar/monthly-tracker?year=2026", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("partial"))
                .andExpect(jsonPath("$.data[0].note").value("Half done"));
    }

    @Test
    void rejectsInvalidMonthlyTrackerRequest() throws Exception {
        RequestPostProcessor jwt = userJwt("tracker-invalid-owner");
        String planId = currentPlan(jwt).at("/data/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "2026-13",
                                  "status": "completed"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "2026-05",
                                  "status": "done"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBudgetAndTrackerWritesForAnonymousSeed() throws Exception {
        mockMvc.perform(patch("/api/v1/plans/{planId}/budget", ANONYMOUS_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method": "503020",
                                  "envelopes": [],
                                  "classifications": {}
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/plans/{planId}/budget/envelopes/autogenerate", ANONYMOUS_PLAN_ID))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", ANONYMOUS_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "2026-05",
                                  "status": "completed"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsBudgetReadForAnotherUsersPlan() throws Exception {
        String planId = currentPlan(userJwt("budget-owner-a")).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}/budget", planId)
                        .with(userJwt("budget-owner-b")))
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

    private static RequestPostProcessor userJwt(String subject) {
        return jwt().jwt(token -> token
                .subject(subject)
                .audience(List.of("finguide-api"))
                .claim("email", subject + "@example.com")
                .claim("name", "Budget Tracker Owner")
                .claim("preferred_username", subject)
        );
    }
}
