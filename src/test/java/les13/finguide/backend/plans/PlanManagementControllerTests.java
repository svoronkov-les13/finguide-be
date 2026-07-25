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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PlanManagementControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsBlankPlanAndMakesItCurrent() throws Exception {
        RequestPostProcessor jwt = userJwt("plan-create-owner");
        String firstPlanId = currentPlan(jwt).at("/data/id").asText();

        String createdBody = mockMvc.perform(post("/api/v1/plans")
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Новый план\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.name").value("Новый план"))
                .andExpect(jsonPath("$.data.current").value(true))
                .andReturn().getResponse().getContentAsString();
        String createdPlanId = objectMapper.readTree(createdBody).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(createdPlanId))
                .andExpect(jsonPath("$.data.incomes", hasSize(0)))
                .andExpect(jsonPath("$.data.expenses", hasSize(0)))
                .andExpect(jsonPath("$.data.goals", hasSize(0)))
                .andExpect(jsonPath("$.data.contributions", hasSize(0)));

        JsonNode plans = readJson(mockMvc.perform(get("/api/v1/plans").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andReturn());
        org.assertj.core.api.Assertions.assertThat(plans.at("/data").findValuesAsText("id")).contains(firstPlanId, createdPlanId);
        long currentCount = 0;
        for (JsonNode plan : plans.at("/data")) {
            if (plan.path("current").asBoolean()) {
                currentCount++;
            }
        }
        org.assertj.core.api.Assertions.assertThat(currentCount).isEqualTo(1);
    }

    @Test
    void copiesPlanModelWithoutFactualHistory() throws Exception {
        RequestPostProcessor jwt = userJwt("plan-copy-owner");
        JsonNode source = currentPlan(jwt);
        String sourcePlanId = source.at("/data/id").asText();
        mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", sourcePlanId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "2026-05",
                                  "status": "completed",
                                  "amount": 45000,
                                  "note": "Actual month"
                                }
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/plans/{planId}/tracker/entries", sourcePlanId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-05-15",
                                  "title": "Actual expense",
                                  "amount": 10000,
                                  "type": "expense",
                                  "status": "actual"
                                }
                                """))
                .andExpect(status().isCreated());

        String copiedBody = mockMvc.perform(post("/api/v1/plans/{planId}/copy", sourcePlanId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Копия модели\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Копия модели"))
                .andExpect(jsonPath("$.data.current").value(true))
                .andReturn().getResponse().getContentAsString();
        String copiedPlanId = objectMapper.readTree(copiedBody).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(copiedPlanId))
                .andExpect(jsonPath("$.data.id").value(not(sourcePlanId)))
                .andExpect(jsonPath("$.data.incomes", hasSize(3)))
                .andExpect(jsonPath("$.data.expenses", hasSize(3)))
                .andExpect(jsonPath("$.data.goals", hasSize(3)))
                .andExpect(jsonPath("$.data.goals[0].savedAmount").value(0))
                .andExpect(jsonPath("$.data.goals[1].savedAmount").value(0))
                .andExpect(jsonPath("$.data.goals[2].savedAmount").value(0))
                .andExpect(jsonPath("$.data.contributions", hasSize(0)));

        mockMvc.perform(get("/api/v1/plans/{planId}/calendar/monthly-tracker?year=2026", copiedPlanId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        mockMvc.perform(get("/api/v1/plans/{planId}/tracker/entries?year=2026&month=5", copiedPlanId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void switchesCurrentPlanAndRejectsForeignPlan() throws Exception {
        RequestPostProcessor owner = userJwt("plan-switch-owner");
        RequestPostProcessor other = userJwt("plan-switch-other");
        String firstPlanId = currentPlan(owner).at("/data/id").asText();
        String secondPlanId = objectMapper.readTree(mockMvc.perform(post("/api/v1/plans")
                        .with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Второй план\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).at("/data/id").asText();

        mockMvc.perform(put("/api/v1/plans/current")
                        .with(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"%s\"}".formatted(firstPlanId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstPlanId))
                .andExpect(jsonPath("$.data.current").value(true));

        mockMvc.perform(get("/api/v1/plans/current").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstPlanId));

        mockMvc.perform(get("/api/v1/plans").with(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(put("/api/v1/plans/current")
                        .with(other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":\"%s\"}".formatted(secondPlanId)))
                .andExpect(status().isForbidden());
    }

    private JsonNode currentPlan(RequestPostProcessor jwt) throws Exception {
        return readJson(mockMvc.perform(get("/api/v1/plans/current").with(jwt))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode readJson(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static RequestPostProcessor userJwt(String subject) {
        return jwt().jwt(token -> token
                .subject(subject)
                .audience(List.of("finguide-api"))
                .claim("email", subject + "@example.com")
                .claim("name", "Plan Owner")
                .claim("preferred_username", subject)
        );
    }
}
