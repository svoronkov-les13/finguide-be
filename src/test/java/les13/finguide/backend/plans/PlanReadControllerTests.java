package les13.finguide.backend.plans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlanReadControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsApiRootWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.swaggerUi").value("/finguide-api/swagger-ui.html"))
                .andExpect(jsonPath("$.data.endpoints.currentPlan").value("/api/v1/plans/current"));

        mockMvc.perform(get("/api/v1/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ok"));
    }

    @Test
    void returnsCurrentPlanEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/plans/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("22222222-2222-4222-8222-222222222222"))
                .andExpect(jsonPath("$.data.profile.name").value("Александр Петров"))
                .andExpect(jsonPath("$.data.incomes", hasSize(3)))
                .andExpect(jsonPath("$.data.expenses", hasSize(3)))
                .andExpect(jsonPath("$.data.goals", hasSize(3)))
                .andExpect(jsonPath("$.data.incomes[0].frequency").value("monthly"));
    }

    @Test
    void returnsDashboardHealthCashflowAndScenarios() throws Exception {
        String planId = "22222222-2222-4222-8222-222222222222";

        mockMvc.perform(get("/api/v1/plans/{planId}/dashboard", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalMonthlyIncome").value(345000))
                .andExpect(jsonPath("$.data.yearlyProjection", hasSize(4)));

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/health", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].status").value("good"));

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/cashflow", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(12)))
                .andExpect(jsonPath("$.data[0].capitalEndOfYear").exists());

        mockMvc.perform(get("/api/v1/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].id").value("base"));
    }

    @Test
    void returnsPersistedAnalyticsAndPensionProjection() throws Exception {
        String planId = "22222222-2222-4222-8222-222222222222";

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/assumptions", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startYear").value(2024))
                .andExpect(jsonPath("$.data.investmentReturnPct").value(6))
                .andExpect(jsonPath("$.data.inflationSchedule", hasSize(4)));

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/balance/current", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyIncome").value(345000))
                .andExpect(jsonPath("$.data.totalIncome").value(4320000))
                .andExpect(jsonPath("$.data.totalOutflow").value(1788000))
                .andExpect(jsonPath("$.data.netSavings").value(2532000));

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/projection?years=2", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].income").value(4320000))
                .andExpect(jsonPath("$.data[0].netSavings").value(2532000));

        mockMvc.perform(get("/api/v1/plans/{planId}/pension/projection", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAge").value(32))
                .andExpect(jsonPath("$.data.retirementAge").value(60))
                .andExpect(jsonPath("$.data.capitalAtRetirement", greaterThan(0.0)))
                .andExpect(jsonPath("$.data.preserveCapital.monthlySpendableCurrentPrices", greaterThan(0.0)))
                .andExpect(jsonPath("$.data.spendDown.series", hasSize(30)));
    }

    @Test
    void updatesPersistedAnalyticsAssumptionsForAuthenticatedPlan() throws Exception {
        String planId = currentPlanId("analytics-owner");

        mockMvc.perform(patch("/api/v1/plans/{planId}/analytics/assumptions", planId)
                        .with(jwt().jwt(token -> token.subject("analytics-owner")
                                .claim("email", "analytics-owner@example.com")
                                .claim("name", "Analytics Owner")
                                .claim("preferred_username", "analytics-owner")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assumptionsPatchJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startYear").value(2028))
                .andExpect(jsonPath("$.data.initialCapital").value(1000000))
                .andExpect(jsonPath("$.data.inflationSchedule", hasSize(2)));

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/assumptions", planId)
                        .with(jwt().jwt(token -> token.subject("analytics-owner")
                                .claim("email", "analytics-owner@example.com")
                                .claim("name", "Analytics Owner")
                                .claim("preferred_username", "analytics-owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startYear").value(2028))
                .andExpect(jsonPath("$.data.sourceModel").value("test override"));
    }

    @Test
    void rejectsAssumptionsUpdateForAnonymousDemoPlan() throws Exception {
        String planId = "22222222-2222-4222-8222-222222222222";

        mockMvc.perform(patch("/api/v1/plans/{planId}/analytics/assumptions", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assumptionsPatchJson()))
                .andExpect(status().isForbidden());
    }

    private static String assumptionsPatchJson() {
        return """
                {
                  "startYear": 2028,
                  "projectionEndYear": 2030,
                  "horizonYears": 3,
                  "birthYear": 1990,
                  "monthsPerYear": 12,
                  "currency": "RUB",
                  "initialCapital": 1000000,
                  "investmentReturnPct": 4,
                  "inflationSchedule": [
                    {"year": 2028, "ratePct": 4},
                    {"year": 2029, "ratePct": 4.5}
                  ],
                  "sourceModel": "test override"
                }
                """;
    }

    private String currentPlanId(String subject) throws Exception {
        String body = mockMvc.perform(get("/api/v1/plans/current")
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Analytics Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).at("/data/id").asText();
    }
}
