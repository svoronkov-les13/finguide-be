package les13.finguide.backend.plans;

import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.budget.BudgetSettings;
import les13.finguide.backend.contributions.Contribution;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.users.UserProfile;

import java.time.Instant;
import java.util.List;

public record PlanState(
        FinancialPlan plan,
        UserProfile profile,
        PensionSettings pension,
        List<IncomeSource> incomes,
        List<ExpenseItem> expenses,
        List<Goal> goals,
        List<Contribution> contributions,
        BudgetSettings budget,
        ModelAssumptions modelAssumptions,
        Instant updatedAt
) {
}
