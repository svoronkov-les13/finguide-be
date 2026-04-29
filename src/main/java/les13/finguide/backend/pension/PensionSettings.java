package les13.finguide.backend.pension;

import java.math.BigDecimal;
import java.util.UUID;

public record PensionSettings(
        UUID planId,
        int currentAge,
        int retirementAge,
        BigDecimal monthlyExpenses,
        BigDecimal desiredMonthlyExpensesCurrentPrices,
        String currency,
        BigDecimal expectedReturnPct,
        BigDecimal inflationPct,
        WithdrawalStrategy withdrawalStrategy,
        boolean statePensionEnabled,
        BigDecimal statePensionMonthly
) {
    public enum WithdrawalStrategy {
        PRESERVE_CAPITAL,
        SPEND_DOWN_30Y
    }
}
