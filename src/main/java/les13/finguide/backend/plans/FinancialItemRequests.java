package les13.finguide.backend.plans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class FinancialItemRequests {
    private FinancialItemRequests() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "Income create/patch request. For PATCH, omitted fields keep current values; nullable fields set to null are treated as unchanged in the current H2 implementation.")
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
    @Schema(description = "Expense create/patch request. For PATCH, omitted fields keep current values; nullable fields set to null are treated as unchanged in the current H2 implementation.")
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
    @Schema(description = "Goal create/patch request. For PATCH, omitted fields keep current values; nullable fields set to null are treated as unchanged in the current H2 implementation.")
    record GoalRequest(
            String id,
            String name,
            String icon,
            BigDecimal currentCost,
            BigDecimal savedAmount,
            String currency,
            @Schema(minimum = "2024", description = "Target year, aligned with the public OpenAPI contract minimum.")
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
