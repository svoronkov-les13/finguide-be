package les13.finguide.backend.pension;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.util.Locale;
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
        SPEND_DOWN_30Y;

        @JsonCreator
        public static WithdrawalStrategy fromApiValue(String value) {
            if (value == null) {
                return null;
            }
            return WithdrawalStrategy.valueOf(value.toUpperCase(Locale.ROOT));
        }

        @JsonValue
        public String apiValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
