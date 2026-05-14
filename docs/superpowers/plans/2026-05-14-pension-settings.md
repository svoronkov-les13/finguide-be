# Pension Settings Endpoints Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement persisted pension settings read/update endpoints for backend issue #11.

**Architecture:** Extend the existing plan read stack instead of introducing a new module boundary. `PlanReadController` exposes the endpoints, `PlanReadService` handles access and validation, `JdbcPlanStateRepository` persists the existing `pension_settings` row, and `PlanApiMapper.pension` remains the response shape.

**Tech Stack:** Java 21, Spring Boot 3, Spring MVC, Spring JDBC, H2 demo persistence, JUnit/MockMvc, Springdoc OpenAPI, MkDocs.

---

## Files

- Modify: `src/test/java/les13/finguide/backend/plans/PlanReadControllerTests.java`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanReadController.java`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanReadService.java`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanStateRepository.java`
- Modify: `src/main/java/les13/finguide/backend/plans/JdbcPlanStateRepository.java`
- Modify: `src/test/java/les13/finguide/backend/openapi/OpenApiContractCoverageTests.java`
- Modify: `README.md`, `docs/index.md`, `docs/status.md`, `docs/roadmap.md`, `docs/contract.md`

## Task 1: Controller tests first

- [ ] Add tests in `PlanReadControllerTests`:
  - `returnsPersistedPensionSettings`
  - `updatesPersistedPensionSettingsForAuthenticatedPlan`
  - `pensionProjectionUsesUpdatedSettings`
  - `rejectsInvalidPensionSettings`
  - `rejectsPensionUpdateForAnonymousDemoPlan`
- [ ] Run `mvn -B -Dtest=PlanReadControllerTests test`.
- [ ] Expected result: compile or request mapping failures because endpoints/service methods do not exist yet.

## Task 2: Service/controller/repository implementation

- [ ] Add `PlanReadController` routes:
  - `@GetMapping("/plans/{planId}/pension")`
  - `@PatchMapping("/plans/{planId}/pension")`
- [ ] Add `PlanReadService.pension(UUID planId)` using `plan(planId).pension()`.
- [ ] Add `PlanReadService.updatePension(UUID planId, PensionSettings request)`:
  - call `accessService.requireWritablePlan(planId)`;
  - validate request;
  - call `repository.updatePensionSettings(planId, validated)`.
- [ ] Add validation matching OpenAPI constraints.
- [ ] Add `PlanStateRepository.updatePensionSettings`.
- [ ] Implement JDBC update of `pension_settings` and return refreshed `PensionSettings`.
- [ ] Run `mvn -B -Dtest=PlanReadControllerTests test`.
- [ ] Expected result: PASS.

## Task 3: OpenAPI coverage and full tests

- [ ] Remove from `KNOWN_CONTRACT_OPERATIONS_NOT_IN_SPRINGDOC`:
  - `GET /api/v1/plans/{planId}/pension`
  - `PATCH /api/v1/plans/{planId}/pension`
- [ ] Update expected gap implications from 26 to 24 where docs mention current Springdoc coverage.
- [ ] Run `mvn -B test`.
- [ ] Expected result: PASS, with OpenAPI contract test expecting 54 contract operations and 24 known missing operations.

## Task 4: Documentation

- [ ] Update docs to mark #11 implemented:
  - `README.md`
  - `docs/index.md`
  - `docs/status.md`
  - `docs/roadmap.md`
  - `docs/contract.md`
- [ ] Mention OpenAPI coverage as 54 total, 30 implemented, 24 known gap.
- [ ] Run `mkdocs build --strict` via temporary venv if needed.
- [ ] Expected result: PASS.

## Task 5: Commit, PR, review, merge

- [ ] Commit with `feat(backend): implement pension settings endpoints`.
- [ ] Push branch `issue-11-pension-settings`.
- [ ] Create PR referencing #11.
- [ ] Request code review.
- [ ] Fix Critical/Important findings.
- [ ] Merge after clean review and passing verification.
- [ ] Clean branch/worktree.
