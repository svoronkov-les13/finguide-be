package les13.finguide.backend.plans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class FinancialItemRequests {
    private FinancialItemRequests() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IncomeRequest(
            String id,
            String name,
            BigDecimal amount,
            String currency,
            String frequency,
            String growthType,
            BigDecimal growthPct,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpenseRequest(
            String id,
            String name,
            BigDecimal amount,
            String currency,
            String frequency,
            String growthType,
            BigDecimal growthPct,
            String growthLabel,
            String budgetClass,
            LocalDate startDate,
            LocalDate endDate
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoalRequest(
            String id,
            String name,
            String icon,
            BigDecimal currentCost,
            BigDecimal savedAmount,
            String currency,
            Integer targetYear,
            String type,
            String growthType,
            BigDecimal growthPct,
            String indexLabel,
            Integer priority
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoalReorderRequest(List<UUID> goalIds) {
    }
}
