package les13.finguide.backend.plans;

import les13.finguide.backend.contributions.Contribution;
import les13.finguide.backend.budget.BudgetEnvelope;
import les13.finguide.backend.budget.BudgetSettings;
import les13.finguide.backend.budget.MonthlyTrackerEntry;
import les13.finguide.backend.budget.OperationJournalEntry;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.scenarios.Scenario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanStateRepository {
    Optional<PlanState> findCurrent();

    Optional<PlanState> findCurrentForOwner(UUID ownerUserId);

    PlanState findOrCreateCurrentForOwner(UUID ownerUserId);

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

    List<Contribution> findContributions(UUID planId);

    Optional<Contribution> findContribution(UUID planId, UUID contributionId);

    Contribution createContribution(UUID planId, Contribution contribution);

    Contribution updateContribution(UUID planId, Contribution contribution);

    boolean deleteContribution(UUID planId, UUID contributionId);

    void recalculateGoalSavedAmount(UUID planId, UUID goalId);

    BudgetSettings findBudget(UUID planId);

    BudgetSettings replaceBudget(UUID planId, BudgetSettings budget);

    BudgetSettings replaceBudgetEnvelopes(UUID planId, List<BudgetEnvelope> envelopes);

    List<MonthlyTrackerEntry> findMonthlyTrackerEntries(UUID planId, int year);

    MonthlyTrackerEntry upsertMonthlyTrackerEntry(UUID planId, MonthlyTrackerEntry entry);

    List<OperationJournalEntry> findOperationJournalEntries(UUID planId, Integer year, Integer month);

    Optional<OperationJournalEntry> findOperationJournalEntry(UUID planId, UUID entryId);

    OperationJournalEntry createOperationJournalEntry(OperationJournalEntry entry);

    OperationJournalEntry updateOperationJournalEntry(OperationJournalEntry entry);

    boolean deleteOperationJournalEntry(UUID planId, UUID entryId);

    List<Scenario> findScenarios(UUID planId);

    Optional<Scenario> findScenario(UUID planId, UUID scenarioId);

    Scenario createScenario(Scenario scenario);

    Scenario updateScenario(Scenario scenario);

    boolean deleteScenario(UUID planId, UUID scenarioId);

    ModelAssumptions updateModelAssumptions(UUID planId, ModelAssumptions assumptions);

    PensionSettings updatePensionSettings(UUID planId, PensionSettings pension);
}
