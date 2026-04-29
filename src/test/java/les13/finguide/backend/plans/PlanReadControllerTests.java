package les13.finguide.backend.plans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlanReadControllerTests {
    @Autowired
    private MockMvc mockMvc;

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
}
