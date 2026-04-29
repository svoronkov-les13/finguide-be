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
