package les13.finguide.backend.plans;

import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.analytics.YearRatePoint;
import les13.finguide.backend.budget.BudgetSettings;
import les13.finguide.backend.contributions.Contribution;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.users.UserProfile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPlanStateRepository implements PlanStateRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcPlanStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PlanState> findCurrent() {
        try {
            UUID planId = jdbcTemplate.queryForObject(
                    "select id from financial_plans order by created_at limit 1",
                    UUID.class
            );
            return planId == null ? Optional.empty() : findById(planId);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PlanState> findById(UUID planId) {
        try {
            FinancialPlan plan = jdbcTemplate.queryForObject(
                    "select * from financial_plans where id = ?",
                    this::mapPlan,
                    planId
            );
            if (plan == null) {
                return Optional.empty();
            }
            UserProfile profile = jdbcTemplate.queryForObject(
                    "select up.* from user_profiles up join financial_plans fp on fp.owner_user_id = up.id where fp.id = ?",
                    this::mapProfile,
                    planId
            );
            PensionSettings pension = jdbcTemplate.queryForObject(
                    "select * from pension_settings where plan_id = ?",
                    this::mapPension,
                    planId
            );
            ModelAssumptions assumptions = jdbcTemplate.queryForObject(
                    "select * from model_assumptions where plan_id = ?",
                    (rs, rowNum) -> mapAssumptions(rs, loadInflationRates(planId)),
                    planId
            );
            List<IncomeSource> incomes = jdbcTemplate.query(
                    "select * from incomes where plan_id = ? order by sort_order, name",
                    this::mapIncome,
                    planId
            );
            List<ExpenseItem> expenses = jdbcTemplate.query(
                    "select * from expenses where plan_id = ? order by sort_order, name",
                    this::mapExpense,
                    planId
            );
            List<Goal> goals = jdbcTemplate.query(
                    "select * from goals where plan_id = ? order by priority, name",
                    this::mapGoal,
                    planId
            );
            BudgetSettings budget = new BudgetSettings(planId, BudgetSettings.Method.RULE_50_30_20, List.of(), Map.of());
            return Optional.of(new PlanState(
                    plan,
                    profile,
                    pension,
                    incomes,
                    expenses,
                    goals,
                    List.<Contribution>of(),
                    budget,
                    assumptions,
                    plan.updatedAt()
            ));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }


    @Override
    public List<IncomeSource> findIncomes(UUID planId) {
        return jdbcTemplate.query(
                "select * from incomes where plan_id = ? order by sort_order, name",
                this::mapIncome,
                planId
        );
    }

    @Override
    public Optional<IncomeSource> findIncome(UUID planId, UUID incomeId) {
        return queryOptional(
                "select * from incomes where plan_id = ? and id = ?",
                this::mapIncome,
                planId,
                incomeId
        );
    }

    @Override
    public IncomeSource createIncome(IncomeSource income) {
        OffsetDateTime now = offset(income.updatedAt());
        jdbcTemplate.update(
                "insert into incomes (id, plan_id, name, amount, currency, frequency, growth_type, growth_pct, start_date, end_date, sort_order, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                income.id(),
                income.planId(),
                income.name(),
                income.amount(),
                income.currency(),
                dbValue(income.frequency()),
                dbValue(income.growthType()),
                income.growthPct(),
                income.startDate(),
                income.endDate(),
                nextSortOrder("incomes", income.planId()),
                offset(income.createdAt()),
                now
        );
        touchPlan(income.planId(), now);
        return findIncome(income.planId(), income.id()).orElseThrow();
    }

    @Override
    public IncomeSource updateIncome(IncomeSource income) {
        OffsetDateTime now = offset(income.updatedAt());
        jdbcTemplate.update(
                "update incomes set name = ?, amount = ?, currency = ?, frequency = ?, growth_type = ?, growth_pct = ?, start_date = ?, end_date = ?, updated_at = ? where plan_id = ? and id = ?",
                income.name(),
                income.amount(),
                income.currency(),
                dbValue(income.frequency()),
                dbValue(income.growthType()),
                income.growthPct(),
                income.startDate(),
                income.endDate(),
                now,
                income.planId(),
                income.id()
        );
        touchPlan(income.planId(), now);
        return findIncome(income.planId(), income.id()).orElseThrow();
    }

    @Override
    public boolean deleteIncome(UUID planId, UUID incomeId) {
        int deleted = jdbcTemplate.update("delete from incomes where plan_id = ? and id = ?", planId, incomeId);
        if (deleted > 0) {
            touchPlan(planId, OffsetDateTime.now(ZoneOffset.UTC));
        }
        return deleted > 0;
    }

    @Override
    public List<ExpenseItem> findExpenses(UUID planId) {
        return jdbcTemplate.query(
                "select * from expenses where plan_id = ? order by sort_order, name",
                this::mapExpense,
                planId
        );
    }

    @Override
    public Optional<ExpenseItem> findExpense(UUID planId, UUID expenseId) {
        return queryOptional(
                "select * from expenses where plan_id = ? and id = ?",
                this::mapExpense,
                planId,
                expenseId
        );
    }

    @Override
    public ExpenseItem createExpense(ExpenseItem expense) {
        OffsetDateTime now = offset(expense.updatedAt());
        jdbcTemplate.update(
                "insert into expenses (id, plan_id, name, amount, currency, frequency, growth_type, growth_pct, growth_label, budget_class, start_date, end_date, sort_order, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                expense.id(),
                expense.planId(),
                expense.name(),
                expense.amount(),
                expense.currency(),
                dbValue(expense.frequency()),
                dbValue(expense.growthType()),
                expense.growthPct(),
                expense.growthLabel(),
                dbValue(expense.budgetClass()),
                expense.startDate(),
                expense.endDate(),
                nextSortOrder("expenses", expense.planId()),
                offset(expense.createdAt()),
                now
        );
        touchPlan(expense.planId(), now);
        return findExpense(expense.planId(), expense.id()).orElseThrow();
    }

    @Override
    public ExpenseItem updateExpense(ExpenseItem expense) {
        OffsetDateTime now = offset(expense.updatedAt());
        jdbcTemplate.update(
                "update expenses set name = ?, amount = ?, currency = ?, frequency = ?, growth_type = ?, growth_pct = ?, growth_label = ?, budget_class = ?, start_date = ?, end_date = ?, updated_at = ? where plan_id = ? and id = ?",
                expense.name(),
                expense.amount(),
                expense.currency(),
                dbValue(expense.frequency()),
                dbValue(expense.growthType()),
                expense.growthPct(),
                expense.growthLabel(),
                dbValue(expense.budgetClass()),
                expense.startDate(),
                expense.endDate(),
                now,
                expense.planId(),
                expense.id()
        );
        touchPlan(expense.planId(), now);
        return findExpense(expense.planId(), expense.id()).orElseThrow();
    }

    @Override
    public boolean deleteExpense(UUID planId, UUID expenseId) {
        int deleted = jdbcTemplate.update("delete from expenses where plan_id = ? and id = ?", planId, expenseId);
        if (deleted > 0) {
            touchPlan(planId, OffsetDateTime.now(ZoneOffset.UTC));
        }
        return deleted > 0;
    }

    @Override
    public List<Goal> findGoals(UUID planId) {
        return jdbcTemplate.query(
                "select * from goals where plan_id = ? order by priority, name",
                this::mapGoal,
                planId
        );
    }

    @Override
    public Optional<Goal> findGoal(UUID planId, UUID goalId) {
        return queryOptional(
                "select * from goals where plan_id = ? and id = ?",
                this::mapGoal,
                planId,
                goalId
        );
    }

    @Override
    public Goal createGoal(Goal goal) {
        OffsetDateTime now = offset(goal.updatedAt());
        jdbcTemplate.update(
                "insert into goals (id, plan_id, name, icon, current_cost, saved_amount, currency, target_year, type, growth_type, growth_pct, index_label, priority, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                goal.id(),
                goal.planId(),
                goal.name(),
                goal.icon(),
                goal.currentCost(),
                goal.savedAmount(),
                goal.currency(),
                goal.targetYear(),
                dbValue(goal.type()),
                dbValue(goal.growthType()),
                goal.growthPct(),
                goal.indexLabel(),
                goal.priority(),
                offset(goal.createdAt()),
                now
        );
        touchPlan(goal.planId(), now);
        return findGoal(goal.planId(), goal.id()).orElseThrow();
    }

    @Override
    public Goal updateGoal(Goal goal) {
        OffsetDateTime now = offset(goal.updatedAt());
        jdbcTemplate.update(
                "update goals set name = ?, icon = ?, current_cost = ?, saved_amount = ?, currency = ?, target_year = ?, type = ?, growth_type = ?, growth_pct = ?, index_label = ?, priority = ?, updated_at = ? where plan_id = ? and id = ?",
                goal.name(),
                goal.icon(),
                goal.currentCost(),
                goal.savedAmount(),
                goal.currency(),
                goal.targetYear(),
                dbValue(goal.type()),
                dbValue(goal.growthType()),
                goal.growthPct(),
                goal.indexLabel(),
                goal.priority(),
                now,
                goal.planId(),
                goal.id()
        );
        touchPlan(goal.planId(), now);
        return findGoal(goal.planId(), goal.id()).orElseThrow();
    }

    @Override
    public boolean deleteGoal(UUID planId, UUID goalId) {
        int deleted = jdbcTemplate.update("delete from goals where plan_id = ? and id = ?", planId, goalId);
        if (deleted > 0) {
            touchPlan(planId, OffsetDateTime.now(ZoneOffset.UTC));
        }
        return deleted > 0;
    }

    @Override
    public List<Goal> reorderGoals(UUID planId, List<UUID> goalIds) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (int index = 0; index < goalIds.size(); index++) {
            jdbcTemplate.update(
                    "update goals set priority = ?, updated_at = ? where plan_id = ? and id = ?",
                    index + 1,
                    now,
                    planId,
                    goalIds.get(index)
            );
        }
        touchPlan(planId, now);
        return findGoals(planId);
    }

    private <T> Optional<T> queryOptional(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, mapper, args));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private int nextSortOrder(String tableName, UUID planId) {
        Integer value = jdbcTemplate.queryForObject("select coalesce(max(sort_order), 0) + 1 from " + tableName + " where plan_id = ?", Integer.class, planId);
        return value == null ? 1 : value;
    }

    private void touchPlan(UUID planId, OffsetDateTime updatedAt) {
        jdbcTemplate.update("update financial_plans set updated_at = ? where id = ?", updatedAt, planId);
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String dbValue(Enum<?> value) {
        return value.name().toLowerCase();
    }


    private FinancialPlan mapPlan(ResultSet rs, int rowNum) throws SQLException {
        return new FinancialPlan(
                rs.getObject("id", UUID.class),
                rs.getObject("owner_user_id", UUID.class),
                rs.getString("name"),
                rs.getString("base_currency"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private UserProfile mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new UserProfile(
                rs.getObject("id", UUID.class),
                rs.getString("keycloak_subject"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getString("phone"),
                rs.getString("avatar_url"),
                integer(rs, "age"),
                enumValue(UserProfile.Gender.class, rs.getString("gender")),
                rs.getBigDecimal("initial_balance"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private PensionSettings mapPension(ResultSet rs, int rowNum) throws SQLException {
        return new PensionSettings(
                rs.getObject("plan_id", UUID.class),
                rs.getInt("current_age"),
                rs.getInt("retirement_age"),
                rs.getBigDecimal("monthly_expenses"),
                rs.getBigDecimal("desired_monthly_expenses_current_prices"),
                rs.getString("currency"),
                rs.getBigDecimal("expected_return_pct"),
                rs.getBigDecimal("inflation_pct"),
                enumValue(PensionSettings.WithdrawalStrategy.class, rs.getString("withdrawal_strategy")),
                rs.getBoolean("state_pension_enabled"),
                rs.getBigDecimal("state_pension_monthly")
        );
    }

    private ModelAssumptions mapAssumptions(ResultSet rs, List<YearRatePoint> inflationRates) throws SQLException {
        return new ModelAssumptions(
                rs.getInt("start_year"),
                integer(rs, "projection_end_year"),
                integer(rs, "horizon_years"),
                integer(rs, "birth_year"),
                rs.getInt("months_per_year"),
                rs.getString("currency"),
                rs.getBigDecimal("initial_capital"),
                rs.getBigDecimal("investment_return_pct"),
                inflationRates,
                rs.getString("source_model")
        );
    }

    private List<YearRatePoint> loadInflationRates(UUID planId) {
        return jdbcTemplate.query(
                "select * from inflation_rates where plan_id = ? order by rate_year",
                (rs, rowNum) -> new YearRatePoint(rs.getInt("rate_year"), rs.getBigDecimal("rate_pct")),
                planId
        );
    }

    private IncomeSource mapIncome(ResultSet rs, int rowNum) throws SQLException {
        return new IncomeSource(
                rs.getObject("id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getString("name"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                enumValue(IncomeSource.Frequency.class, rs.getString("frequency")),
                enumValue(IncomeSource.GrowthType.class, rs.getString("growth_type")),
                rs.getBigDecimal("growth_pct"),
                localDate(rs, "start_date"),
                localDate(rs, "end_date"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private ExpenseItem mapExpense(ResultSet rs, int rowNum) throws SQLException {
        return new ExpenseItem(
                rs.getObject("id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getString("name"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                enumValue(ExpenseItem.Frequency.class, rs.getString("frequency")),
                enumValue(ExpenseItem.GrowthType.class, rs.getString("growth_type")),
                rs.getBigDecimal("growth_pct"),
                rs.getString("growth_label"),
                enumValue(ExpenseItem.BudgetClass.class, rs.getString("budget_class")),
                localDate(rs, "start_date"),
                localDate(rs, "end_date"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private Goal mapGoal(ResultSet rs, int rowNum) throws SQLException {
        return new Goal(
                rs.getObject("id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getString("name"),
                rs.getString("icon"),
                rs.getBigDecimal("current_cost"),
                rs.getBigDecimal("saved_amount"),
                rs.getString("currency"),
                rs.getInt("target_year"),
                enumValue(Goal.Type.class, rs.getString("type")),
                enumValue(Goal.GrowthType.class, rs.getString("growth_type")),
                rs.getBigDecimal("growth_pct"),
                rs.getString("index_label"),
                rs.getInt("priority"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static LocalDate localDate(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, value.trim().toUpperCase().replace('-', '_'));
    }
}
