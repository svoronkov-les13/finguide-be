package les13.finguide.backend.pension;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static les13.finguide.backend.pension.PensionRequiredCapitalCalculator.RequiredCapitalStatus;
import static les13.finguide.backend.pension.PensionRequiredCapitalCalculator.Result;
import static les13.finguide.backend.pension.PensionRequiredCapitalCalculator.calculate;
import static org.assertj.core.api.Assertions.assertThat;

class PensionRequiredCapitalCalculatorTests {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Test
    void returnsExactTwoDecimalRequiredCapitalValues() {
        Result result = calculate(settings("100", "10", "0", false, "0"), 0);

        assertThat(result.preserveCapital()).isEqualByComparingTo("12000.00");
        assertThat(result.preserveCapital().scale()).isEqualTo(2);
        assertThat(result.spendDownCapital()).isEqualByComparingTo("11312.30");
        assertThat(result.spendDownCapital().scale()).isEqualTo(2);
    }

    @Test
    void higherSpendingRaisesBothRequiredCapitalValues() {
        Result baseline = calculate(settings("100000", "10", "5", false, "0"), 0);
        Result higherSpending = calculate(settings("200000", "10", "5", false, "0"), 0);

        assertThat(higherSpending.preserveCapital()).isGreaterThan(baseline.preserveCapital());
        assertThat(higherSpending.spendDownCapital()).isGreaterThan(baseline.spendDownCapital());
    }

    @Test
    void higherReturnLowersBothRequiredCapitalValues() {
        Result baseline = calculate(settings("100000", "10", "5", false, "0"), 0);
        Result higherReturn = calculate(settings("100000", "12", "5", false, "0"), 0);

        assertThat(higherReturn.preserveCapital()).isLessThan(baseline.preserveCapital());
        assertThat(higherReturn.spendDownCapital()).isLessThan(baseline.spendDownCapital());
    }

    @Test
    void enabledStatePensionLowersBothRequiredCapitalValues() {
        Result baseline = calculate(settings("100000", "10", "5", false, "0"), 0);
        Result withPension = calculate(settings("100000", "10", "5", true, "30000"), 0);

        assertThat(withPension.preserveCapital()).isLessThan(baseline.preserveCapital());
        assertThat(withPension.spendDownCapital()).isLessThan(baseline.spendDownCapital());
    }

    @Test
    void higherInflationRaisesBothRequiredCapitalValues() {
        Result baseline = calculate(settings("100000", "10", "4", false, "0"), 20);
        Result higherInflation = calculate(settings("100000", "10", "5", false, "0"), 20);

        assertThat(higherInflation.preserveCapital()).isGreaterThan(baseline.preserveCapital());
        assertThat(higherInflation.spendDownCapital()).isGreaterThan(baseline.spendDownCapital());
    }

    @Test
    void preserveCapitalReportsNonPositiveRealReturn() {
        Result result = calculate(settings("100000", "5", "5", false, "0"), 0);

        assertThat(result.preserveCapital()).isNull();
        assertThat(result.preserveStatus()).isEqualTo(RequiredCapitalStatus.NON_POSITIVE_REAL_RETURN);
    }

    @Test
    void zeroAnnualNeedRequiresNoCapital() {
        Result result = calculate(settings("100000", "5", "5", true, "100000"), 10);

        assertThat(result.preserveCapital()).isEqualByComparingTo("0.00");
        assertThat(result.preserveStatus()).isEqualTo(RequiredCapitalStatus.CALCULATED);
        assertThat(result.spendDownCapital()).isEqualByComparingTo("0.00");
    }

    @Test
    void spendDownReverseCalculationReachesApproximatelyZeroAfterThirtyYears() {
        PensionSettings settings = settings("100000", "8", "4", false, "0");
        Result result = calculate(settings, 0);

        BigDecimal endingCapital = simulateThirtyYears(result.spendDownCapital(), settings);

        assertThat(endingCapital.abs()).isLessThanOrEqualTo(new BigDecimal("0.05"));
    }

    private static PensionSettings settings(
            String desiredMonthlyExpenses,
            String expectedReturnPct,
            String inflationPct,
            boolean statePensionEnabled,
            String statePensionMonthly
    ) {
        return new PensionSettings(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                40,
                65,
                BigDecimal.ZERO,
                new BigDecimal(desiredMonthlyExpenses),
                "RUB",
                new BigDecimal(expectedReturnPct),
                new BigDecimal(inflationPct),
                PensionSettings.WithdrawalStrategy.PRESERVE_CAPITAL,
                statePensionEnabled,
                new BigDecimal(statePensionMonthly)
        );
    }

    private static BigDecimal simulateThirtyYears(BigDecimal startingCapital, PensionSettings settings) {
        BigDecimal returnRate = settings.expectedReturnPct().divide(HUNDRED, 12, RoundingMode.HALF_UP);
        BigDecimal inflationRate = settings.inflationPct().divide(HUNDRED, 12, RoundingMode.HALF_UP);
        BigDecimal annualNeed = settings.desiredMonthlyExpensesCurrentPrices().multiply(BigDecimal.valueOf(12));
        BigDecimal capital = startingCapital;

        for (int year = 0; year < PensionRequiredCapitalCalculator.SPEND_DOWN_YEARS; year++) {
            BigDecimal withdrawal = annualNeed.multiply(BigDecimal.ONE.add(inflationRate).pow(year));
            capital = capital.multiply(BigDecimal.ONE.add(returnRate)).subtract(withdrawal);
        }
        return capital;
    }
}
