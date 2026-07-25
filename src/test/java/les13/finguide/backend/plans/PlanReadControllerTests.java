package les13.finguide.backend.plans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlanReadControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
                .andExpect(jsonPath("$.data.monthlyGoalContribution").value(211000))
                .andExpect(jsonPath("$.data.netMonthlyBalance").value(196000))
                .andExpect(jsonPath("$.data.netYearlyBalance").value(2532000))
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
    void dashboardMonthlyGoalContributionAveragesAllIncomeAndExpenses() throws Exception {
        String subject = "dashboard-annual-net-owner";
        String planId = currentPlanId(subject);

        mockMvc.perform(post("/api/v1/plans/{planId}/incomes", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Dashboard Annual Net Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Разовый бонус",
                                  "amount": 120000,
                                  "currency": "RUB",
                                  "frequency": "one_time",
                                  "growthType": "none",
                                  "growthPct": 0,
                                  "startDate": "2026-01-01"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/plans/{planId}/expenses", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Dashboard Annual Net Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Разовый ремонт",
                                  "amount": 60000,
                                  "currency": "RUB",
                                  "frequency": "one_time",
                                  "growthType": "none",
                                  "growthPct": 0,
                                  "growthLabel": "Без роста",
                                  "budgetClass": "needs",
                                  "startDate": "2026-01-01"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/plans/{planId}/dashboard", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Dashboard Annual Net Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.netYearlyBalance").value(2592000))
                .andExpect(jsonPath("$.data.monthlyGoalContribution").value(216000));
    }


    @Test
    void schedulesPlannedGoalSpendingInTargetYearAndSubtractsItFromCapital() throws Exception {
        String planId = "22222222-2222-4222-8222-222222222222";

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/cashflow", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].netSavings").value(2532000))
                .andExpect(jsonPath("$.data[0].totalGoalExpenses").value(0))
                .andExpect(jsonPath("$.data[0].capitalEndOfYear").value(5182000.0))
                .andExpect(jsonPath("$.data[1].monthlyIncome").value(370650.0))
                .andExpect(jsonPath("$.data[1].yearlyIncome").value(198000.0))
                .andExpect(jsonPath("$.data[1].monthlyExpenses").value(152820.0))
                .andExpect(jsonPath("$.data[1].netSavings").value(1206960.0))
                .andExpect(jsonPath("$.data[1].totalGoalExpenses").value(1605000.0));

        mockMvc.perform(get("/api/v1/plans/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.goals[0].projectedTargetCost").value(1605000.0))
                .andExpect(jsonPath("$.data.goals[0].projectedSavedAmount").value(1605000.0))
                .andExpect(jsonPath("$.data.goals[0].projectedProgressPct").value(100.0))
                .andExpect(jsonPath("$.data.goals[0].projectedReachable").value(true))
                .andExpect(jsonPath("$.data.goals[0].targetMonth").value(12))
                .andExpect(jsonPath("$.data.goals[0].projectedCompletionYear").value(2026))
                .andExpect(jsonPath("$.data.goals[1].projectedSavedAmount", greaterThan(0.0)));
    }

    @Test
    void goalReachabilityAllowsPositiveCapitalBeforeTargetMonth() throws Exception {
        String subject = "goal-month-deadline-owner";
        String planId = currentPlanId(subject);

        String createdBody = mockMvc.perform(post("/api/v1/plans/{planId}/goals", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Goal Month Deadline Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "June Target",
                                  "currentCost": 1300000,
                                  "currency": "RUB",
                                  "targetYear": 2026,
                                  "targetMonth": 6,
                                  "type": "one_time",
                                  "growthType": "none",
                                  "growthPct": 0,
                                  "priority": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String goalId = objectMapper.readTree(createdBody).at("/data/id").asText();

        String body = mockMvc.perform(get("/api/v1/plans/current")
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Goal Month Deadline Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode juneGoal = java.util.stream.StreamSupport.stream(objectMapper.readTree(body).at("/data/goals").spliterator(), false)
                .filter(goal -> goalId.equals(goal.get("id").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(juneGoal.get("targetMonth").asInt()).isEqualTo(6);
        assertThat(juneGoal.get("projectedTargetCost").decimalValue()).isEqualByComparingTo("1300000.00");
        assertThat(juneGoal.get("projectedReachable").asBoolean()).isTrue();
    }

    @Test
    void monthlyTrackerFactsReplacePlannedMonthlySavingsInCapitalProjection() throws Exception {
        String subject = "monthly-tracker-capital-owner";
        String planId = currentPlanId(subject);

        mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Monthly Tracker Capital Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "2026-01",
                                  "status": "partial",
                                  "amount": 5000,
                                  "note": "less than plan"
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/cashflow/monthly", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Monthly Tracker Capital Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].month").value("2026-01"))
                .andExpect(jsonPath("$.data[0].netSavings").value(5000))
                .andExpect(jsonPath("$.data[0].capitalEndOfMonth").value(2505000.0))
                .andExpect(jsonPath("$.data[11].month").value("2026-12"))
                .andExpect(jsonPath("$.data[11].capitalEndOfMonth").value(4991000.0));

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/cashflow", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Monthly Tracker Capital Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].netSavings").value(2341000.0))
                .andExpect(jsonPath("$.data[0].capitalEndOfYear").value(4991000.0));
    }

    @Test
    void goalReachabilityUsesPositiveTargetYearCapital() throws Exception {
        String subject = "goal-reachability-tracker-owner";
        String planId = currentPlanId(subject);

        for (int month = 1; month <= 12; month++) {
            mockMvc.perform(post("/api/v1/plans/{planId}/calendar/monthly-tracker", planId)
                            .with(jwt().jwt(token -> token.subject(subject)
                                    .claim("email", subject + "@example.com")
                                    .claim("name", "Goal Reachability Tracker Owner")
                                    .claim("preferred_username", subject)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "month": "2026-%02d",
                                      "status": "missed",
                                      "amount": 0,
                                      "note": "no savings"
                                    }
                                    """.formatted(month)))
                    .andExpect(status().isNoContent());
        }

        String createdBody = mockMvc.perform(post("/api/v1/plans/{planId}/goals", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Goal Reachability Tracker Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Tracker-sensitive target",
                                  "currentCost": 2000000,
                                  "currency": "RUB",
                                  "targetYear": 2026,
                                  "targetMonth": 12,
                                  "type": "one_time",
                                  "growthType": "none",
                                  "growthPct": 0,
                                  "priority": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String goalId = objectMapper.readTree(createdBody).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/cashflow", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Goal Reachability Tracker Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].capitalEndOfYear", greaterThan(0.0)));

        String body = mockMvc.perform(get("/api/v1/plans/current")
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Goal Reachability Tracker Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode goal = java.util.stream.StreamSupport.stream(objectMapper.readTree(body).at("/data/goals").spliterator(), false)
                .filter(node -> goalId.equals(node.get("id").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(goal.get("projectedReachable").asBoolean()).isTrue();
        assertThat(goal.get("projectedCompletionYear").asInt()).isEqualTo(2026);

        JsonNode negativeCapitalGoal = java.util.stream.StreamSupport.stream(objectMapper.readTree(body).at("/data/goals").spliterator(), false)
                .filter(node -> node.get("targetYear").asInt() == 2028)
                .findFirst()
                .orElseThrow();
        assertThat(negativeCapitalGoal.get("projectedReachable").asBoolean()).isFalse();
    }

    @Test
    void goalCanBeFundedAtTargetMonthEvenWhenYearEndCapitalTurnsNegative() throws Exception {
        String subject = "goal-funded-before-year-end-owner";
        String planId = currentPlanId(subject);

        String createdBody = mockMvc.perform(post("/api/v1/plans/{planId}/goals", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Goal Funded Before Year End Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "January purchase",
                                  "currentCost": 1000000,
                                  "currency": "RUB",
                                  "targetYear": 2026,
                                  "targetMonth": 1,
                                  "type": "one_time",
                                  "growthType": "none",
                                  "growthPct": 0,
                                  "priority": 1
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String goalId = objectMapper.readTree(createdBody).at("/data/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/expenses", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Goal Funded Before Year End Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Temporary burn",
                                  "amount": 500000,
                                  "currency": "RUB",
                                  "frequency": "monthly",
                                  "growthType": "none",
                                  "growthPct": 0,
                                  "growthLabel": "No growth",
                                  "budgetClass": "needs",
                                  "startDate": "2026-01-01"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/cashflow", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Goal Funded Before Year End Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].totalGoalExpenses", greaterThan(0.0)))
                .andExpect(jsonPath("$.data[0].capitalEndOfYear", lessThan(0.0)));

        String body = mockMvc.perform(get("/api/v1/plans/current")
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Goal Funded Before Year End Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode goal = java.util.stream.StreamSupport.stream(objectMapper.readTree(body).at("/data/goals").spliterator(), false)
                .filter(node -> goalId.equals(node.get("id").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(goal.get("projectedSavedAmount").decimalValue()).isEqualByComparingTo("1000000.00");
        assertThat(goal.get("projectedProgressPct").decimalValue()).isEqualByComparingTo("100.0");
        assertThat(goal.get("projectedReachable").asBoolean()).isTrue();
        assertThat(goal.get("projectedCompletionYear").asInt()).isEqualTo(2026);
    }

    @Test
    void contributionLedgerDoesNotReduceCurrentYearCapital() throws Exception {
        String subject = "actual-goal-outflow-owner";
        String planId = currentPlanId(subject);
        String currentPlan = mockMvc.perform(get("/api/v1/plans/current")
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Actual Goal Outflow Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String goalId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(currentPlan).at("/data/goals/0/id").asText();

        mockMvc.perform(post("/api/v1/plans/{planId}/contributions", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Actual Goal Outflow Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                {
                  "goalId": "%s",
                  "amount": 3000000,
                                  "currency": "RUB",
                                  "date": "2026-05-15",
                                  "note": "close goal"
                }
                """.formatted(goalId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/cashflow", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Actual Goal Outflow Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].totalGoalExpenses").value(0))
                .andExpect(jsonPath("$.data[0].netSavings").value(2532000))
                .andExpect(jsonPath("$.data[0].capitalEndOfYear").value(5182000.0));
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
                .andExpect(jsonPath("$.data.goalExpenses").value(0))
                .andExpect(jsonPath("$.data.totalOutflow").value(1788000))
                .andExpect(jsonPath("$.data.netSavings").value(2532000));

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/projection?years=2", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].income").value(4320000))
                .andExpect(jsonPath("$.data[0].goalsCost").value(0))
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

        mockMvc.perform(get("/api/v1/plans/{planId}/analytics/cashflow", planId)
                        .with(jwt().jwt(token -> token.subject("analytics-owner")
                                .claim("email", "analytics-owner@example.com")
                                .claim("name", "Analytics Owner")
                                .claim("preferred_username", "analytics-owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].capitalStartOfYear").value(1000000.0));
    }

    @Test
    void rejectsAssumptionsUpdateForAnonymousDemoPlan() throws Exception {
        String planId = "22222222-2222-4222-8222-222222222222";

        mockMvc.perform(patch("/api/v1/plans/{planId}/analytics/assumptions", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assumptionsPatchJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsPersistedPensionSettings() throws Exception {
        String planId = "22222222-2222-4222-8222-222222222222";

        mockMvc.perform(get("/api/v1/plans/{planId}/pension", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAge").value(32))
                .andExpect(jsonPath("$.data.retirementAge").value(60))
                .andExpect(jsonPath("$.data.monthlyExpenses").value(120000))
                .andExpect(jsonPath("$.data.desiredMonthlyExpensesCurrentPrices").value(120000))
                .andExpect(jsonPath("$.data.currency").value("RUB"))
                .andExpect(jsonPath("$.data.expectedReturnPct").value(9))
                .andExpect(jsonPath("$.data.inflationPct").value(7))
                .andExpect(jsonPath("$.data.withdrawalStrategy").value("spend_down_30y"))
                .andExpect(jsonPath("$.data.statePensionEnabled").value(true))
                .andExpect(jsonPath("$.data.statePensionMonthly").value(22000));
    }

    @Test
    void updatesPersistedPensionSettingsForAuthenticatedPlan() throws Exception {
        String subject = "pension-owner";
        String planId = currentPlanId(subject);

        mockMvc.perform(patch("/api/v1/plans/{planId}/pension", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Pension Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pensionPatchJson(25000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAge").value(45))
                .andExpect(jsonPath("$.data.retirementAge").value(67))
                .andExpect(jsonPath("$.data.expectedReturnPct").value(5))
                .andExpect(jsonPath("$.data.withdrawalStrategy").value("preserve_capital"))
                .andExpect(jsonPath("$.data.statePensionMonthly").value(25000));

        mockMvc.perform(get("/api/v1/plans/{planId}/pension", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Pension Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAge").value(45))
                .andExpect(jsonPath("$.data.retirementAge").value(67))
                .andExpect(jsonPath("$.data.statePensionMonthly").value(25000));
    }

    @Test
    void pensionProjectionUsesUpdatedSettings() throws Exception {
        String subject = "pension-projection-owner";
        String planId = currentPlanId(subject);

        mockMvc.perform(patch("/api/v1/plans/{planId}/pension", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Pension Projection Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pensionPatchJson(50000)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/plans/{planId}/pension/projection", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Pension Projection Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentAge").value(45))
                .andExpect(jsonPath("$.data.retirementAge").value(67))
                .andExpect(jsonPath("$.data.nominalReturnPct").value(5))
                .andExpect(jsonPath("$.data.spendDown.desiredMonthlyExpensesCurrentPrices").value(180000));
    }

    @Test
    void rejectsInvalidPensionSettings() throws Exception {
        String subject = "pension-invalid-owner";
        String planId = currentPlanId(subject);

        mockMvc.perform(patch("/api/v1/plans/{planId}/pension", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Pension Invalid Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentAge": 12,
                                  "retirementAge": 90,
                                  "monthlyExpenses": -1,
                                  "desiredMonthlyExpensesCurrentPrices": 180000,
                                  "currency": "RUB",
                                  "expectedReturnPct": 31,
                                  "inflationPct": 3,
                                  "withdrawalStrategy": "preserve_capital",
                                  "statePensionEnabled": true,
                                  "statePensionMonthly": 25000
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInvalidPensionCurrency() throws Exception {
        String subject = "pension-invalid-currency-owner";
        String planId = currentPlanId(subject);

        mockMvc.perform(patch("/api/v1/plans/{planId}/pension", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Pension Invalid Currency Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pensionPatchJson(25000).replace("\"currency\": \"RUB\"", "\"currency\": \"12!\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingWithdrawalStrategy() throws Exception {
        String subject = "pension-missing-strategy-owner";
        String planId = currentPlanId(subject);

        mockMvc.perform(patch("/api/v1/plans/{planId}/pension", planId)
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Pension Missing Strategy Owner")
                                .claim("preferred_username", subject)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pensionPatchJson(25000).replace("\"withdrawalStrategy\": \"preserve_capital\",", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPensionUpdateForAnonymousDemoPlan() throws Exception {
        String planId = "22222222-2222-4222-8222-222222222222";

        mockMvc.perform(patch("/api/v1/plans/{planId}/pension", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pensionPatchJson(25000)))
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

    private static String pensionPatchJson(int statePensionMonthly) {
        return """
                {
                  "currentAge": 45,
                  "retirementAge": 67,
                  "monthlyExpenses": 150000,
                  "desiredMonthlyExpensesCurrentPrices": 180000,
                  "currency": "RUB",
                  "expectedReturnPct": 5,
                  "inflationPct": 3,
                  "withdrawalStrategy": "preserve_capital",
                  "statePensionEnabled": true,
                  "statePensionMonthly": %d
                }
                """.formatted(statePensionMonthly);
    }

    private String currentPlanId(String subject) throws Exception {
        String body = mockMvc.perform(get("/api/v1/plans/current")
                        .with(jwt().jwt(token -> token.subject(subject)
                                .claim("email", subject + "@example.com")
                                .claim("name", "Analytics Owner")
                                .claim("preferred_username", subject))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/data/id").asText();
    }
}
