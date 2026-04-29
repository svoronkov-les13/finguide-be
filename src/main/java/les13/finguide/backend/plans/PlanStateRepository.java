package les13.finguide.backend.plans;

import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanStateRepository {
    Optional<PlanState> findCurrent();

    Optional<PlanState> findCurrentForOwner(UUID ownerUserId);

    Optional<PlanState> findById(UUID planId);

    Optional<UUID> findOwnerUserId(UUID planId);

    List<IncomeSource> findIncomes(UUID planId);

    Optional<IncomeSource> findIncome(UUID planId, UUID incomeId);

    IncomeSource createIncome(IncomeSource income);

    IncomeSource updateIncome(IncomeSource income);

    boolean deleteIncome(UUID planId, UUID incomeId);

    List<ExpenseItem> findExpenses(UUID planId);

    Optional<ExpenseItem> findExpense(UUID planId, UUID expenseId);

    ExpenseItem createExpense(ExpenseItem expense);

    ExpenseItem updateExpense(ExpenseItem expense);

    boolean deleteExpense(UUID planId, UUID expenseId);

    List<Goal> findGoals(UUID planId);

    Optional<Goal> findGoal(UUID planId, UUID goalId);

    Goal createGoal(Goal goal);

    Goal updateGoal(Goal goal);

    boolean deleteGoal(UUID planId, UUID goalId);

    List<Goal> reorderGoals(UUID planId, List<UUID> goalIds);
}
