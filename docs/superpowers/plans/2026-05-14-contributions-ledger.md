# Contributions Ledger Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement persisted contribution ledger endpoints for issue #10, with goal saved amount derived from contributions.

**Architecture:** Add a dedicated `ContributionService` and controller endpoints under `/api/v1/plans/{planId}`. Persist contributions in H2 through `JdbcPlanStateRepository`, and recalculate `goals.saved_amount` from contribution sums after each mutation. Keep access rules aligned with existing financial mutations: reads require plan access, writes require writable plan access.

**Tech Stack:** Java 21, Spring Boot 3, Spring MVC, Spring JDBC, H2, JUnit/MockMvc, Spring Security test JWT helpers, Springdoc OpenAPI.

---

## Files

- Modify `src/main/resources/schema.sql`: add `contributions` table and drop order.
- Modify `src/main/java/les13/finguide/backend/plans/PlanStateRepository.java`: add contribution persistence methods.
- Modify `src/main/java/les13/finguide/backend/plans/JdbcPlanStateRepository.java`: load, map, CRUD contributions and recalculate goal progress.
- Create `src/main/java/les13/finguide/backend/plans/ContributionRequests.java`: request DTO for create/patch.
- Create `src/main/java/les13/finguide/backend/plans/ContributionService.java`: validation, access checks, mutation orchestration.
- Modify `src/main/java/les13/finguide/backend/plans/FinancialItemController.java`: add contribution routes or delegate methods; keep existing route style.
- Modify `src/test/java/les13/finguide/backend/plans/PlanReadControllerTests.java` or create `ContributionControllerTests.java`: controller integration tests.
- Modify `src/test/java/les13/finguide/backend/openapi/OpenApiContractCoverageTests.java`: reduce known gap by five operations.
- Modify docs: `README.md`, `docs/contract.md`, `docs/database.md`, `docs/index.md`, `docs/roadmap.md`, `docs/status.md`.

---

### Task 1: Add failing contribution endpoint tests

**Files:**
- Create: `src/test/java/les13/finguide/backend/plans/ContributionControllerTests.java`

- [ ] **Step 1: Write failing tests for contribution CRUD and goal recalculation**

Create `ContributionControllerTests` with SpringBootTest/MockMvc, matching style from `FinancialItemControllerTests` and `PlanReadControllerTests`. Include these test names and assertions:

```java
@Test
void createsListsReadsUpdatesAndDeletesContributionForAuthenticatedPlan() throws Exception
```

Flow:
1. Create authenticated current plan using JWT subject `contribution-owner`.
2. Extract `planId` and first two `goalId` values from `/api/v1/plans/current`.
3. Assert first goal starts with `savedAmount == 0` after ledger source-of-truth is active.
4. POST `/api/v1/plans/{planId}/contributions` with:
   - `goalId`: first goal id
   - `amount`: `1000`
   - `currency`: `RUB`
   - `date`: `2026-05-14`
   - `note`: `Initial deposit`
5. Expect `201`, response `data.amount == 1000`, response `data.goalId == firstGoalId`.
6. GET list and expect one contribution.
7. GET by id and expect the same note.
8. GET `/plans/current` and expect first goal `savedAmount == 1000`.
9. PATCH contribution to second goal with `amount == 2500`, `date == 2026-05-15`, `note == Moved deposit`.
10. GET `/plans/current` and expect first goal `savedAmount == 0`, second goal `savedAmount == 2500`.
11. DELETE contribution and expect `204`.
12. GET `/plans/current` and expect second goal `savedAmount == 0`.

- [ ] **Step 2: Write failing validation/access tests**

Add tests:

```java
@Test
void rejectsInvalidContributionRequest() throws Exception
```

POST with valid JWT but invalid body: missing `goalId`, `amount: -1`, `currency: "12!"`, missing `date`. Expect `400`.

```java
@Test
void returnsNotFoundForUnknownContribution() throws Exception
```

GET `/contributions/{randomUuid}` under an accessible authenticated plan. Expect `404`.

```java
@Test
void rejectsContributionMutationForAnonymousDemoPlan() throws Exception
```

POST to anonymous seed plan `22222222-2222-4222-8222-222222222222`. Expect `403`.

```java
@Test
void rejectsContributionAccessForAnotherUsersPlan() throws Exception
```

Create plan for subject `contribution-owner-a`; then try GET list for that plan as subject `contribution-owner-b`. Expect `403`.

- [ ] **Step 3: Run tests and verify RED**

Run:

```bash
mvn -B -Dtest=ContributionControllerTests test
```

Expected: compilation failure or 404 failures because contribution endpoints and request types do not exist yet.

---

### Task 2: Add persistence schema and repository methods

**Files:**
- Modify: `src/main/resources/schema.sql`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanStateRepository.java`
- Modify: `src/main/java/les13/finguide/backend/plans/JdbcPlanStateRepository.java`

- [ ] **Step 1: Add H2 table**

In `schema.sql`, add `drop table if exists contributions;` before `drop table if exists goals;`.

After `goals` table, add:

```sql
create table contributions (
  id uuid primary key,
  plan_id uuid not null references financial_plans(id),
  goal_id uuid not null references goals(id),
  amount numeric(19, 2) not null,
  currency varchar(3) not null,
  contribution_date date not null,
  note varchar(1024),
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);
```

- [ ] **Step 2: Extend repository interface**

Add methods to `PlanStateRepository`:

```java
List<Contribution> findContributions(UUID planId);
Optional<Contribution> findContribution(UUID planId, UUID contributionId);
Contribution createContribution(UUID planId, Contribution contribution);
Contribution updateContribution(UUID planId, Contribution contribution);
boolean deleteContribution(UUID planId, UUID contributionId);
void recalculateGoalSavedAmount(UUID planId, UUID goalId);
```

- [ ] **Step 3: Implement repository methods**

In `JdbcPlanStateRepository`:

- load contributions in `findById` using `findContributions(planId)` instead of `List.<Contribution>of()`.
- add `mapContribution` mapping `contribution_date` to `Contribution.date()`.
- implement CRUD scoped by `plan_id` and `id`.
- implement recalc:

```java
jdbcTemplate.update(
    "update goals set saved_amount = (select coalesce(sum(amount), 0) from contributions where plan_id = ? and goal_id = ?), updated_at = ? where plan_id = ? and id = ?",
    planId,
    goalId,
    OffsetDateTime.now(ZoneOffset.UTC),
    planId,
    goalId
);
```

- call `touchPlan(planId, now)` after mutations and recalc.

- [ ] **Step 4: Run targeted tests**

Run:

```bash
mvn -B -Dtest=ContributionControllerTests test
```

Expected: still RED because service/controller endpoints are not implemented, but schema compilation should pass.

---

### Task 3: Add service, request DTO, and controller routes

**Files:**
- Create: `src/main/java/les13/finguide/backend/plans/ContributionRequests.java`
- Create: `src/main/java/les13/finguide/backend/plans/ContributionService.java`
- Modify: `src/main/java/les13/finguide/backend/plans/FinancialItemController.java`

- [ ] **Step 1: Add request DTO**

Create:

```java
package les13.finguide.backend.plans;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class ContributionRequests {
    private ContributionRequests() {
    }

    public record ContributionRequest(
            UUID goalId,
            BigDecimal amount,
            String currency,
            LocalDate date,
            String note
    ) {
    }
}
```

- [ ] **Step 2: Implement `ContributionService`**

Create a Spring `@Service` with methods:

```java
public List<Contribution> contributions(UUID planId)
public Contribution contribution(UUID planId, UUID contributionId)
@Transactional public Contribution createContribution(UUID planId, ContributionRequests.ContributionRequest request)
@Transactional public Contribution updateContribution(UUID planId, UUID contributionId, ContributionRequests.ContributionRequest request)
@Transactional public void deleteContribution(UUID planId, UUID contributionId)
```

Validation helpers:

- `required(value, field)` throws `ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required")`.
- `nonNegative(amount, "amount")` rejects null and negative.
- `currency(value)` requires `[A-Z]{3}`.
- `goal(planId, goalId)` checks `repository.findGoal(planId, goalId)` and throws `404` if missing.

Mutation behavior:

- create: require writable, validate goal, create contribution, recalc new goal.
- update: require writable, load current or `404`, patch supplied fields over current, validate goal, update, recalc old goal, recalc new goal if changed.
- delete: require writable, load current first to know `goalId`, delete, recalc old goal.

- [ ] **Step 3: Add controller endpoints**

Inject `ContributionService` into `FinancialItemController` constructor.

Add endpoints:

```java
@GetMapping("/contributions")
public ApiEnvelope<Object> contributions(@PathVariable UUID planId)

@PostMapping("/contributions")
public ResponseEntity<ApiEnvelope<Map<String, Object>>> createContribution(...)

@GetMapping("/contributions/{id}")
public ApiEnvelope<Map<String, Object>> contribution(...)

@PatchMapping("/contributions/{id}")
public ApiEnvelope<Map<String, Object>> updateContribution(...)

@DeleteMapping("/contributions/{id}")
public ResponseEntity<Void> deleteContribution(...)
```

Use existing `mapper.contribution(...)`.

- [ ] **Step 4: Run targeted tests and verify GREEN**

Run:

```bash
mvn -B -Dtest=ContributionControllerTests test
```

Expected: all contribution controller tests pass.

---

### Task 4: Update OpenAPI coverage and docs

**Files:**
- Modify: `src/test/java/les13/finguide/backend/openapi/OpenApiContractCoverageTests.java`
- Modify: `README.md`
- Modify: `docs/contract.md`
- Modify: `docs/database.md`
- Modify: `docs/index.md`
- Modify: `docs/roadmap.md`
- Modify: `docs/status.md`

- [ ] **Step 1: Update OpenAPI gap test**

Remove these five operations from `KNOWN_CONTRACT_OPERATIONS_NOT_IN_SPRINGDOC`:

```text
DELETE /api/v1/plans/{planId}/contributions/{id}
GET /api/v1/plans/{planId}/contributions
GET /api/v1/plans/{planId}/contributions/{id}
PATCH /api/v1/plans/{planId}/contributions/{id}
POST /api/v1/plans/{planId}/contributions
```

- [ ] **Step 2: Update docs**

Update docs to state:

- #10 is done.
- Contributions are persisted in H2.
- Goal `savedAmount` is derived from contribution sums.
- OpenAPI coverage is now 54 total / 35 implemented / 19 known gap.
- Anonymous demo seed is read-only for contribution mutations.

- [ ] **Step 3: Run verification**

Run:

```bash
mvn -B -Dtest=ContributionControllerTests,OpenApiContractCoverageTests test
mvn -B test
mkdocs build --strict
```

If `mkdocs` is unavailable locally, record the blocker and rely on GitHub Actions docs deploy after PR merge.

---

### Task 5: Commit, PR, review, merge, cleanup

**Files:**
- All changed files

- [ ] **Step 1: Commit implementation**

Run:

```bash
git status --short
git add .
git commit -m "feat(backend): implement contribution ledger"
```

- [ ] **Step 2: Push branch**

Run:

```bash
git push -u git@github.com:svoronkov-les13/finguide-be.git issue-10-contributions-ledger
```

- [ ] **Step 3: Request code review**

Use the requesting-code-review skill with base `main` and feature `HEAD`. Address Critical/Important findings before merge. Minor findings may be fixed if low-risk.

- [ ] **Step 4: Create PR**

Create PR with title:

```text
feat(backend): implement contribution ledger
```

PR body includes summary, verification commands, docs note, and `Closes #10`.

- [ ] **Step 5: Merge PR**

After review and green checks, merge PR into `main`.

- [ ] **Step 6: Post-merge verify and cleanup**

Run from main repo:

```bash
git fetch origin main
git checkout main
git pull --ff-only origin main
mvn -B test
```

Then remove worktree/branch:

```bash
git worktree remove /home/clawd/.openclaw/workspace/repos/finguide-be/.worktrees/issue-10-contributions-ledger
git worktree prune
git branch -d issue-10-contributions-ledger
git push git@github.com:svoronkov-les13/finguide-be.git --delete issue-10-contributions-ledger
```

Update workspace memory with PR URL, merge commit, verification, and CI status.
