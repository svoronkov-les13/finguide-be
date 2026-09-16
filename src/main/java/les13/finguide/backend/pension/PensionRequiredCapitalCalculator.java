package les13.finguide.backend.pension;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PensionRequiredCapitalCalculator {
    public static final int SPEND_DOWN_YEARS = 30;

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private PensionRequiredCapitalCalculator() {
    }

    public static Result calculate(PensionSettings pension, int yearsToRetirement) {
        BigDecimal inflationRate = pension.inflationPct().divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);
        BigDecimal returnRate = pension.expectedReturnPct().divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP);
        BigDecimal inflationFactor = BigDecimal.ONE.add(inflationRate).pow(Math.max(0, yearsToRetirement));
        BigDecimal desiredAnnualAtRetirement = pension.desiredMonthlyExpensesCurrentPrices()
                .multiply(TWELVE)
                .multiply(inflationFactor);
        BigDecimal statePensionAnnualAtRetirement = pension.statePensionEnabled()
                ? pension.statePensionMonthly().multiply(TWELVE).multiply(inflationFactor)
                : BigDecimal.ZERO;
        BigDecimal annualNeed = desiredAnnualAtRetirement.subtract(statePensionAnnualAtRetirement).max(BigDecimal.ZERO);
        BigDecimal realReturnPct = pension.expectedReturnPct().subtract(pension.inflationPct());

        BigDecimal preserveCapital = annualNeed.signum() == 0
                ? BigDecimal.ZERO
                : realReturnPct.signum() <= 0
                ? null
                : annualNeed.multiply(ONE_HUNDRED).divide(realReturnPct, 2, RoundingMode.HALF_UP);
        RequiredCapitalStatus preserveStatus = annualNeed.signum() > 0 && realReturnPct.signum() <= 0
                ? RequiredCapitalStatus.NON_POSITIVE_REAL_RETURN
                : RequiredCapitalStatus.CALCULATED;

        BigDecimal spendDownCapital = BigDecimal.ZERO;
        for (int year = SPEND_DOWN_YEARS - 1; year >= 0; year--) {
            BigDecimal withdrawal = annualNeed.multiply(BigDecimal.ONE.add(inflationRate).pow(year));
            spendDownCapital = spendDownCapital.add(withdrawal)
                    .divide(BigDecimal.ONE.add(returnRate), 12, RoundingMode.HALF_UP);
        }

        return new Result(scale(preserveCapital), preserveStatus, scale(spendDownCapital));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public enum RequiredCapitalStatus {
        CALCULATED,
        NON_POSITIVE_REAL_RETURN
    }

    public record Result(
            BigDecimal preserveCapital,
            RequiredCapitalStatus preserveStatus,
            BigDecimal spendDownCapital
    ) {
    }
}
