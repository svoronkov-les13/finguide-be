# Budget and Monthly Tracker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement persisted budget settings/envelopes and simple monthly tracker endpoints for backend issue #12.

**Architecture:** Add focused budget/tracker request and service classes under `plans`, extend `PlanStateRepository`/`JdbcPlanStateRepository` for H2 persistence, and expose endpoints from the existing plan-scoped controller. Keep computation small and deterministic: generated envelopes mirror expense categories; monthly tracker stores explicit month statuses only.

**Tech Stack:** Java 21, Spring Boot 3.3, Spring MVC/MockMvc, Spring Security test JWT, Spring JDBC, H2, Maven, MkDocs.

---

## Files

- Create `src/test/java/les13/finguide/backend/plans/BudgetTrackerControllerTests.java` — MockMvc TDD coverage for #12.
- Create `src/main/java/les13/finguide/backend/plans/BudgetTrackerRequests.java` — request records for budget patch and tracker upsert.
- Create `src/main/java/les13/finguide/backend/plans/BudgetTrackerService.java` — access checks, validation, orchestration.
- Create `src/main/java/les13/finguide/backend/budget/MonthlyTrackerEntry.java` — monthly tracker domain record.
- Modify `src/main/java/les13/finguide/backend/plans/PlanStateRepository.java` — budget/tracker persistence contract.
- Modify `src/main/java/les13/finguide/backend/plans/JdbcPlanStateRepository.java` — H2 CRUD and clone behavior.
- Modify `src/main/java/les13/finguide/backend/plans/FinancialItemController.java` — add five endpoints.
- Modify `src/main/java/les13/finguide/backend/api/PlanApiMapper.java` — add monthly tracker mapper and keep budget mapper.
- Modify `src/main/resources/schema.sql` — add `budget_settings`, `budget_envelopes`, `budget_classifications`, `monthly_tracker_entries`.
- Modify `src/test/java/les13/finguide/backend/openapi/OpenApiContractCoverageTests.java` — remove #12 endpoints from known gap.
- Modify `src/test/java/les13/finguide/backend/plans/FinancialItemOpenApiTests.java` — assert budget/tracker endpoints appear.
- Modify docs: `README.md`, `docs/status.md`, `docs/roadmap.md`, `docs/contract.md`, `docs/database.md`, `docs/index.md`.

---

### Task 1: Write failing integration tests

**Files:**
- Create: `src/test/java/les13/finguide/backend/plans/BudgetTrackerControllerTests.java`

- [ ] **Step 1: Add MockMvc tests**

Create `BudgetTrackerControllerTests` with tests for:

1. `getsPatchesAndAutogeneratesBudgetForAuthenticatedPlan`
   - GET default budget returns method `503020`, empty envelopes.
   - PATCH method `envelope`, one manual envelope, classifications for an existing expense id.
   - GET returns persisted settings.
   - POST autogenerate returns three envelopes from seeded expense categories.
2. `rejectsInvalidBudgetRequest`
   - negative envelope limit or invalid classification id returns `400`.
3. `upsertsAndListsMonthlyTrackerEntries`
   - POST `2026-05 completed`, GET year returns one row, POST same month `partial`, GET returns updated row.
4. `rejectsInvalidMonthlyTrackerRequest`
   - bad month or status returns `400`.
5. `rejectsBudgetAndTrackerWritesForAnonymousSeed`
   - PATCH budget, autogenerate, POST tracker return `403` anonymously.
6. `rejectsBudgetReadForAnotherUsersPlan`
   - authenticated user B cannot read user A plan.

Use the existing JWT helper style from `ContributionControllerTests`.

- [ ] **Step 2: Verify RED**

Run:

```bash
mvn -B -Dtest=BudgetTrackerControllerTests test
```

Expected: compile failure or 404 failures because endpoints/classes are not implemented yet.

---

### Task 2: Add schema and repository methods

**Files:**
- Modify: `src/main/resources/schema.sql`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanStateRepository.java`
- Modify: `src/main/java/les13/finguide/backend/plans/JdbcPlanStateRepository.java`
- Create: `src/main/java/les13/finguide/backend/budget/MonthlyTrackerEntry.java`

- [ ] **Step 1: Add domain record**

Create `MonthlyTrackerEntry`:

```java
package les13.finguide.backend.budget;

import java.time.Instant;
import java.time.YearMonth;

public record MonthlyTrackerEntry(
        YearMonth month,
        Status status,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Status {
        COMPLETED,
        PARTIAL,
        MISSED
    }
}
```

- [ ] **Step 2: Add tables**

In `schema.sql`, add drops before `financial_plans` dependencies and tables after `contributions`:

```sql
drop table if exists monthly_tracker_entries;
drop table if exists budget_classifications;
drop table if exists budget_envelopes;
drop table if exists budget_settings;
```

```sql
create table budget_settings (
  plan_id uuid primary key references financial_plans(id),
  method varchar(32) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create table budget_envelopes (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  name varchar(255) not null,
  limit_amount numeric(19, 2) not null,
  icon varchar(64) not null,
  color varchar(32) not null,
  sort_order integer not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);

create table budget_classifications (
  plan_id uuid not null references financial_plans(id),
  expense_id uuid not null references expenses(id),
  budget_class varchar(32) not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  primary key (plan_id, expense_id)
);

create table monthly_tracker_entries (
  plan_id uuid not null references financial_plans(id),
  month varchar(7) not null,
  status varchar(32) not null,
  note varchar(1024),
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  primary key (plan_id, month)
);
```

- [ ] **Step 3: Extend repository interface**

Add methods:

```java
BudgetSettings findBudget(UUID planId);
BudgetSettings replaceBudget(UUID planId, BudgetSettings budget);
BudgetSettings replaceBudgetEnvelopes(UUID planId, List<BudgetEnvelope> envelopes);
List<MonthlyTrackerEntry> findMonthlyTrackerEntries(UUID planId, int year);
MonthlyTrackerEntry upsertMonthlyTrackerEntry(UUID planId, MonthlyTrackerEntry entry);
```

- [ ] **Step 4: Implement H2 repository methods**

Implement default budget when no `budget_settings` row exists. Implement full replace by deleting classifications/envelopes/settings then inserting current settings in one transaction. Compute `spent/remaining/pct/isOver` while mapping envelopes using current monthly expenses matched by envelope name.

- [ ] **Step 5: Clone budget/tracker for authenticated plan clone**

In `createPlanForUserFromSeed`, clone budget rows and monthly tracker entries from seed if present. Because seed has none now, this is mainly future-safe.

- [ ] **Step 6: Run tests**

Run:

```bash
mvn -B -Dtest=BudgetTrackerControllerTests test
```

Expected: still failing with missing endpoints/service, not schema errors.

---

### Task 3: Add service, requests, mapper, controller endpoints

**Files:**
- Create: `src/main/java/les13/finguide/backend/plans/BudgetTrackerRequests.java`
- Create: `src/main/java/les13/finguide/backend/plans/BudgetTrackerService.java`
- Modify: `src/main/java/les13/finguide/backend/api/PlanApiMapper.java`
- Modify: `src/main/java/les13/finguide/backend/plans/FinancialItemController.java`

- [ ] **Step 1: Add request records**

Create `BudgetTrackerRequests` with:

```java
public record BudgetRequest(String method, List<EnvelopeRequest> envelopes, Map<UUID, String> classifications) {}
public record EnvelopeRequest(UUID id, String name, BigDecimal limit, String icon, String color) {}
public record MonthlyTrackerRequest(String month, String status, String note) {}
```

- [ ] **Step 2: Add service methods**

Implement:

```java
BudgetSettings budget(UUID planId)
BudgetSettings updateBudget(UUID planId, BudgetRequest request)
BudgetSettings autogenerateEnvelopes(UUID planId)
List<MonthlyTrackerEntry> monthlyTracker(UUID planId, Integer year)
void upsertMonthlyTracker(UUID planId, MonthlyTrackerRequest request)
```

Validation details:

- `method`: `503020` maps to `RULE_50_30_20`, `envelope` maps to `ENVELOPE`.
- Envelope `name`, `icon`, `color` required; `limit >= 0`.
- Classification expense id must exist in `repository.findExpense(planId, expenseId)`.
- Month parse: regex `^[0-9]{4}-[0-9]{2}$`, then `YearMonth.parse` and reject invalid month.
- Status accepts `completed`, `partial`, `missed`.

- [ ] **Step 3: Add monthly tracker mapper**

In `PlanApiMapper`, add:

```java
public Map<String, Object> monthlyTrackerEntry(MonthlyTrackerEntry entry)
```

Return `month`, lowercase `status`, `note`, `updatedAt`.

- [ ] **Step 4: Add controller endpoints**

In `FinancialItemController`, inject `BudgetTrackerService` and add mappings:

```java
@GetMapping("/budget")
@PatchMapping("/budget")
@PostMapping("/budget/envelopes/autogenerate")
@GetMapping("/calendar/monthly-tracker")
@PostMapping("/calendar/monthly-tracker")
```

`POST /calendar/monthly-tracker` returns `204`.

- [ ] **Step 5: Verify GREEN**

Run:

```bash
mvn -B -Dtest=BudgetTrackerControllerTests test
```

Expected: all tests pass.

---

### Task 4: Update OpenAPI coverage and docs

**Files:**
- Modify: `src/test/java/les13/finguide/backend/openapi/OpenApiContractCoverageTests.java`
- Modify: `src/test/java/les13/finguide/backend/plans/FinancialItemOpenApiTests.java`
- Modify docs listed above.

- [ ] **Step 1: Remove five endpoints from known gap**

Remove:

```txt
GET /api/v1/plans/{planId}/budget
PATCH /api/v1/plans/{planId}/budget
POST /api/v1/plans/{planId}/budget/envelopes/autogenerate
GET /api/v1/plans/{planId}/calendar/monthly-tracker
POST /api/v1/plans/{planId}/calendar/monthly-tracker
```

- [ ] **Step 2: Extend OpenAPI smoke test**

Add these paths/methods to `FinancialItemOpenApiTests.EXPECTED_METHODS`.

- [ ] **Step 3: Update docs**

Document #12 done, OpenAPI gap `14`, schema tables, and endpoint semantics.

- [ ] **Step 4: Run focused checks**

Run:

```bash
mvn -B -Dtest=BudgetTrackerControllerTests,OpenApiContractCoverageTests,FinancialItemOpenApiTests test
```

Expected: pass.

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

If venv is missing, create it with:

```bash
python3 -m venv /tmp/finguide-docs-venv
/tmp/finguide-docs-venv/bin/pip install -q -r requirements-docs.txt
/tmp/finguide-docs-venv/bin/mkdocs build --strict
```

Expected: docs build succeeds.

- [ ] **Step 3: Commit implementation**

```bash
git add README.md docs src/main src/test
git commit -m "feat: implement budget tracker endpoints"
```

- [ ] **Step 4: Request code review**

Dispatch a code review against `origin/main..HEAD`, requiring checks for access control, budget calculations, tracker upsert, OpenAPI gap, docs, and data consistency.

- [ ] **Step 5: Fix review findings**

Fix Critical/Important findings, add regression tests, rerun focused and full checks.

- [ ] **Step 6: Push and create PR**

```bash
git push -u git@github.com:svoronkov-les13/finguide-be.git issue-12-budget-tracker
```

Create PR with title:

```txt
feat: implement budget tracker endpoints
```

Body includes verification and `Closes #12`.

- [ ] **Step 7: Merge after green checks**

Merge PR, run post-merge `mvn -B test` on `main`, confirm backend/docs deploy success, then clean branch/worktree and remove `/tmp/openclaw-gh-token`.
