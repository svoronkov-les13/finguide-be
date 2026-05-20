# Dashboard Monthly Chart Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show dashboard capital accumulation by months because the model now uses monthly tracker facts and goal target months.

**Architecture:** Backend exposes a monthly cashflow projection derived from the same capital model as yearly cashflow. Frontend prefers monthly projection for the dashboard forecast chart, keeping the lower annual bars unchanged unless monthly data is unavailable.

**Tech Stack:** Java 21/Spring Boot backend, React/TypeScript/Vite frontend, Recharts.

---

### Task 1: Backend monthly projection

**Files:**
- Modify: `src/main/java/les13/finguide/backend/plans/PlanReadController.java`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanReadService.java`
- Modify: `src/main/java/les13/finguide/backend/api/PlanApiMapper.java`
- Test: `src/test/java/les13/finguide/backend/plans/PlanReadControllerTests.java`

- [ ] Add a test for `GET /plans/{planId}/analytics/cashflow/monthly` showing tracker facts replace plan monthly savings and goal expenses happen in `targetMonth`.
- [ ] Implement a monthly projection point DTO/map and service method.
- [ ] Keep annual `/analytics/cashflow` behavior compatible.
- [ ] Run `mvn -B test`.

### Task 2: Frontend dashboard monthly chart

**Files:**
- Modify: `src/types/finance.ts`
- Modify: `src/api/backendPlanClient.ts`
- Modify: `src/components/dashboard/ForecastChart.tsx`
- Test: `src/api/backendPlanClient.test.ts`

- [ ] Add frontend model for monthly forecast points.
- [ ] Fetch monthly cashflow and map it to dashboard chart rows with labels like `янв 2026`.
- [ ] Use monthly points for the top capital line and keep annual data for lower bars.
- [ ] Run targeted tests, `bun run build`, `bun run lint`.
