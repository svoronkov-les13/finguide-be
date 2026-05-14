package les13.finguide.backend.scenarios;

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
import static org.hamcrest.Matchers.notNullValue;
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
class ScenarioControllerTests {
    private static final String ANONYMOUS_PLAN_ID = "22222222-2222-4222-8222-222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listsBuiltInsAndCreatesUpdatesReadsDeletesUserScenario() throws Exception {
        RequestPostProcessor jwt = userJwt("scenario-owner");

        mockMvc.perform(get("/api/v1/scenarios").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].id").value("base"))
                .andExpect(jsonPath("$.data[0].isBase").value(true))
                .andExpect(jsonPath("$.data[0].base").value(true));

        String createdBody = mockMvc.perform(post("/api/v1/scenarios")
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Aggressive savings",
                                  "emoji": "🚀",
                                  "description": "Higher income and lower expenses",
                                  "adjustments": {
                                    "incomeAdjPct": 10,
                                    "expenseAdjPct": -5,
                                    "returnAdjPct": 1,
                                    "inflationAdjPct": -1,
                                    "retirementAgeShift": -2,
                                    "goalsCostAdjPct": 0
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.name").value("Aggressive savings"))
                .andExpect(jsonPath("$.data.isBase").value(false))
                .andExpect(jsonPath("$.data.base").value(false))
                .andExpect(jsonPath("$.data.adjustments.incomeAdjPct").value(10))
                .andReturn().getResponse().getContentAsString();
        String scenarioId = objectMapper.readTree(createdBody).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/scenarios").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)));

        mockMvc.perform(get("/api/v1/scenarios/{scenarioId}", scenarioId).with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(scenarioId));

        mockMvc.perform(patch("/api/v1/scenarios/{scenarioId}", scenarioId)
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Balanced upside",
                                  "adjustments": {
                                    "returnAdjPct": 2,
                                    "retirementAgeShift": -1
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Balanced upside"))
                .andExpect(jsonPath("$.data.adjustments.incomeAdjPct").value(10))
                .andExpect(jsonPath("$.data.adjustments.returnAdjPct").value(2))
                .andExpect(jsonPath("$.data.adjustments.retirementAgeShift").value(-1));

        mockMvc.perform(delete("/api/v1/scenarios/{scenarioId}", scenarioId).with(jwt))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/scenarios/{scenarioId}", scenarioId).with(jwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void readsBuiltInsButRejectsBuiltInMutations() throws Exception {
        RequestPostProcessor jwt = userJwt("scenario-builtins");

        mockMvc.perform(get("/api/v1/scenarios/base").with(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("base"))
                .andExpect(jsonPath("$.data.isBase").value(true))
                .andExpect(jsonPath("$.data.base").value(true));

        mockMvc.perform(patch("/api/v1/scenarios/base")
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cannot edit\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/scenarios/base").with(jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidScenarioRequests() throws Exception {
        RequestPostProcessor jwt = userJwt("scenario-invalid");

        mockMvc.perform(post("/api/v1/scenarios")
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "adjustments": {
                                    "incomeAdjPct": 1001,
                                    "expenseAdjPct": 0,
                                    "returnAdjPct": 0,
                                    "inflationAdjPct": 0,
                                    "retirementAgeShift": 0,
                                    "goalsCostAdjPct": 0
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/scenarios/compare")
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenarioIds\":[\"base\",\"base\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMoreThanTenCustomScenarios() throws Exception {
        RequestPostProcessor jwt = userJwt("scenario-limit");

        for (int i = 1; i <= 10; i++) {
            createScenario(jwt, "Custom scenario " + i);
        }

        mockMvc.perform(post("/api/v1/scenarios")
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "One too many",
                                  "adjustments": {
                                    "incomeAdjPct": 0,
                                    "expenseAdjPct": 0,
                                    "returnAdjPct": 0,
                                    "inflationAdjPct": 0,
                                    "retirementAgeShift": 0,
                                    "goalsCostAdjPct": 0
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hidesForeignUserScenarios() throws Exception {
        RequestPostProcessor ownerJwt = userJwt("scenario-owner-a");
        RequestPostProcessor otherJwt = userJwt("scenario-owner-b");
        String scenarioId = createScenario(ownerJwt, "Owner A scenario");

        mockMvc.perform(get("/api/v1/scenarios/{scenarioId}", scenarioId).with(otherJwt))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/scenarios/{scenarioId}", scenarioId)
                        .with(otherJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"stolen\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/scenarios/{scenarioId}", scenarioId).with(otherJwt))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnonymousSeedWrites() throws Exception {
        mockMvc.perform(get("/api/v1/plans/{planId}/dashboard", ANONYMOUS_PLAN_ID))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Anonymous scenario",
                                  "adjustments": {
                                    "incomeAdjPct": 1,
                                    "expenseAdjPct": 0,
                                    "returnAdjPct": 0,
                                    "inflationAdjPct": 0,
                                    "retirementAgeShift": 0,
                                    "goalsCostAdjPct": 0
                                  }
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void comparesBuiltInAndUserScenariosDeterministically() throws Exception {
        RequestPostProcessor jwt = userJwt("scenario-compare");
        String scenarioId = createScenario(jwt, "Upside case");

        mockMvc.perform(post("/api/v1/scenarios/compare")
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scenarioIds": ["base", "%s"]
                                }
                                """.formatted(scenarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scenarios", hasSize(2)))
                .andExpect(jsonPath("$.data.scenarios[0].scenarioId").value("base"))
                .andExpect(jsonPath("$.data.scenarios[0].finalCapital").isNumber())
                .andExpect(jsonPath("$.data.scenarios[0].minCapital").isNumber())
                .andExpect(jsonPath("$.data.scenarios[0].retirementYear").isNumber())
                .andExpect(jsonPath("$.data.scenarios[0].capitalAtRetirement").isNumber())
                .andExpect(jsonPath("$.data.scenarios[0].goalCoveragePct").isNumber())
                .andExpect(jsonPath("$.data.scenarios[0].projection", hasSize(53)))
                .andExpect(jsonPath("$.data.scenarios[1].scenarioId").value(scenarioId));
    }

    private String createScenario(RequestPostProcessor jwt, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/scenarios")
                        .with(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "emoji": "📈",
                                  "adjustments": {
                                    "incomeAdjPct": 5,
                                    "expenseAdjPct": -2,
                                    "returnAdjPct": 1,
                                    "inflationAdjPct": 0,
                                    "retirementAgeShift": 0,
                                    "goalsCostAdjPct": 0
                                  }
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }

    private static RequestPostProcessor userJwt(String subject) {
        return jwt().jwt(token -> token
                .subject(subject)
                .audience(List.of("finguide-api"))
                .claim("email", subject + "@example.com")
                .claim("name", "Scenario Owner")
                .claim("preferred_username", subject)
        );
    }
}
