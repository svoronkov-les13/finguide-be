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
                                  "amount": 150000,
                                  "note": "On track"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/{planId}/calendar/monthly-tracker?year=2026", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].month").value("2026-05"))
                .andExpect(jsonPath("$.data[0].status").value("completed"))
                .andExpect(jsonPath("$.data[0].amount").value(150000))
                .andExpect(jsonPath("$.data[0].note").value("On track"))
                .andExpect(jsonPath("$.data[0].updatedAt").exists());

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].savedAmount").value(150000));

        mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "2026-05",
                                  "status": "partial",
                                  "amount": 75000,
                                  "note": "Half done"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/{planId}/calendar/monthly-tracker?year=2026", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("partial"))
                .andExpect(jsonPath("$.data[0].amount").value(75000))
                .andExpect(jsonPath("$.data[0].note").value("Half done"));

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].savedAmount").value(75000));
    }

    @Test
    void actualGoalTrackerEntriesContributeToGoalsAndOverflowByPriority() throws Exception {
        RequestPostProcessor jwt = userJwt("operation-journal-goal-owner");
        JsonNode current = currentPlan(jwt);
        String planId = current.at("/data/id").asText();
        String firstGoalId = current.at("/data/goals/0/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/contributions", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goalId": "%s",
                                  "amount": 1000000,
                                  "currency": "RUB",
                                  "date": "2026-05-14",
                                  "note": "Manual deposit"
                                }
                                """.formatted(firstGoalId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/plans/{planId}/tracker/entries", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-05-15",
                                  "title": "Факт взноса в цели",
                                  "amount": 600000,
                                  "type": "goal",
                                  "status": "actual"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].savedAmount").value(1500000))
                .andExpect(jsonPath("$.data.goals[1].savedAmount").value(100000));
    }

    @Test
    void monthlyTrackerAmountsOverflowToNextGoalByPriority() throws Exception {
        RequestPostProcessor jwt = userJwt("monthly-tracker-overflow-owner");
        JsonNode current = currentPlan(jwt);
        String planId = current.at("/data/id").asText();
        String firstGoalId = current.at("/data/goals/0/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/contributions", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "goalId": "%s",
                                  "amount": 1500000,
                                  "currency": "RUB",
                                  "date": "2026-05-14",
                                  "note": "Complete first goal"
                                }
                                """.formatted(firstGoalId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "2026-05",
                                  "status": "completed",
                                  "amount": 196000,
                                  "note": "May savings"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].savedAmount").value(1500000))
                .andExpect(jsonPath("$.data.goals[1].savedAmount").value(196000));
    }

    @Test
    void createsUpdatesListsAndDeletesOperationJournalEntries() throws Exception {
        RequestPostProcessor jwt = userJwt("operation-journal-owner");
        String planId = currentPlan(jwt).at("/data/id").asText();

        String createdBody = mockMvc.perform(post("/api/v1/plans/{planId}/tracker/entries", planId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-05-14",
                                  "title": "Кофе",
                                  "amount": -350,
                                  "type": "expense",
                                  "status": "actual"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.date").value("2026-05-14"))
                .andExpect(jsonPath("$.data.title").value("Кофе"))
                .andExpect(jsonPath("$.data.amount").value(-350))
                .andExpect(jsonPath("$.data.type").value("expense"))
                .andExpect(jsonPath("$.data.status").value("actual"))
                .andReturn().getResponse().getContentAsString();
        String entryId = objectMapper.readTree(createdBody).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}/tracker/entries?year=2026&month=5", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(entryId));

        mockMvc.perform(patch("/api/v1/plans/{planId}/tracker/entries/{entryId}", planId, entryId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Кофе и завтрак",
                                  "amount": -900,
                                  "status": "planned"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Кофе и завтрак"))
                .andExpect(jsonPath("$.data.amount").value(-900))
                .andExpect(jsonPath("$.data.status").value("planned"));

        mockMvc.perform(delete("/api/v1/plans/{planId}/tracker/entries/{entryId}", planId, entryId).with(jwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/{planId}/tracker/entries?year=2026&month=5", planId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void rejectsOperationJournalWritesForAnonymousSeedAndForeignPlan() throws Exception {
        String planId = currentPlan(userJwt("operation-journal-owner-a")).at("/data/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/tracker/entries", ANONYMOUS_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-05-14",
                                  "title": "Anonymous write",
                                  "amount": 100,
                                  "type": "income",
                                  "status": "actual"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/plans/{planId}/tracker/entries", planId)
                        .with(userJwt("operation-journal-owner-b")))
                .andExpect(status().isForbidden());
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
