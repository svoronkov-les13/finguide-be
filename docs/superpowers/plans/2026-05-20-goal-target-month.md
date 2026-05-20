# Goal Target Month Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add month-level goal deadlines and use income-minus-expenses for the recommended monthly goal contribution.

**Architecture:** Extend the goal model/persistence/API with `targetMonth`, defaulting to December for compatibility. Update analytics ordering and allocation to compare goals by year-month and allocate cashflow monthly. Keep the public API backward compatible by preserving `targetYear`.

**Tech Stack:** Java 21, Spring Boot 3, H2 SQL schema/data, MockMvc/JUnit, Springdoc OpenAPI JSON contract.

---

### Task 1: Contract and persistence

**Files:**
- Modify: `src/test/java/les13/finguide/backend/plans/FinancialItemControllerTests.java`
- Modify: `src/main/java/les13/finguide/backend/goals/Goal.java`
- Modify: `src/main/java/les13/finguide/backend/plans/FinancialItemRequests.java`
- Modify: `src/main/java/les13/finguide/backend/plans/FinancialItemService.java`
- Modify: `src/main/java/les13/finguide/backend/plans/JdbcPlanStateRepository.java`
- Modify: `src/main/java/les13/finguide/backend/api/PlanApiMapper.java`
- Modify: `src/main/resources/schema.sql`
- Modify: `src/main/resources/data.sql`
- Modify: `openapi/openapi.json`

- [ ] Write failing tests for targetMonth create/default/patch/validation.
- [ ] Run `mvn -B -Dtest=FinancialItemControllerTests test` and confirm failure.
- [ ] Add `targetMonth` to model, SQL, mapper, request, validation.
- [ ] Run `mvn -B -Dtest=FinancialItemControllerTests test` and confirm pass.

### Task 2: Analytics behavior

**Files:**
- Modify: `src/test/java/les13/finguide/backend/plans/PlanReadControllerTests.java`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanReadService.java`
- Modify: `docs/model-analytics.md`

- [ ] Write failing analytics tests for monthly deadline ordering/reachability and monthly contribution free-cashflow.
- [ ] Run `mvn -B -Dtest=PlanReadControllerTests test` and confirm failure.
- [ ] Update analytics sorting and allocation to work by target year-month.
- [ ] Replace recommended monthly goal contribution with `max(monthlyIncome - monthlyExpenses, 0)`.
- [ ] Run `mvn -B -Dtest=PlanReadControllerTests test` and confirm pass.

### Task 3: Verification and integration

**Files:**
- Modify docs/contracts touched by generated Springdoc/OpenAPI expectations.

- [ ] Run `mvn -B test`.
- [ ] Run docs build if docs changed.
- [ ] Review diff for token leaks and unrelated changes.
- [ ] Commit implementation.
