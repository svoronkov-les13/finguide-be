# Pension Required Capital Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Pension page display strategy-specific required capital that reacts to desired retirement spending, investment return, inflation, and state pension.

**Architecture:** Keep pension math canonical in `finguide-be` and add required-capital fields to the existing pension projection response. `finguide-web` fetches and maps that response, selects the field for the persisted withdrawal strategy, and keeps mock-only parity in an isolated helper. Existing dashboard accumulation metrics and spend-down chart-horizon behavior remain unchanged.

**Tech Stack:** Java 21, Spring Boot 3, BigDecimal, JUnit 5/AssertJ/MockMvc, OpenAPI/Orval, React 19, TypeScript, TanStack Query, Vitest, Playwright.

---

### Task 1: Add the backend required-capital calculator

**Repository:** `finguide-be`

**Files:**
- Create: `src/main/java/les13/finguide/backend/pension/PensionRequiredCapitalCalculator.java`
- Create: `src/test/java/les13/finguide/backend/pension/PensionRequiredCapitalCalculatorTests.java`

- [ ] **Step 1: Write failing calculator tests**

Cover the monotonic and edge-case contract with exact inputs:

```java
@Test
void preserveCapitalRespondsToSpendingReturnAndStatePension() {
    Result baseline = calculate(settings("100000", "10", "5", false, "0"), 0);
    Result higherSpend = calculate(settings("200000", "10", "5", false, "0"), 0);
    Result higherReturn = calculate(settings("100000", "12", "5", false, "0"), 0);
    Result withPension = calculate(settings("100000", "10", "5", true, "30000"), 0);

    assertThat(higherSpend.preserveCapital()).isGreaterThan(baseline.preserveCapital());
    assertThat(higherReturn.preserveCapital()).isLessThan(baseline.preserveCapital());
    assertThat(withPension.preserveCapital()).isLessThan(baseline.preserveCapital());
}

@Test
void preserveCapitalReportsNonPositiveRealReturn() {
    Result result = calculate(settings("100000", "5", "5", false, "0"), 0);

    assertThat(result.preserveCapital()).isNull();
    assertThat(result.preserveStatus()).isEqualTo(RequiredCapitalStatus.NON_POSITIVE_REAL_RETURN);
}

@Test
void spendDownFundsExactlyThirtyYears() {
    PensionSettings settings = settings("100000", "8", "4", false, "0");
    Result result = calculate(settings, 0);

    BigDecimal endingCapital = simulateThirtyYears(result.spendDownCapital(), settings);
    assertThat(endingCapital.abs()).isLessThanOrEqualTo(new BigDecimal("0.05"));
}
```

- [ ] **Step 2: Run the calculator tests and verify failure**

Run:

```bash
mvn -q -Dtest=PensionRequiredCapitalCalculatorTests test
```

Expected: compilation fails because the calculator and result types do not exist.

- [ ] **Step 3: Implement the minimal calculator**

Implement one focused utility:

```java
public final class PensionRequiredCapitalCalculator {
    public static final int SPEND_DOWN_YEARS = 30;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    public static Result calculate(PensionSettings pension, int yearsToRetirement) {
        BigDecimal inflationRate = pension.inflationPct().divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);
        BigDecimal returnRate = pension.expectedReturnPct().divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);
        BigDecimal inflationFactor = BigDecimal.ONE.add(inflationRate).pow(Math.max(0, yearsToRetirement));
        BigDecimal desired = pension.desiredMonthlyExpensesCurrentPrices()
                .multiply(TWELVE).multiply(inflationFactor);
        BigDecimal statePension = pension.statePensionEnabled()
                ? pension.statePensionMonthly().multiply(TWELVE).multiply(inflationFactor)
                : BigDecimal.ZERO;
        BigDecimal annualNeed = desired.subtract(statePension).max(BigDecimal.ZERO);
        BigDecimal realReturn = pension.expectedReturnPct().subtract(pension.inflationPct());

        BigDecimal preserve = annualNeed.signum() == 0
                ? BigDecimal.ZERO
                : realReturn.signum() <= 0 ? null
                : annualNeed.multiply(ONE_HUNDRED).divide(realReturn, 2, RoundingMode.HALF_UP);
        RequiredCapitalStatus status = annualNeed.signum() > 0 && realReturn.signum() <= 0
                ? RequiredCapitalStatus.NON_POSITIVE_REAL_RETURN
                : RequiredCapitalStatus.CALCULATED;

        BigDecimal spendDown = BigDecimal.ZERO;
        for (int year = SPEND_DOWN_YEARS - 1; year >= 0; year--) {
            BigDecimal withdrawal = annualNeed.multiply(BigDecimal.ONE.add(inflationRate).pow(year));
            spendDown = spendDown.add(withdrawal)
                    .divide(BigDecimal.ONE.add(returnRate), 12, RoundingMode.HALF_UP);
        }
        return new Result(scale(preserve), status, scale(spendDown));
    }

    public enum RequiredCapitalStatus { CALCULATED, NON_POSITIVE_REAL_RETURN }
    public record Result(BigDecimal preserveCapital, RequiredCapitalStatus preserveStatus,
                         BigDecimal spendDownCapital) {}
}
```

Keep `scale` null-safe and round monetary results to two decimals.

- [ ] **Step 4: Run tests and verify pass**

Run the focused Maven command from Step 2. Expected: all calculator tests pass.

- [ ] **Step 5: Commit backend calculator**

```bash
git add src/main/java/les13/finguide/backend/pension/PensionRequiredCapitalCalculator.java \
        src/test/java/les13/finguide/backend/pension/PensionRequiredCapitalCalculatorTests.java
git commit -m "feat: calculate required pension capital"
```

### Task 2: Add required capital to the pension projection API

**Repository:** `finguide-be`

**Files:**
- Modify: `src/main/java/les13/finguide/backend/pension/PensionProjection.java`
- Modify: `src/main/java/les13/finguide/backend/plans/PlanReadService.java`
- Modify: `src/main/java/les13/finguide/backend/api/PlanApiMapper.java`
- Modify: `src/test/java/les13/finguide/backend/plans/PlanReadControllerTests.java`
- Modify: `openapi/openapi.json`
- Modify: `openapi/openapi-mock.json`

- [ ] **Step 1: Extend controller assertions first**

Add assertions to `returnsPersistedAnalyticsAndPensionProjection`:

```java
.andExpect(jsonPath("$.data.preserveCapital.requiredCapitalStatus").value("calculated"))
.andExpect(jsonPath("$.data.preserveCapital.requiredCapitalAtRetirement", greaterThan(0.0)))
.andExpect(jsonPath("$.data.spendDown.requiredCapitalAtRetirement", greaterThan(0.0)))
.andExpect(jsonPath("$.data.spendDown.series", hasSize(25)));
```

Add a controller test that patches higher desired spending and verifies both required-capital values increase while `spendDown.series` still follows the configured horizon.

- [ ] **Step 2: Run the controller test and verify failure**

```bash
mvn -q -Dtest=PlanReadControllerTests#returnsPersistedAnalyticsAndPensionProjection test
```

Expected: JSON path assertions fail because the new fields are absent.

- [ ] **Step 3: Extend the domain response and service**

Add fields without changing existing ones:

```java
public record PreserveCapital(
        BigDecimal requiredCapitalAtRetirement,
        PensionRequiredCapitalCalculator.RequiredCapitalStatus requiredCapitalStatus,
        BigDecimal annualSpendableAtRetirement,
        BigDecimal annualSpendableCurrentPrices,
        BigDecimal monthlySpendableCurrentPrices
) {}

public record SpendDown(
        BigDecimal requiredCapitalAtRetirement,
        BigDecimal desiredMonthlyExpensesCurrentPrices,
        BigDecimal desiredAnnualExpensesAtRetirement,
        int retirementYears,
        int depletionAge,
        List<PensionSpendDownPoint> series
) {}
```

In `PlanReadService.pensionProjection`, call:

```java
PensionRequiredCapitalCalculator.Result required =
        PensionRequiredCapitalCalculator.calculate(pension, yearsToRetirement);
```

Pass `required.preserveCapital()`, `required.preserveStatus()`, and `required.spendDownCapital()` into the nested response records. Do not change `retirementSpendDownYears`, `series`, or dashboard calculations.

- [ ] **Step 4: Map the additive JSON fields**

Extend `PlanApiMapper.pensionProjection`:

```java
"requiredCapitalAtRetirement", projection.preserveCapital().requiredCapitalAtRetirement(),
"requiredCapitalStatus", projection.preserveCapital().requiredCapitalStatus().name().toLowerCase()
```

and:

```java
"requiredCapitalAtRetirement", projection.spendDown().requiredCapitalAtRetirement()
```

Use `LinkedHashMap` for preserve-capital mapping because `Map.of` rejects the intentional null required-capital value.

- [ ] **Step 5: Update OpenAPI schemas**

Add nullable `requiredCapitalAtRetirement` and enum `requiredCapitalStatus` to preserve capital, and numeric `requiredCapitalAtRetirement` to spend down in both backend OpenAPI files. Keep every existing field and required-list entry consistent with runtime JSON.

- [ ] **Step 6: Run backend regression tests**

```bash
mvn -q -Dtest=PensionRequiredCapitalCalculatorTests,PlanReadControllerTests,PlanReadServiceUnitTests test
```

Expected: all selected tests pass, including the existing variable-length spend-down series test.

- [ ] **Step 7: Commit the API integration**

```bash
git add src/main/java/les13/finguide/backend/pension/PensionProjection.java \
        src/main/java/les13/finguide/backend/plans/PlanReadService.java \
        src/main/java/les13/finguide/backend/api/PlanApiMapper.java \
        src/test/java/les13/finguide/backend/plans/PlanReadControllerTests.java \
        openapi/openapi.json openapi/openapi-mock.json
git commit -m "feat: expose required pension capital"
```

### Task 3: Fetch and map pension projection in the web client

**Repository:** `finguide-web`

**Files:**
- Modify: `openapi/finguide.openapi.json`
- Regenerate: `src/shared/api/generated/finguide.ts`
- Regenerate: `src/shared/api/generated/model/pensionProjectionPreserveCapital.ts`
- Regenerate: `src/shared/api/generated/model/pensionProjectionSpendDown.ts`
- Modify: `src/types/finance.ts`
- Modify: `src/api/backendPlanClient.ts`
- Modify: `src/api/backendPlanClient.test.ts`

- [ ] **Step 1: Write a failing adapter test**

Extend the fetch mock to return:

```ts
if (url.endsWith("/pension/projection")) return jsonResponse({
  data: {
    currentAge: 33,
    retirementAge: 60,
    retirementYear: 2053,
    capitalAtRetirement: 10_000_000,
    nominalReturnPct: 10,
    averageInflationPct: 7,
    realReturnPct: 3,
    preserveCapital: {
      requiredCapitalAtRetirement: 40_000_000,
      requiredCapitalStatus: "calculated",
      annualSpendableAtRetirement: 1,
      annualSpendableCurrentPrices: 1,
      monthlySpendableCurrentPrices: 1,
    },
    spendDown: {
      requiredCapitalAtRetirement: 25_000_000,
      desiredMonthlyExpensesCurrentPrices: 150_000,
      desiredAnnualExpensesAtRetirement: 1,
      retirementYears: 20,
      depletionAge: 80,
      series: [],
    },
  },
});
```

Assert `plan.pensionProjection` contains both required-capital values and status.

- [ ] **Step 2: Run adapter test and verify failure**

```bash
bun run test -- src/api/backendPlanClient.test.ts
```

Expected: the projection request or mapped property is missing.

- [ ] **Step 3: Sync and regenerate the client**

Copy the updated backend contract to `openapi/finguide.openapi.json`, then run:

```bash
bun run generate:api
```

Verify generated types contain the three additive fields and no unrelated contract churn.

- [ ] **Step 4: Add the UI-facing projection type**

Add to `src/types/finance.ts`:

```ts
export interface PensionRequiredCapitalProjection {
  preserveCapital: {
    requiredCapitalAtRetirement: number | null;
    requiredCapitalStatus: "calculated" | "non_positive_real_return";
  };
  spendDown: {
    requiredCapitalAtRetirement: number;
  };
}
```

Add optional `pensionProjection?: PensionRequiredCapitalProjection` to `FinancialPlan`.

- [ ] **Step 5: Fetch and map the projection**

Import `getPlansPlanIdPensionProjection` and generated `PensionProjection`. Fetch it in the existing `Promise.all` inside `readBackendPlan`, unwrap it, and map only the required fields into `FinancialPlan.pensionProjection`.

- [ ] **Step 6: Run adapter tests and typecheck**

```bash
bun run test -- src/api/backendPlanClient.test.ts
bun run typecheck
```

Expected: both commands pass.

- [ ] **Step 7: Commit web contract integration**

```bash
git add openapi/finguide.openapi.json src/shared/api/generated src/types/finance.ts \
        src/api/backendPlanClient.ts src/api/backendPlanClient.test.ts
git commit -m "feat: load required pension capital"
```

### Task 4: Render required capital and preserve mock parity

**Repository:** `finguide-web`

**Files:**
- Create: `src/engine/calculatePensionRequiredCapital.ts`
- Create: `src/engine/calculatePensionRequiredCapital.test.ts`
- Modify: `src/data/mock-plan.ts`
- Modify: `src/api/mockApi.ts`
- Modify: `src/pages/PensionPage.tsx`
- Modify: `src/pages/PensionPage.test.tsx`
- Modify: `src/i18n/messages.ts`

- [ ] **Step 1: Write failing page tests**

Provide distinct fixture values for preserve and spend-down strategies. Assert:

```ts
expect(renderedCapital("preserve_capital")).toContain("40");
expect(renderedCapital("spend_down_30y")).toContain("25");
expect(html).not.toContain("80,3");
```

Add a test where preserve capital is null with `non_positive_real_return` and assert the explanatory message is rendered instead of a number.

- [ ] **Step 2: Run page tests and verify failure**

```bash
bun run test -- src/pages/PensionPage.test.tsx
```

Expected: page still reads `dashboardSnapshot.pensionCapitalRub` or the magic fallback.

- [ ] **Step 3: Update PensionPage**

Replace:

```ts
const targetCapital = plan.dashboardSnapshot?.pensionCapitalRub || 80330049;
```

with strategy selection from `plan.pensionProjection`. Remove the magic fallback. Render the formatted amount for calculated results and a translated unavailable explanation for `non_positive_real_return`.

- [ ] **Step 4: Add mock-only recalculation**

Implement the same fixed formulas in `calculatePensionRequiredCapital.ts`, document that it exists only for the in-memory mock, and use it from `mockApi.updateSettings` to update `db.pensionProjection`. Seed `mock-plan.ts` with the same shape.

- [ ] **Step 5: Test mock monotonic behavior**

Assert in the helper test that higher spending raises both values, higher return lowers both values, and state pension lowers both values. Assert spend-down uses 30 iterations.

- [ ] **Step 6: Run focused web tests**

```bash
bun run test -- src/engine/calculatePensionRequiredCapital.test.ts src/api/mockApi.test.ts src/pages/PensionPage.test.tsx
bun run typecheck
```

Expected: all pass.

- [ ] **Step 7: Commit the page fix**

```bash
git add src/engine/calculatePensionRequiredCapital.ts \
        src/engine/calculatePensionRequiredCapital.test.ts src/data/mock-plan.ts \
        src/api/mockApi.ts src/pages/PensionPage.tsx src/pages/PensionPage.test.tsx \
        src/i18n/messages.ts
git commit -m "fix: update required pension capital"
```

### Task 5: Full verification and browser proof

**Repositories:** `finguide-be`, `finguide-web`

- [ ] **Step 1: Run backend verification**

```bash
mvn -q test
```

Expected: full backend suite passes.

- [ ] **Step 2: Run frontend verification**

```bash
bun run test
bun run typecheck
bun run lint
bun run build
```

Expected: all commands pass.

- [ ] **Step 3: Run the local browser reproduction**

Start the web app in mock mode and repeat the original report:

1. Open `/pension`.
2. Record required capital at `10 000 ₽/month`, `9%`.
3. Change spending to `200 000 ₽/month` and return to `15%`.
4. Press Calculate.
5. Verify the required-capital number changes and the displayed strategy matches the selected mode.
6. Switch to preserve-capital with return not exceeding inflation and verify the explicit unavailable state.

- [ ] **Step 4: Review diffs for scope**

Confirm no dashboard accumulation formula, scenario formula, existing spend-down series horizon, or unrelated UI styling changed.

- [ ] **Step 5: Commit any verification-only fixture adjustments**

Only if required by the browser proof; otherwise make no additional commit.

