# Scenario Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement persisted user scenario CRUD and deterministic scenario comparison for backend issue #13.

**Architecture:** Add a focused scenario module around the existing `scenarios/Scenario` domain record: repository persistence in `plans/JdbcPlanStateRepository`, orchestration/validation in `scenarios/ScenarioService`, HTTP endpoints in `scenarios/ScenarioController`, and response mapping in `api/PlanApiMapper`. Built-in scenarios stay generated/read-only; user scenarios are adjustment-only rows scoped to a plan; comparison applies adjustments in memory and reuses the existing projection calculator.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring MVC/MockMvc, Spring Security test JWT, Spring JDBC, H2, Maven, Springdoc OpenAPI, MkDocs.

---

## Files

- Create `src/test/java/les13/finguide/backend/scenarios/ScenarioControllerTests.java` — MockMvc TDD coverage for CRUD, compare, validation, and access.
- Create `src/main/java/les13/finguide/backend/scenarios/ScenarioRequests.java` — request records for create/patch/compare.
- Create `src/main/java/les13/finguide/backend/scenarios/ScenarioComparison.java` — comparison DTO/domain record.
- Create `src/main/java/les13/finguide/backend/scenarios/ScenarioService.java` — built-ins, validation, access checks, CRUD orchestration, compare logic.
- Create `src/main/java/les13/finguide/backend/scenarios/ScenarioController.java` — `/api/v1/scenarios` endpoints.
- Modify `src/main/java/les13/finguide/backend/api/PlanApiMapper.java` — scenario and comparison response mappers.
- Modify `src/main/java/les13/finguide/backend/plans/PlanReadController.java` — remove old `GET /scenarios` method after moving it to `ScenarioController`.
- Modify `src/main/java/les13/finguide/backend/plans/PlanStateRepository.java` — scenario persistence methods.
- Modify `src/main/java/les13/finguide/backend/plans/JdbcPlanStateRepository.java` — scenario CRUD and clone support.
- Modify `src/main/resources/schema.sql` — add `scenarios` table.
- Modify `src/test/java/les13/finguide/backend/openapi/OpenApiContractCoverageTests.java` — reduce known gap by five operations.
- Create or modify `src/test/java/les13/finguide/backend/scenarios/ScenarioOpenApiTests.java` — Springdoc smoke coverage for scenario paths.
- Modify docs: `README.md`, `docs/index.md`, `docs/status.md`, `docs/roadmap.md`, `docs/contract.md`, `docs/database.md`.

---

### Task 1: Write failing scenario controller tests

**Files:**
- Create: `src/test/java/les13/finguide/backend/scenarios/ScenarioControllerTests.java`

- [ ] **Step 1: Create test class with authenticated plan helper**

Add this full test file:

```java
package les13.finguide.backend.scenarios;

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
```

- [ ] **Step 2: Verify RED**

Run:

```bash
mvn -B -Dtest=ScenarioControllerTests test
```

Expected: compile failure because `ScenarioController` endpoints and scenario request classes do not exist yet.

---

### Task 2: Add schema and repository persistence

**Files:**
- Modify: `src/main/resources/schema.sql`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanStateRepository.java`
- Modify: `src/main/java/les13/finguide/backend/plans/JdbcPlanStateRepository.java`

- [ ] **Step 1: Add scenarios table**

In `schema.sql`, add this drop before `financial_plans`:

```sql
drop table if exists scenarios;
```

Add this table after `monthly_tracker_entries`:

```sql
create table scenarios (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  name varchar(120) not null,
  emoji varchar(16),
  description varchar(1024),
  is_base boolean not null default false,
  income_adj_pct numeric(9, 4) not null,
  expense_adj_pct numeric(9, 4) not null,
  return_adj_pct numeric(9, 4) not null,
  inflation_adj_pct numeric(9, 4) not null,
  retirement_age_shift integer not null,
  goals_cost_adj_pct numeric(9, 4) not null,
  snapshot_json clob,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);
```

- [ ] **Step 2: Extend repository interface**

In `PlanStateRepository`, add imports and methods:

```java
import les13.finguide.backend.scenarios.Scenario;
```

```java
List<Scenario> findScenarios(UUID planId);
Optional<Scenario> findScenario(UUID planId, UUID scenarioId);
Scenario createScenario(Scenario scenario);
Scenario updateScenario(Scenario scenario);
boolean deleteScenario(UUID planId, UUID scenarioId);
```

- [ ] **Step 3: Implement repository methods**

In `JdbcPlanStateRepository`, add scenario imports and methods:

```java
@Override
public List<Scenario> findScenarios(UUID planId) {
    return jdbcTemplate.query(
            "select * from scenarios where plan_id = ? order by created_at, name",
            this::mapScenario,
            planId
    );
}

@Override
public Optional<Scenario> findScenario(UUID planId, UUID scenarioId) {
    return queryOptional(
            "select * from scenarios where plan_id = ? and id = ?",
            this::mapScenario,
            planId,
            scenarioId
    );
}

@Override
public Scenario createScenario(Scenario scenario) {
    OffsetDateTime now = offset(scenario.createdAt());
    jdbcTemplate.update(
            "insert into scenarios (id, plan_id, name, emoji, description, is_base, income_adj_pct, expense_adj_pct, return_adj_pct, inflation_adj_pct, retirement_age_shift, goals_cost_adj_pct, snapshot_json, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            scenario.id(),
            scenario.basePlanId(),
            scenario.name(),
            scenario.emoji(),
            scenario.description(),
            scenario.base(),
            scenario.adjustments().incomeAdjPct(),
            scenario.adjustments().expenseAdjPct(),
            scenario.adjustments().returnAdjPct(),
            scenario.adjustments().inflationAdjPct(),
            scenario.adjustments().retirementAgeShift(),
            scenario.adjustments().goalsCostAdjPct(),
            null,
            now,
            now
    );
    touchPlan(scenario.basePlanId(), now);
    return findScenario(scenario.basePlanId(), scenario.id()).orElseThrow();
}

@Override
public Scenario updateScenario(Scenario scenario) {
    OffsetDateTime now = offset(scenario.updatedAt());
    jdbcTemplate.update(
            "update scenarios set name = ?, emoji = ?, description = ?, income_adj_pct = ?, expense_adj_pct = ?, return_adj_pct = ?, inflation_adj_pct = ?, retirement_age_shift = ?, goals_cost_adj_pct = ?, updated_at = ? where plan_id = ? and id = ?",
            scenario.name(),
            scenario.emoji(),
            scenario.description(),
            scenario.adjustments().incomeAdjPct(),
            scenario.adjustments().expenseAdjPct(),
            scenario.adjustments().returnAdjPct(),
            scenario.adjustments().inflationAdjPct(),
            scenario.adjustments().retirementAgeShift(),
            scenario.adjustments().goalsCostAdjPct(),
            now,
            scenario.basePlanId(),
            scenario.id()
    );
    touchPlan(scenario.basePlanId(), now);
    return findScenario(scenario.basePlanId(), scenario.id()).orElseThrow();
}

@Override
public boolean deleteScenario(UUID planId, UUID scenarioId) {
    int deleted = jdbcTemplate.update("delete from scenarios where plan_id = ? and id = ?", planId, scenarioId);
    if (deleted > 0) {
        touchPlan(planId, OffsetDateTime.now(ZoneOffset.UTC));
    }
    return deleted > 0;
}
```

Add mapper:

```java
private Scenario mapScenario(ResultSet rs, int rowNum) throws SQLException {
    return new Scenario(
            rs.getObject("id", UUID.class),
            rs.getObject("plan_id", UUID.class),
            rs.getString("name"),
            rs.getString("emoji"),
            rs.getString("description"),
            rs.getBoolean("is_base"),
            new Scenario.Adjustments(
                    rs.getBigDecimal("income_adj_pct"),
                    rs.getBigDecimal("expense_adj_pct"),
                    rs.getBigDecimal("return_adj_pct"),
                    rs.getBigDecimal("inflation_adj_pct"),
                    rs.getInt("retirement_age_shift"),
                    rs.getBigDecimal("goals_cost_adj_pct")
            ),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
    );
}
```

- [ ] **Step 4: Clone user scenarios when cloning seed plan**

In `createPlanForUserFromSeed`, call `cloneScenarios(seedPlanId, planId, now)` after plan child rows are cloned.

Add helper:

```java
private void cloneScenarios(UUID seedPlanId, UUID planId, OffsetDateTime now) {
    jdbcTemplate.update(
            "insert into scenarios (id, plan_id, name, emoji, description, is_base, income_adj_pct, expense_adj_pct, return_adj_pct, inflation_adj_pct, retirement_age_shift, goals_cost_adj_pct, snapshot_json, created_at, updated_at) " +
                    "select random_uuid(), ?, name, emoji, description, is_base, income_adj_pct, expense_adj_pct, return_adj_pct, inflation_adj_pct, retirement_age_shift, goals_cost_adj_pct, snapshot_json, ?, ? from scenarios where plan_id = ?",
            planId,
            now,
            now,
            seedPlanId
    );
}
```

- [ ] **Step 5: Run RED again**

Run:

```bash
mvn -B -Dtest=ScenarioControllerTests test
```

Expected: compile failure for missing service/controller/request classes, not schema errors.

---

### Task 3: Add service, controller, request records, and API mapping

**Files:**
- Create: `src/main/java/les13/finguide/backend/scenarios/ScenarioRequests.java`
- Create: `src/main/java/les13/finguide/backend/scenarios/ScenarioComparison.java`
- Create: `src/main/java/les13/finguide/backend/scenarios/ScenarioService.java`
- Create: `src/main/java/les13/finguide/backend/scenarios/ScenarioController.java`
- Modify: `src/main/java/les13/finguide/backend/api/PlanApiMapper.java`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanReadController.java`

- [ ] **Step 1: Add request records**

Create `ScenarioRequests.java`:

```java
package les13.finguide.backend.scenarios;

import java.math.BigDecimal;
import java.util.List;

public final class ScenarioRequests {
    private ScenarioRequests() {
    }

    public record ScenarioRequest(
            String name,
            String emoji,
            String description,
            AdjustmentsRequest adjustments
    ) {
    }

    public record AdjustmentsRequest(
            BigDecimal incomeAdjPct,
            BigDecimal expenseAdjPct,
            BigDecimal returnAdjPct,
            BigDecimal inflationAdjPct,
            Integer retirementAgeShift,
            BigDecimal goalsCostAdjPct
    ) {
    }

    public record CompareRequest(List<String> scenarioIds) {
    }
}
```

- [ ] **Step 2: Add comparison records**

Create `ScenarioComparison.java`:

```java
package les13.finguide.backend.scenarios;

import les13.finguide.backend.analytics.CashFlowProjectionPoint;

import java.math.BigDecimal;
import java.util.List;

public record ScenarioComparison(
        List<Result> scenarios
) {
    public record Result(
            String scenarioId,
            String name,
            BigDecimal finalCapital,
            BigDecimal minCapital,
            int retirementYear,
            BigDecimal capitalAtRetirement,
            BigDecimal goalCoveragePct,
            List<CashFlowProjectionPoint> projection
    ) {
    }
}
```

- [ ] **Step 3: Add mapper methods**

In `PlanApiMapper`, import `Scenario` and `ScenarioComparison`, then add:

```java
public Map<String, Object> scenario(Scenario scenario) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", scenario.base() ? scenario.name().toLowerCase(Locale.ROOT) : string(scenario.id()));
    result.put("basePlanId", string(scenario.basePlanId()));
    result.put("name", scenario.name());
    result.put("emoji", scenario.emoji());
    result.put("description", scenario.description());
    result.put("base", scenario.base());
    result.put("adjustments", scenarioAdjustments(scenario.adjustments()));
    result.put("createdAt", scenario.createdAt());
    result.put("updatedAt", scenario.updatedAt());
    return result;
}

public Map<String, Object> scenarioAdjustments(Scenario.Adjustments adjustments) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("incomeAdjPct", adjustments.incomeAdjPct());
    result.put("expenseAdjPct", adjustments.expenseAdjPct());
    result.put("returnAdjPct", adjustments.returnAdjPct());
    result.put("inflationAdjPct", adjustments.inflationAdjPct());
    result.put("retirementAgeShift", adjustments.retirementAgeShift());
    result.put("goalsCostAdjPct", adjustments.goalsCostAdjPct());
    return result;
}

public Map<String, Object> scenarioComparison(ScenarioComparison comparison) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("scenarios", comparison.scenarios().stream().map(this::scenarioComparisonResult).toList());
    return result;
}

private Map<String, Object> scenarioComparisonResult(ScenarioComparison.Result comparison) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("scenarioId", comparison.scenarioId());
    result.put("name", comparison.name());
    result.put("finalCapital", comparison.finalCapital());
    result.put("minCapital", comparison.minCapital());
    result.put("retirementYear", comparison.retirementYear());
    result.put("capitalAtRetirement", comparison.capitalAtRetirement());
    result.put("goalCoveragePct", comparison.goalCoveragePct());
    result.put("projection", comparison.projection().stream().map(this::cashflow).toList());
    return result;
}
```

After implementation, adjust built-in id mapping if needed by adding a stable `builtInKey(Scenario scenario)` helper instead of deriving from localized name. The preferred helper maps built-ins by exact name: `Базовый -> base`, `Оптимистичный -> optimistic`, `Пессимистичный -> pessimistic`.

- [ ] **Step 4: Add service**

Create `ScenarioService.java` with these responsibilities:

```java
package les13.finguide.backend.scenarios;

import les13.finguide.backend.analytics.CashFlowProjectionPoint;
import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.analytics.ProjectionCalculator;
import les13.finguide.backend.auth.PlanAccessService;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.plans.PlanReadService;
import les13.finguide.backend.plans.PlanState;
import les13.finguide.backend.plans.PlanStateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ScenarioService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_PCT = BigDecimal.valueOf(1000);
    private static final BigDecimal MIN_PCT = BigDecimal.valueOf(-100);
    private static final int MAX_SCENARIOS = 10;

    private final PlanReadService planReadService;
    private final PlanStateRepository repository;
    private final PlanAccessService accessService;
    private final ProjectionCalculator projectionCalculator;

    public ScenarioService(PlanReadService planReadService, PlanStateRepository repository, PlanAccessService accessService, ProjectionCalculator projectionCalculator) {
        this.planReadService = planReadService;
        this.repository = repository;
        this.accessService = accessService;
        this.projectionCalculator = projectionCalculator;
    }

    public List<Scenario> scenarios() {
        PlanState state = planReadService.currentPlan();
        List<Scenario> result = new ArrayList<>(builtIns(state.plan().id()));
        result.addAll(repository.findScenarios(state.plan().id()));
        return result;
    }

    public Scenario scenario(String scenarioId) {
        UUID planId = planReadService.currentPlan().plan().id();
        return resolveScenario(planId, scenarioId).orElseThrow(() -> notFound("Scenario was not found"));
    }

    @Transactional
    public Scenario createScenario(ScenarioRequests.ScenarioRequest request) {
        PlanState state = planReadService.currentPlan();
        UUID planId = state.plan().id();
        accessService.requireWritablePlan(planId);
        Instant now = Instant.now();
        Scenario scenario = new Scenario(
                UUID.randomUUID(),
                planId,
                requiredText(request.name(), "name", 120),
                optionalText(request.emoji(), "emoji", 16),
                optionalText(request.description(), "description", 1024),
                false,
                adjustments(request.adjustments(), null),
                now,
                now
        );
        return repository.createScenario(scenario);
    }

    @Transactional
    public Scenario updateScenario(String scenarioId, ScenarioRequests.ScenarioRequest request) {
        UUID planId = planReadService.currentPlan().plan().id();
        if (builtIn(scenarioId).isPresent()) {
            throw forbidden("Built-in scenarios are read-only");
        }
        UUID id = parseUuid(scenarioId);
        Scenario current = repository.findScenario(planId, id).orElseThrow(() -> notFound("Scenario was not found"));
        accessService.requireWritablePlan(current.basePlanId());
        Scenario updated = new Scenario(
                current.id(),
                current.basePlanId(),
                request.name() == null ? current.name() : requiredText(request.name(), "name", 120),
                request.emoji() == null ? current.emoji() : optionalText(request.emoji(), "emoji", 16),
                request.description() == null ? current.description() : optionalText(request.description(), "description", 1024),
                false,
                adjustments(request.adjustments(), current.adjustments()),
                current.createdAt(),
                Instant.now()
        );
        return repository.updateScenario(updated);
    }

    @Transactional
    public void deleteScenario(String scenarioId) {
        UUID planId = planReadService.currentPlan().plan().id();
        if (builtIn(scenarioId).isPresent()) {
            throw forbidden("Built-in scenarios are read-only");
        }
        UUID id = parseUuid(scenarioId);
        Scenario current = repository.findScenario(planId, id).orElseThrow(() -> notFound("Scenario was not found"));
        accessService.requireWritablePlan(current.basePlanId());
        if (!repository.deleteScenario(current.basePlanId(), current.id())) {
            throw notFound("Scenario was not found");
        }
    }

    public ScenarioComparison compare(ScenarioRequests.CompareRequest request) {
        PlanState state = planReadService.currentPlan();
        UUID planId = state.plan().id();
        List<String> scenarioIds = request.scenarioIds() == null ? List.of() : request.scenarioIds();
        if (scenarioIds.isEmpty() || scenarioIds.size() > MAX_SCENARIOS) {
            throw badRequest("scenarioIds must contain 1..10 items");
        }
        Set<String> seen = new HashSet<>();
        List<ScenarioComparison.Result> results = new ArrayList<>();
        for (String scenarioId : scenarioIds) {
            if (scenarioId == null || scenarioId.isBlank() || !seen.add(scenarioId)) {
                throw badRequest("scenarioIds must be unique and non-blank");
            }
            Scenario scenario = resolveScenario(planId, scenarioId).orElseThrow(() -> notFound("Scenario was not found"));
            results.add(compareOne(state, scenario, stableScenarioId(scenario)));
        }
        return new ScenarioComparison(results);
    }

    private ScenarioComparison.Result compareOne(PlanState state, Scenario scenario, String scenarioId) {
        PlanState adjusted = adjustedState(state, scenario.adjustments());
        List<CashFlowProjectionPoint> projection = projectionCalculator.project(adjusted);
        BigDecimal finalCapital = projection.isEmpty() ? ZERO : projection.get(projection.size() - 1).capitalEndOfYear();
        BigDecimal minCapital = projection.stream().map(CashFlowProjectionPoint::capitalEndOfYear).min(BigDecimal::compareTo).orElse(ZERO);
        int retirementYear = adjusted.modelAssumptions().startYear() + Math.max(0, adjusted.pension().retirementAge() - adjusted.pension().currentAge());
        BigDecimal capitalAtRetirement = projection.stream()
                .filter(point -> point.year() >= retirementYear)
                .findFirst()
                .orElse(projection.isEmpty() ? null : projection.get(projection.size() - 1))
                .capitalEndOfYear();
        BigDecimal totalGoalCost = adjusted.goals().stream().map(Goal::currentCost).reduce(ZERO, BigDecimal::add);
        BigDecimal goalCoveragePct = totalGoalCost.compareTo(ZERO) == 0
                ? HUNDRED
                : finalCapital.max(ZERO).multiply(HUNDRED).divide(totalGoalCost, 2, RoundingMode.HALF_UP).min(HUNDRED);
        return new ScenarioComparison.Result(scenarioId, scenario.name(), finalCapital, minCapital, retirementYear, capitalAtRetirement, goalCoveragePct, projection);
    }

    private PlanState adjustedState(PlanState state, Scenario.Adjustments adjustments) {
        BigDecimal incomeFactor = factor(adjustments.incomeAdjPct());
        BigDecimal expenseFactor = factor(adjustments.expenseAdjPct());
        BigDecimal goalFactor = factor(adjustments.goalsCostAdjPct());
        ModelAssumptions assumptions = state.modelAssumptions();
        ModelAssumptions adjustedAssumptions = new ModelAssumptions(
                assumptions.startYear(),
                assumptions.projectionEndYear(),
                assumptions.horizonYears(),
                assumptions.birthYear(),
                assumptions.monthsPerYear(),
                assumptions.currency(),
                assumptions.initialCapital(),
                assumptions.investmentReturnPct().add(adjustments.returnAdjPct()),
                assumptions.inflationSchedule().stream()
                        .map(point -> new les13.finguide.backend.analytics.YearRatePoint(point.year(), point.ratePct().add(adjustments.inflationAdjPct())))
                        .toList(),
                assumptions.sourceModel()
        );
        PensionSettings pension = state.pension();
        PensionSettings adjustedPension = new PensionSettings(
                pension.currentAge(),
                pension.retirementAge() + adjustments.retirementAgeShift(),
                pension.monthlyExpenses().multiply(expenseFactor),
                pension.desiredMonthlyExpensesCurrentPrices().multiply(expenseFactor),
                pension.currency(),
                pension.expectedReturnPct().add(adjustments.returnAdjPct()),
                pension.inflationPct().add(adjustments.inflationAdjPct()),
                pension.withdrawalStrategy(),
                pension.statePensionEnabled(),
                pension.statePensionMonthly()
        );
        return new PlanState(
                state.plan(),
                state.profile(),
                adjustedPension,
                state.incomes().stream().map(income -> new IncomeSource(income.id(), income.planId(), income.name(), income.amount().multiply(incomeFactor), income.currency(), income.frequency(), income.growthType(), income.growthPct().add(adjustments.incomeAdjPct()), income.startDate(), income.endDate(), income.createdAt(), income.updatedAt())).toList(),
                state.expenses().stream().map(expense -> new ExpenseItem(expense.id(), expense.planId(), expense.name(), expense.amount().multiply(expenseFactor), expense.currency(), expense.frequency(), expense.growthType(), expense.growthPct().add(adjustments.expenseAdjPct()), expense.growthLabel(), expense.budgetClass(), expense.startDate(), expense.endDate(), expense.createdAt(), expense.updatedAt())).toList(),
                state.goals().stream().map(goal -> new Goal(goal.id(), goal.planId(), goal.name(), goal.icon(), goal.currentCost().multiply(goalFactor), goal.savedAmount(), goal.currency(), goal.targetYear(), goal.type(), goal.growthType(), goal.growthPct().add(adjustments.goalsCostAdjPct()), goal.indexLabel(), goal.priority(), goal.createdAt(), goal.updatedAt())).toList(),
                state.contributions(),
                state.budget(),
                adjustedAssumptions,
                state.updatedAt()
        );
    }

    private Optional<Scenario> resolveScenario(UUID planId, String scenarioId) {
        Optional<Scenario> builtIn = builtIn(scenarioId);
        if (builtIn.isPresent()) {
            Scenario scenario = builtIn.get();
            return Optional.of(new Scenario(scenario.id(), planId, scenario.name(), scenario.emoji(), scenario.description(), true, scenario.adjustments(), scenario.createdAt(), scenario.updatedAt()));
        }
        return repository.findScenario(planId, parseUuid(scenarioId));
    }

    private List<Scenario> builtIns(UUID planId) {
        Instant now = Instant.EPOCH;
        return List.of(
                new Scenario(null, planId, "Базовый", "📊", "Текущий план без изменений", true, zeroAdjustments(), now, now),
                new Scenario(null, planId, "Оптимистичный", "🚀", "Рост доходов и доходности выше базового", true, new Scenario.Adjustments(BigDecimal.valueOf(10), BigDecimal.valueOf(-5), BigDecimal.ONE, BigDecimal.valueOf(-1), -2, ZERO), now, now),
                new Scenario(null, planId, "Пессимистичный", "⚠️", "Стресс-сценарий с давлением на расходы и доходность", true, new Scenario.Adjustments(BigDecimal.valueOf(-10), BigDecimal.valueOf(10), BigDecimal.valueOf(-2), BigDecimal.valueOf(2), 3, BigDecimal.valueOf(10)), now, now)
        );
    }

    private Optional<Scenario> builtIn(String scenarioId) {
        UUID planId = planReadService.currentPlan().plan().id();
        return builtIns(planId).stream().filter(scenario -> stableScenarioId(scenario).equals(scenarioId)).findFirst();
    }

    private String stableScenarioId(Scenario scenario) {
        if (!scenario.base()) return scenario.id().toString();
        return switch (scenario.name()) {
            case "Базовый" -> "base";
            case "Оптимистичный" -> "optimistic";
            case "Пессимистичный" -> "pessimistic";
            default -> scenario.name().toLowerCase();
        };
    }

    private Scenario.Adjustments adjustments(ScenarioRequests.AdjustmentsRequest request, Scenario.Adjustments fallback) {
        Scenario.Adjustments base = fallback == null ? zeroAdjustments() : fallback;
        if (request == null) return base;
        return new Scenario.Adjustments(
                pct(request.incomeAdjPct(), base.incomeAdjPct(), "incomeAdjPct"),
                pct(request.expenseAdjPct(), base.expenseAdjPct(), "expenseAdjPct"),
                pct(request.returnAdjPct(), base.returnAdjPct(), "returnAdjPct"),
                pct(request.inflationAdjPct(), base.inflationAdjPct(), "inflationAdjPct"),
                shift(request.retirementAgeShift(), base.retirementAgeShift()),
                pct(request.goalsCostAdjPct(), base.goalsCostAdjPct(), "goalsCostAdjPct")
        );
    }

    private Scenario.Adjustments zeroAdjustments() {
        return new Scenario.Adjustments(ZERO, ZERO, ZERO, ZERO, 0, ZERO);
    }

    private BigDecimal pct(BigDecimal value, BigDecimal fallback, String field) {
        BigDecimal actual = value == null ? fallback : value;
        if (actual.compareTo(MIN_PCT) < 0 || actual.compareTo(MAX_PCT) > 0) throw badRequest(field + " is out of range");
        return actual;
    }

    private int shift(Integer value, int fallback) {
        int actual = value == null ? fallback : value;
        if (actual < -20 || actual > 20) throw badRequest("retirementAgeShift is out of range");
        return actual;
    }

    private BigDecimal factor(BigDecimal pct) {
        return BigDecimal.ONE.add(pct.divide(HUNDRED, 8, RoundingMode.HALF_UP));
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            throw notFound("Scenario was not found");
        }
    }

    private String requiredText(String value, String field, int maxLength) {
        String text = optionalText(value, field, maxLength);
        if (text == null || text.isBlank()) throw badRequest(field + " is required");
        return text;
    }

    private String optionalText(String value, String field, int maxLength) {
        if (value == null) return null;
        if (value.length() > maxLength) throw badRequest(field + " is too long");
        return value;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
```

If compilation shows constructor mismatches for `PlanState`, `IncomeSource`, `ExpenseItem`, or `Goal`, inspect the current records and adjust the constructor calls to match exact field order while preserving the same adjusted values.

- [ ] **Step 5: Add controller**

Create `ScenarioController.java`:

```java
package les13.finguide.backend.scenarios;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import les13.finguide.backend.api.ApiEnvelope;
import les13.finguide.backend.api.PlanApiMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {
    private final ScenarioService service;
    private final PlanApiMapper mapper;

    public ScenarioController(ScenarioService service, PlanApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public ApiEnvelope<Object> scenarios() {
        return ApiEnvelope.of(service.scenarios().stream().map(mapper::scenario).toList());
    }

    @PostMapping
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> createScenario(@RequestBody ScenarioRequests.ScenarioRequest request) {
        Scenario scenario = service.createScenario(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(scenario.id());
        return ResponseEntity.created(location).body(ApiEnvelope.of(mapper.scenario(scenario)));
    }

    @GetMapping("/{scenarioId}")
    public ApiEnvelope<Map<String, Object>> scenario(@PathVariable String scenarioId) {
        return ApiEnvelope.of(mapper.scenario(service.scenario(scenarioId)));
    }

    @PatchMapping("/{scenarioId}")
    public ApiEnvelope<Map<String, Object>> updateScenario(@PathVariable String scenarioId, @RequestBody ScenarioRequests.ScenarioRequest request) {
        return ApiEnvelope.of(mapper.scenario(service.updateScenario(scenarioId, request)));
    }

    @DeleteMapping("/{scenarioId}")
    public ResponseEntity<Void> deleteScenario(@PathVariable String scenarioId) {
        service.deleteScenario(scenarioId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/compare")
    public ApiEnvelope<Map<String, Object>> compare(@RequestBody ScenarioRequests.CompareRequest request) {
        return ApiEnvelope.of(mapper.scenarioComparison(service.compare(request)));
    }
}
```

- [ ] **Step 6: Remove old scenario list endpoint from PlanReadController**

Delete the old method from `PlanReadController`:

```java
@GetMapping("/scenarios")
public ApiEnvelope<Object> scenarios() {
    UUID currentPlanId = planReadService.currentPlan().plan().id();
    return ApiEnvelope.of(planReadService.scenarios(currentPlanId));
}
```

Also remove now-unused `java.util.UUID` import from `PlanReadController` if compilation flags it.

- [ ] **Step 7: Verify GREEN for scenario tests**

Run:

```bash
mvn -B -Dtest=ScenarioControllerTests test
```

Expected: all scenario tests pass.

---

### Task 4: OpenAPI coverage and documentation

**Files:**
- Modify: `src/test/java/les13/finguide/backend/openapi/OpenApiContractCoverageTests.java`
- Create: `src/test/java/les13/finguide/backend/scenarios/ScenarioOpenApiTests.java`
- Modify docs listed in Files section.

- [ ] **Step 1: Remove scenario operations from known OpenAPI gap**

In `OpenApiContractCoverageTests.KNOWN_MISSING_OPERATIONS`, remove exactly:

```txt
DELETE /api/v1/scenarios/{scenarioId}
GET /api/v1/scenarios/{scenarioId}
PATCH /api/v1/scenarios/{scenarioId}
POST /api/v1/scenarios
POST /api/v1/scenarios/compare
```

Expected known gap becomes 9.

- [ ] **Step 2: Add OpenAPI smoke test**

Create `ScenarioOpenApiTests.java`:

```java
package les13.finguide.backend.scenarios;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScenarioOpenApiTests {
    private static final Map<String, Set<String>> EXPECTED_METHODS = Map.of(
            "/api/v1/scenarios", Set.of("get", "post"),
            "/api/v1/scenarios/{scenarioId}", Set.of("get", "patch", "delete"),
            "/api/v1/scenarios/compare", Set.of("post")
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesScenarioEndpointsInOpenApi() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode api = objectMapper.readTree(body);

        EXPECTED_METHODS.forEach((path, methods) -> methods.forEach(method ->
                assertThat(api.at("/paths/" + path.replace("/", "~1") + "/" + method).isMissingNode())
                        .as("%s %s", method.toUpperCase(), path)
                        .isFalse()
        ));
        assertThat(api.at("/paths/~1api~1v1~1scenarios/post/responses/201").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1api~1v1~1scenarios~1compare/post/requestBody/content/application~1json/schema/$ref").asText())
                .contains("CompareRequest");
    }
}
```

- [ ] **Step 3: Update docs**

Update docs with concrete statements:

- `README.md`: add scenario CRUD/compare to implemented endpoint list.
- `docs/index.md`: add #13 completion bullet after implementation.
- `docs/status.md`: set OpenAPI real coverage to 45 implemented / gap 9 and mark #13 done.
- `docs/roadmap.md`: move #13 from Then to done list.
- `docs/contract.md`: say real backend now covers scenario CRUD/compare and OpenAPI gap 9.
- `docs/database.md`: add `scenarios` table and note snapshot_json is reserved for future scenario snapshots.

- [ ] **Step 4: Run focused checks**

Run:

```bash
mvn -B -Dtest=ScenarioControllerTests,ScenarioOpenApiTests,OpenApiContractCoverageTests test
```

Expected: all tests pass.

---

### Task 5: Full verification, review, PR, merge

**Files:** all changed files.

- [ ] **Step 1: Full tests**

Run:

```bash
mvn -B test
```

Expected: all tests pass.

- [ ] **Step 2: Docs build**

Run:

```bash
/tmp/finguide-docs-venv/bin/mkdocs build --strict
```

If missing:

```bash
python3 -m venv /tmp/finguide-docs-venv
/tmp/finguide-docs-venv/bin/pip install -q -r requirements-docs.txt
/tmp/finguide-docs-venv/bin/mkdocs build --strict
```

Expected: docs build succeeds.

- [ ] **Step 3: Secret scan**

Run:

```bash
grep -RE "github_pat_[A-Za-z0-9_]{20,}" -n --exclude-dir=.git --exclude-dir=target --exclude-dir=site . || true
```

Expected: no leaked token in tracked files. A test string in a security verification script is acceptable only if it is not the real token.

- [ ] **Step 4: Commit implementation**

Run:

```bash
git add README.md docs src/main src/test
git commit -m "feat: implement scenario management"
```

- [ ] **Step 5: Request code review**

Dispatch a code-review subagent for `origin/main..HEAD` checking:

- access control for scenario ownership and built-in read-only behavior;
- adjustment math and projection reuse;
- H2 schema/repository consistency;
- OpenAPI gap and docs;
- validation and deterministic compare responses.

- [ ] **Step 6: Fix review findings**

Fix all Critical/Important findings with regression tests. Rerun focused tests and `mvn -B test`.

- [ ] **Step 7: Push and create PR**

Run:

```bash
git push -u origin issue-13-scenarios
```

Create PR:

- Title: `feat: implement scenario management`
- Body includes summary, verification, and `Closes #13`.

- [ ] **Step 8: Merge after checks**

After PR is clean and checks are green, merge with squash or regular merge consistent with current repo practice.

- [ ] **Step 9: Post-merge verification and cleanup**

On `main`, run:

```bash
git fetch origin main
git checkout main
git pull --ff-only origin main
mvn -B test
/tmp/finguide-docs-venv/bin/mkdocs build --strict
```

Confirm GitHub Actions backend/docs deploy success, then clean:

```bash
git branch -d issue-13-scenarios || true
git push origin --delete issue-13-scenarios || true
rm -f /tmp/openclaw-gh-token
```
