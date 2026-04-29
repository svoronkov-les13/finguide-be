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

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FinancialItemControllerTests {
    private static final String PLAN_ID = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesIncomeCrudEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/plans/{planId}/incomes", PLAN_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

        String createdBody = mockMvc.perform(post("/api/v1/plans/{planId}/incomes", PLAN_ID)
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("Бонус"))
                .andReturn().getResponse().getContentAsString();
        String createdId = objectMapper.readTree(createdBody).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}/incomes/{id}", PLAN_ID, createdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(50000));

        mockMvc.perform(patch("/api/v1/plans/{planId}/incomes/{id}", PLAN_ID, createdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":75000,\"growthPct\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(75000))
                .andExpect(jsonPath("$.data.growthPct").value(4));

        mockMvc.perform(delete("/api/v1/plans/{planId}/incomes/{id}", PLAN_ID, createdId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/{planId}/incomes/{id}", PLAN_ID, createdId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void exposesExpenseAndGoalCrudPlusGoalReorder() throws Exception {
        String expenseBody = mockMvc.perform(post("/api/v1/plans/{planId}/expenses", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Страховка",
                                  "amount": 120000,
                                  "currency": "RUB",
                                  "frequency": "yearly",
                                  "growthType": "inflation",
                                  "growthPct": 5,
                                  "growthLabel": "Инфляция",
                                  "budgetClass": "needs",
                                  "startDate": "2026-01-01"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.budgetClass").value("needs"))
                .andReturn().getResponse().getContentAsString();
        String expenseId = objectMapper.readTree(expenseBody).at("/data/id").asText();

        mockMvc.perform(patch("/api/v1/plans/{planId}/expenses/{id}", PLAN_ID, expenseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budgetClass\":\"wants\",\"amount\":130000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.budgetClass").value("wants"))
                .andExpect(jsonPath("$.data.amount").value(130000));

        String goalBody = mockMvc.perform(post("/api/v1/plans/{planId}/goals", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Обучение",
                                  "icon": "book-open",
                                  "currentCost": 800000,
                                  "savedAmount": 100000,
                                  "currency": "RUB",
                                  "targetYear": 2030,
                                  "type": "one_time",
                                  "growthType": "manual",
                                  "growthPct": 6,
                                  "indexLabel": "+6% / год",
                                  "priority": 4
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Обучение"))
                .andReturn().getResponse().getContentAsString();
        String goalId = objectMapper.readTree(goalBody).at("/data/id").asText();

        mockMvc.perform(patch("/api/v1/plans/{planId}/goals/{id}", PLAN_ID, goalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"savedAmount\":200000,\"priority\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savedAmount").value(200000));

        JsonNode goals = objectMapper.readTree(mockMvc.perform(get("/api/v1/plans/{planId}/goals", PLAN_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("data");
        List<String> reversedIds = java.util.stream.StreamSupport.stream(goals.spliterator(), false)
                .map(node -> node.get("id").asText())
                .sorted(java.util.Comparator.reverseOrder())
                .toList();
        mockMvc.perform(post("/api/v1/plans/{planId}/goals/reorder", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("goalIds", reversedIds))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].priority").value(1));

        mockMvc.perform(delete("/api/v1/plans/{planId}/expenses/{id}", PLAN_ID, expenseId))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/plans/{planId}/goals/{id}", PLAN_ID, goalId))
                .andExpect(status().isNoContent());
    }

    @Test
    void validatesRequestBodies() throws Exception {
        mockMvc.perform(post("/api/v1/plans/{planId}/expenses", PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad",
                                  "amount": -10,
                                  "currency": "RUB",
                                  "frequency": "monthly",
                                  "growthType": "manual",
                                  "growthPct": 0,
                                  "budgetClass": "needs",
                                  "startDate": "2026-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
