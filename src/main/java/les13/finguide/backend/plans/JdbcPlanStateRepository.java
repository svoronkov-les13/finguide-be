package les13.finguide.backend.plans;

import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.analytics.YearRatePoint;
import les13.finguide.backend.budget.BudgetEnvelope;
import les13.finguide.backend.budget.BudgetSettings;
import les13.finguide.backend.budget.MonthlyTrackerEntry;
import les13.finguide.backend.budget.OperationJournalEntry;
import les13.finguide.backend.contributions.Contribution;
import les13.finguide.backend.expenses.ExpenseItem;
import les13.finguide.backend.goals.Goal;
import les13.finguide.backend.incomes.IncomeSource;
import les13.finguide.backend.pension.PensionSettings;
import les13.finguide.backend.scenarios.Scenario;
import les13.finguide.backend.users.UserProfile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcPlanStateRepository implements PlanStateRepository {
    private static final UUID SEEDED_DEMO_PLAN_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final JdbcTemplate jdbcTemplate;

    public JdbcPlanStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PlanState> findCurrent() {
        return findSeedDemoPlanId().flatMap(this::findById);
    }

    @Override
    public Optional<PlanState> findCurrentForOwner(UUID ownerUserId) {
        Optional<UUID> selectedPlanId = queryOptional(
                "select current_plan_id from user_profiles where id = ?",
                (rs, rowNum) -> rs.getObject("current_plan_id", UUID.class),
                ownerUserId
        );
        if (selectedPlanId.isPresent()) {
            Optional<PlanState> selected = findById(selectedPlanId.get())
                    .filter(state -> ownerUserId.equals(state.plan().ownerUserId()));
            if (selected.isPresent()) {
                return selected;
            }
        }
        return queryOptional(
                "select id from financial_plans where owner_user_id = ? order by updated_at desc, created_at desc limit 1",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                ownerUserId
        ).flatMap(this::findById);
    }

    @Override
    @Transactional
    public PlanState findOrCreateCurrentForOwner(UUID ownerUserId) {
        return findCurrentForOwner(ownerUserId).orElseGet(() -> createCurrentForOwner(ownerUserId));
    }

    @Override
    public List<PlanSummary> findPlansForOwner(UUID ownerUserId) {
        Optional<UUID> currentPlanId = queryOptional(
                "select current_plan_id from user_profiles where id = ?",
                (rs, rowNum) -> rs.getObject("current_plan_id", UUID.class),
                ownerUserId
        );
        return jdbcTemplate.query(
                "select id, name, created_at, updated_at from financial_plans where owner_user_id = ? order by updated_at desc, created_at desc",
                (rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    return new PlanSummary(
                            id,
                            rs.getString("name"),
                            currentPlanId.filter(current -> current.equals(id)).isPresent(),
                            instant(rs, "created_at"),
                            instant(rs, "updated_at")
                    );
                },
                ownerUserId
        );
    }

    @Override
    @Transactional
    public PlanState createBlankPlanForOwner(UUID ownerUserId, String name) {
        Optional<UUID> seedPlanId = findSeedDemoPlanId();
        if (seedPlanId.isEmpty()) {
            UUID planId = createEmptyPlanForOwner(ownerUserId, name);
            setCurrentPlanForOwner(ownerUserId, planId);
            return findById(planId).orElseThrow();
        }
        UUID planId = createPlanRowFromSource(ownerUserId, seedPlanId.get(), name);
        clonePensionSettings(seedPlanId.get(), planId);
        cloneModelAssumptions(seedPlanId.get(), planId);
        cloneInflationRates(seedPlanId.get(), planId);
        cloneBudgetSettings(seedPlanId.get(), planId);
        setCurrentPlanForOwner(ownerUserId, planId);
        return findById(planId).orElseThrow();
    }

    @Override
    @Transactional
    public PlanState copyPlanForOwner(UUID ownerUserId, UUID sourcePlanId, String name) {
        PlanState source = findById(sourcePlanId)
                .filter(state -> ownerUserId.equals(state.plan().ownerUserId()))
                .orElseThrow(() -> new EmptyResultDataAccessException("Source plan was not found", 1));
        UUID planId = createPlanRowFromSource(ownerUserId, source.plan().id(), name);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        clonePensionSettings(source.plan().id(), planId);
        cloneModelAssumptions(source.plan().id(), planId);
        cloneInflationRates(source.plan().id(), planId);
        cloneIncomes(source.plan().id(), planId, now);
        cloneExpenses(source.plan().id(), planId, now);
        cloneGoals(source.plan().id(), planId, now);
        cloneBudget(source.plan().id(), planId, now);
        cloneScenarios(source.plan().id(), planId, now);
        setCurrentPlanForOwner(ownerUserId, planId);
        return findById(planId).orElseThrow();
    }

    @Override
    @Transactional
    public boolean setCurrentPlanForOwner(UUID ownerUserId, UUID planId) {
        int updated = jdbcTemplate.update(
                "update user_profiles set current_plan_id = ?, updated_at = ? where id = ? and exists (select 1 from financial_plans where id = ? and owner_user_id = ?)",
                planId,
                OffsetDateTime.now(ZoneOffset.UTC),
                ownerUserId,
                planId,
                ownerUserId
        );
        return updated > 0;
    }

    private PlanState createCurrentForOwner(UUID ownerUserId) {
        Optional<UUID> seedPlanId = findSeedDemoPlanId();
        if (seedPlanId.isEmpty()) {
            UUID planId = createEmptyPlanForOwner(ownerUserId, "Мой план");
            setCurrentPlanForOwner(ownerUserId, planId);
            return findById(planId).orElseThrow();
        }
        UUID planId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            jdbcTemplate.update(
                    "insert into financial_plans (id, owner_user_id, name, base_currency, created_at, updated_at) " +
                            "select ?, ?, name, base_currency, ?, ? from financial_plans where id = ?",
                    planId,
                    ownerUserId,
                    now,
                    now,
                    seedPlanId.get()
            );
            clonePensionSettings(seedPlanId.get(), planId);
            cloneModelAssumptions(seedPlanId.get(), planId);
            cloneInflationRates(seedPlanId.get(), planId);
            cloneIncomes(seedPlanId.get(), planId, now);
            cloneExpenses(seedPlanId.get(), planId, now);
            cloneGoals(seedPlanId.get(), planId, now);
            cloneBudget(seedPlanId.get(), planId, now);
            cloneMonthlyTracker(seedPlanId.get(), planId, now);
            cloneOperationJournal(seedPlanId.get(), planId, now);
            cloneScenarios(seedPlanId.get(), planId, now);
            setCurrentPlanForOwner(ownerUserId, planId);
            return findById(planId).orElseThrow();
        } catch (DuplicateKeyException ignored) {
            return findCurrentForOwner(ownerUserId).orElseThrow();
        }
    }

    private UUID createEmptyPlanForOwner(UUID ownerUserId, String name) {
        UUID planId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "insert into financial_plans (id, owner_user_id, name, base_currency, created_at, updated_at) values (?, ?, ?, ?, ?, ?)",
                planId,
                ownerUserId,
                name,
                "RUB",
                now,
                now
        );
        insertDefaultPensionSettings(planId);
        insertDefaultModelAssumptions(planId, ownerUserId);
        return planId;
    }

    private void insertDefaultPensionSettings(UUID planId) {
        jdbcTemplate.update(
                "insert into pension_settings (plan_id, current_age, retirement_age, monthly_expenses, desired_monthly_expenses_current_prices, currency, expected_return_pct, inflation_pct, withdrawal_strategy, state_pension_enabled, state_pension_monthly) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                planId,
                30,
                60,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "RUB",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                dbValue(PensionSettings.WithdrawalStrategy.PRESERVE_CAPITAL),
                false,
                BigDecimal.ZERO
        );
    }

    private void insertDefaultModelAssumptions(UUID planId, UUID ownerUserId) {
        jdbcTemplate.update(
                "insert into model_assumptions (plan_id, start_year, projection_end_year, horizon_years, birth_year, months_per_year, currency, initial_capital, investment_return_pct, source_model) " +
                        "values (?, ?, null, ?, null, ?, ?, ?, ?, ?)",
                planId,
                Year.now(ZoneOffset.UTC).getValue(),
                30,
                12,
                "RUB",
                initialCapitalForOwner(ownerUserId),
                BigDecimal.ZERO,
                "empty-plan-fallback"
        );
    }

    private BigDecimal initialCapitalForOwner(UUID ownerUserId) {
        return queryOptional(
                "select initial_balance from user_profiles where id = ?",
                (rs, rowNum) -> rs.getBigDecimal("initial_balance"),
                ownerUserId
        ).orElse(BigDecimal.ZERO);
    }

    private UUID createPlanRowFromSource(UUID ownerUserId, UUID sourcePlanId, String name) {
        UUID planId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "insert into financial_plans (id, owner_user_id, name, base_currency, created_at, updated_at) " +
                        "select ?, ?, ?, base_currency, ?, ? from financial_plans where id = ?",
                planId,
                ownerUserId,
                name,
                now,
                now,
                sourcePlanId
        );
        return planId;
    }

    private Optional<UUID> findSeedDemoPlanId() {
        return queryOptional(
                "select id from financial_plans where id = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                SEEDED_DEMO_PLAN_ID
        );
    }

    private void clonePensionSettings(UUID seedPlanId, UUID planId) {
        jdbcTemplate.update(
                "insert into pension_settings (plan_id, current_age, retirement_age, monthly_expenses, desired_monthly_expenses_current_prices, currency, expected_return_pct, inflation_pct, withdrawal_strategy, state_pension_enabled, state_pension_monthly) " +
                        "select ?, current_age, retirement_age, monthly_expenses, desired_monthly_expenses_current_prices, currency, expected_return_pct, inflation_pct, withdrawal_strategy, state_pension_enabled, state_pension_monthly from pension_settings where plan_id = ?",
                planId,
                seedPlanId
        );
    }

    private void cloneModelAssumptions(UUID seedPlanId, UUID planId) {
        jdbcTemplate.update(
                "insert into model_assumptions (plan_id, start_year, projection_end_year, horizon_years, birth_year, months_per_year, currency, initial_capital, investment_return_pct, source_model) " +
                        "select ?, start_year, projection_end_year, horizon_years, birth_year, months_per_year, currency, initial_capital, investment_return_pct, source_model from model_assumptions where plan_id = ?",
                planId,
                seedPlanId
        );
    }

    private void cloneInflationRates(UUID seedPlanId, UUID planId) {
        jdbcTemplate.update(
                "insert into inflation_rates (plan_id, rate_year, rate_pct) select ?, rate_year, rate_pct from inflation_rates where plan_id = ?",
                planId,
                seedPlanId
        );
    }

    private void cloneIncomes(UUID seedPlanId, UUID planId, OffsetDateTime now) {
        jdbcTemplate.query(
                "select * from incomes where plan_id = ? order by sort_order, name",
                rs -> {
                    jdbcTemplate.update(
                            "insert into incomes (id, plan_id, name, amount, currency, frequency, growth_type, growth_pct, start_date, end_date, sort_order, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(),
                            planId,
                            rs.getString("name"),
                            rs.getBigDecimal("amount"),
                            rs.getString("currency"),
                            rs.getString("frequency"),
                            rs.getString("growth_type"),
                            rs.getBigDecimal("growth_pct"),
                            localDate(rs, "start_date"),
                            localDate(rs, "end_date"),
                            rs.getInt("sort_order"),
                            now,
                            now
                    );
                },
                seedPlanId
        );
    }

    private void cloneExpenses(UUID seedPlanId, UUID planId, OffsetDateTime now) {
        jdbcTemplate.query(
                "select * from expenses where plan_id = ? order by sort_order, name",
                rs -> {
                    jdbcTemplate.update(
                            "insert into expenses (id, plan_id, name, amount, currency, frequency, growth_type, growth_pct, growth_label, budget_class, start_date, end_date, sort_order, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(),
                            planId,
                            rs.getString("name"),
                            rs.getBigDecimal("amount"),
                            rs.getString("currency"),
                            rs.getString("frequency"),
                            rs.getString("growth_type"),
                            rs.getBigDecimal("growth_pct"),
                            rs.getString("growth_label"),
                            rs.getString("budget_class"),
                            localDate(rs, "start_date"),
                            localDate(rs, "end_date"),
                            rs.getInt("sort_order"),
                            now,
                            now
                    );
                },
                seedPlanId
        );
    }

    private void cloneGoals(UUID seedPlanId, UUID planId, OffsetDateTime now) {
        jdbcTemplate.query(
                "select * from goals where plan_id = ? order by priority, name",
                rs -> {
                    jdbcTemplate.update(
                            "insert into goals (id, plan_id, name, icon, current_cost, saved_amount, currency, target_year, target_month, type, growth_type, growth_pct, index_label, priority, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            UUID.randomUUID(),
                            planId,
                            rs.getString("name"),
                            rs.getString("icon"),
                            rs.getBigDecimal("current_cost"),
                            java.math.BigDecimal.ZERO,
                            rs.getString("currency"),
                            rs.getInt("target_year"),
                            rs.getInt("target_month"),
                            rs.getString("type"),
                            rs.getString("growth_type"),
                            rs.getBigDecimal("growth_pct"),
                            rs.getString("index_label"),
                            rs.getInt("priority"),
                            now,
                            now
                    );
                },
                seedPlanId
        );
    }

    private void cloneBudget(UUID seedPlanId, UUID planId, OffsetDateTime now) {
        cloneBudgetSettings(seedPlanId, planId);
        jdbcTemplate.update("update budget_settings set created_at = ?, updated_at = ? where plan_id = ?", now, now, planId);
        jdbcTemplate.update(
                "insert into budget_envelopes (id, plan_id, name, limit_amount, icon, color, sort_order, created_at, updated_at) " +
                        "select random_uuid(), ?, name, limit_amount, icon, color, sort_order, ?, ? from budget_envelopes where plan_id = ?",
                planId,
                now,
                now,
                seedPlanId
        );
        jdbcTemplate.update(
                "insert into budget_classifications (plan_id, expense_id, budget_class, created_at, updated_at) " +
                        "select ?, e2.id, bc.budget_class, ?, ? " +
                        "from budget_classifications bc " +
                        "join expenses e1 on e1.id = bc.expense_id " +
                        "join expenses e2 on e2.plan_id = ? and e2.name = e1.name and e2.amount = e1.amount and e2.budget_class = e1.budget_class " +
                        "where bc.plan_id = ?",
                planId,
                now,
                now,
                planId,
                seedPlanId
        );
    }

    private void cloneBudgetSettings(UUID seedPlanId, UUID planId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "insert into budget_settings (plan_id, method, created_at, updated_at) select ?, method, ?, ? from budget_settings where plan_id = ?",
                planId,
                now,
                now,
                seedPlanId
        );
    }

    private void cloneMonthlyTracker(UUID seedPlanId, UUID planId, OffsetDateTime now) {
        jdbcTemplate.update(
                "insert into monthly_tracker_entries (plan_id, tracker_month, status, amount, note, created_at, updated_at) select ?, tracker_month, status, amount, note, ?, ? from monthly_tracker_entries where plan_id = ?",
                planId,
                now,
                now,
                seedPlanId
        );
    }

    private void cloneOperationJournal(UUID seedPlanId, UUID planId, OffsetDateTime now) {
        jdbcTemplate.update(
                "insert into operation_journal_entries (id, plan_id, entry_date, title, amount, entry_type, status, created_at, updated_at) " +
                        "select random_uuid(), ?, entry_date, title, amount, entry_type, status, ?, ? from operation_journal_entries where plan_id = ?",
                planId,
                now,
                now,
                seedPlanId
        );
    }

    private void cloneScenarios(UUID seedPlanId, UUID planId, OffsetDateTime now) {
        jdbcTemplate.update(
                "insert into scenarios (id, plan_id, name, emoji, description, is_base, income_adj_pct, expense_adj_pct, return_adj_pct, inflation_adj_pct, retirement_age_shift, goals_cost_adj_pct, snapshot_json, created_at, updated_at) " +
                        "select random_uuid(), ?, name, emoji, description, is_base, income_adj_pct, expense_adj_pct, return_adj_pct, inflation_adj_pct, retirement_age_shift, goals_cost_adj_pct, snapshot_json, ?, ? from scenarios where plan_id = ?",
                planId,
                now,
                now,
                seedPlanId
        );
    }

    @Override
    public Optional<PlanState> findById(UUID planId) {
        try {
            List<YearRatePoint> inflationRates = loadInflationRates(planId);
            PlanCore core = jdbcTemplate.queryForObject(
                    "select" +
                    "  fp.id as fp_id, fp.owner_user_id, fp.name as fp_name, fp.base_currency," +
                    "  fp.created_at as fp_created_at, fp.updated_at as fp_updated_at," +
                    "  up.id as up_id, up.keycloak_subject, up.email, up.name as up_name," +
                    "  up.phone, up.avatar_url, up.age, up.gender, up.initial_balance," +
                    "  up.created_at as up_created_at, up.updated_at as up_updated_at," +
                    "  ps.current_age, ps.retirement_age, ps.monthly_expenses," +
                    "  ps.desired_monthly_expenses_current_prices, ps.currency as ps_currency," +
                    "  ps.expected_return_pct, ps.inflation_pct, ps.withdrawal_strategy," +
                    "  ps.state_pension_enabled, ps.state_pension_monthly," +
                    "  ma.start_year, ma.projection_end_year, ma.horizon_years, ma.birth_year," +
                    "  ma.months_per_year, ma.currency as ma_currency, ma.initial_capital," +
                    "  ma.investment_return_pct, ma.source_model" +
                    " from financial_plans fp" +
                    " join user_profiles up on fp.owner_user_id = up.id" +
                    " join pension_settings ps on ps.plan_id = fp.id" +
                    " join model_assumptions ma on ma.plan_id = fp.id" +
                    " where fp.id = ?",
                    (rs, rowNum) -> new PlanCore(
                            mapPlanJoin(rs),
                            mapProfileJoin(rs),
                            mapPensionJoin(rs),
                            mapAssumptionsJoin(rs, inflationRates)
                    ),
                    planId
            );
            if (core == null) {
                return Optional.empty();
            }
            List<IncomeSource> incomes = jdbcTemplate.query(
                    "select * from incomes where plan_id = ? order by sort_order, name",
                    this::mapIncome, planId);
            List<ExpenseItem> expenses = jdbcTemplate.query(
                    "select * from expenses where plan_id = ? order by sort_order, name",
                    this::mapExpense, planId);
            List<Goal> goals = jdbcTemplate.query(
                    "select * from goals where plan_id = ? order by priority, name",
                    this::mapGoal, planId);
            List<Contribution> contributions = jdbcTemplate.query(
                    "select * from contributions where plan_id = ? order by contribution_date desc, created_at desc",
                    this::mapContribution, planId);
            BudgetSettings budget = findBudget(planId, expenses);
            return Optional.of(new PlanState(
                    core.plan(), core.profile(), core.pension(),
                    incomes, expenses, goals, contributions,
                    budget, core.assumptions(), core.plan().updatedAt()
            ));
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }


    @Override
    public Optional<UUID> findOwnerUserId(UUID planId) {
        return queryOptional(
                "select owner_user_id from financial_plans where id = ?",
                (rs, rowNum) -> rs.getObject("owner_user_id", UUID.class),
                planId
        );
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
        jdbcTemplate.update("delete from budget_classifications where plan_id = ? and expense_id = ?", planId, expenseId);
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
                "insert into goals (id, plan_id, name, icon, current_cost, saved_amount, currency, target_year, target_month, type, growth_type, growth_pct, index_label, priority, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                goal.id(),
                goal.planId(),
                goal.name(),
                goal.icon(),
                goal.currentCost(),
                goal.savedAmount(),
                goal.currency(),
                goal.targetYear(),
                goal.targetMonth(),
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
                "update goals set name = ?, icon = ?, current_cost = ?, saved_amount = ?, currency = ?, target_year = ?, target_month = ?, type = ?, growth_type = ?, growth_pct = ?, index_label = ?, priority = ?, updated_at = ? where plan_id = ? and id = ?",
                goal.name(),
                goal.icon(),
                goal.currentCost(),
                goal.savedAmount(),
                goal.currency(),
                goal.targetYear(),
                goal.targetMonth(),
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
        jdbcTemplate.update("delete from contributions where plan_id = ? and goal_id = ?", planId, goalId);
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

    @Override
    public List<Contribution> findContributions(UUID planId) {
        return jdbcTemplate.query(
                "select * from contributions where plan_id = ? order by contribution_date desc, created_at desc",
                this::mapContribution,
                planId
        );
    }

    @Override
    public Optional<Contribution> findContribution(UUID planId, UUID contributionId) {
        return queryOptional(
                "select * from contributions where plan_id = ? and id = ?",
                this::mapContribution,
                planId,
                contributionId
        );
    }

    @Override
    public Contribution createContribution(UUID planId, Contribution contribution) {
        jdbcTemplate.update(
                "insert into contributions (id, plan_id, goal_id, amount, currency, contribution_date, note, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                contribution.id(),
                planId,
                contribution.goalId(),
                contribution.amount(),
                contribution.currency(),
                contribution.date(),
                contribution.note(),
                offset(contribution.createdAt()),
                offset(contribution.updatedAt())
        );
        touchPlan(planId, offset(contribution.updatedAt()));
        return contribution;
    }

    @Override
    public Contribution updateContribution(UUID planId, Contribution contribution) {
        jdbcTemplate.update(
                "update contributions set goal_id = ?, amount = ?, currency = ?, contribution_date = ?, note = ?, updated_at = ? where plan_id = ? and id = ?",
                contribution.goalId(),
                contribution.amount(),
                contribution.currency(),
                contribution.date(),
                contribution.note(),
                offset(contribution.updatedAt()),
                planId,
                contribution.id()
        );
        touchPlan(planId, offset(contribution.updatedAt()));
        return contribution;
    }

    @Override
    public boolean deleteContribution(UUID planId, UUID contributionId) {
        int deleted = jdbcTemplate.update("delete from contributions where plan_id = ? and id = ?", planId, contributionId);
        if (deleted > 0) {
            touchPlan(planId, OffsetDateTime.now(ZoneOffset.UTC));
        }
        return deleted > 0;
    }

    @Override
    public void recalculateGoalSavedAmount(UUID planId, UUID goalId) {
        List<GoalSavingsTarget> goals = jdbcTemplate.query(
                "select id, current_cost from goals where plan_id = ? order by target_year, target_month, priority, id",
                (rs, rowNum) -> new GoalSavingsTarget(rs.getObject("id", UUID.class), rs.getBigDecimal("current_cost")),
                planId
        );
        if (goals.isEmpty()) {
            return;
        }

        Map<UUID, BigDecimal> manualByGoal = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select goal_id, coalesce(sum(amount), 0) amount from contributions where plan_id = ? group by goal_id",
                (RowCallbackHandler) rs -> manualByGoal.put(rs.getObject("goal_id", UUID.class), rs.getBigDecimal("amount")),
                planId
        );
        BigDecimal journalGeneratedPool = queryOptional(
                "select coalesce(sum(amount), 0) from operation_journal_entries where plan_id = ? and entry_type = 'goal' and status = 'actual' and amount > 0",
                (rs, rowNum) -> rs.getBigDecimal(1),
                planId
        ).orElse(BigDecimal.ZERO);
        BigDecimal monthlyGeneratedPool = queryOptional(
                "select coalesce(sum(amount), 0) from monthly_tracker_entries where plan_id = ? and amount > 0",
                (rs, rowNum) -> rs.getBigDecimal(1),
                planId
        ).orElse(BigDecimal.ZERO);
        BigDecimal generatedPool = journalGeneratedPool.add(monthlyGeneratedPool);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BigDecimal overflow = generatedPool.max(BigDecimal.ZERO);
        List<Object[]> batchArgs = new ArrayList<>();
        for (GoalSavingsTarget goal : goals) {
            BigDecimal available = overflow.add(manualByGoal.getOrDefault(goal.id(), BigDecimal.ZERO).max(BigDecimal.ZERO));
            BigDecimal target = goal.currentCost().max(BigDecimal.ZERO);
            BigDecimal saved = available.min(target).setScale(2, RoundingMode.HALF_UP);
            overflow = available.subtract(saved).max(BigDecimal.ZERO);
            batchArgs.add(new Object[]{saved, now, planId, goal.id()});
        }
        jdbcTemplate.batchUpdate(
                "update goals set saved_amount = ?, updated_at = ? where plan_id = ? and id = ?",
                batchArgs
        );
        touchPlan(planId, now);
    }

    @Override
    public BudgetSettings findBudget(UUID planId) {
        BudgetSettings.Method method = queryOptional(
                "select method from budget_settings where plan_id = ?",
                (rs, rowNum) -> enumValue(BudgetSettings.Method.class, rs.getString("method")),
                planId
        ).orElse(BudgetSettings.Method.RULE_50_30_20);
        Map<UUID, BudgetSettings.BudgetClass> classifications = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select * from budget_classifications where plan_id = ? order by created_at, expense_id",
                (RowCallbackHandler) rs -> classifications.put(rs.getObject("expense_id", UUID.class), enumValue(BudgetSettings.BudgetClass.class, rs.getString("budget_class"))),
                planId
        );
        return new BudgetSettings(planId, method, findBudgetEnvelopes(planId), classifications);
    }

    private BudgetSettings findBudget(UUID planId, List<ExpenseItem> expenses) {
        BudgetSettings.Method[] method = {BudgetSettings.Method.RULE_50_30_20};
        Map<UUID, BudgetSettings.BudgetClass> classifications = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select bs.method, bc.expense_id, bc.budget_class" +
                " from budget_settings bs" +
                " left join budget_classifications bc on bc.plan_id = bs.plan_id" +
                " where bs.plan_id = ?" +
                " order by bc.created_at, bc.expense_id",
                (RowCallbackHandler) rs -> {
                    method[0] = enumValue(BudgetSettings.Method.class, rs.getString("method"));
                    UUID expenseId = rs.getObject("expense_id", UUID.class);
                    if (expenseId != null) {
                        classifications.put(expenseId, enumValue(BudgetSettings.BudgetClass.class, rs.getString("budget_class")));
                    }
                },
                planId
        );
        return new BudgetSettings(planId, method[0], findBudgetEnvelopes(planId, expenses), classifications);
    }

    @Override
    @Transactional
    public BudgetSettings replaceBudget(UUID planId, BudgetSettings budget) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        deleteBudget(planId);
        insertBudget(planId, budget.method(), budget.envelopes(), budget.classifications(), now);
        touchPlan(planId, now);
        return findBudget(planId);
    }

    @Override
    @Transactional
    public BudgetSettings replaceBudgetEnvelopes(UUID planId, List<BudgetEnvelope> envelopes) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        BudgetSettings current = findBudget(planId);
        jdbcTemplate.update("delete from budget_envelopes where plan_id = ?", planId);
        ensureBudgetSettings(planId, current.method(), now);
        insertBudgetEnvelopes(planId, envelopes, now);
        touchPlan(planId, now);
        return findBudget(planId);
    }

    @Override
    public List<MonthlyTrackerEntry> findMonthlyTrackerEntries(UUID planId, int year) {
        String prefix = String.valueOf(year) + "-%";
        return jdbcTemplate.query(
                "select * from monthly_tracker_entries where plan_id = ? and tracker_month like ? order by tracker_month",
                this::mapMonthlyTrackerEntry,
                planId,
                prefix
        );
    }

    @Override
    @Transactional
    public MonthlyTrackerEntry upsertMonthlyTrackerEntry(UUID planId, MonthlyTrackerEntry entry) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int updated = jdbcTemplate.update(
                "update monthly_tracker_entries set status = ?, amount = ?, note = ?, updated_at = ? where plan_id = ? and tracker_month = ?",
                dbValue(entry.status()),
                entry.amount(),
                entry.note(),
                now,
                planId,
                entry.month().toString()
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    "insert into monthly_tracker_entries (plan_id, tracker_month, status, amount, note, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                    planId,
                    entry.month().toString(),
                    dbValue(entry.status()),
                    entry.amount(),
                    entry.note(),
                    now,
                    now
            );
        }
        touchPlan(planId, now);
        return queryOptional(
                "select * from monthly_tracker_entries where plan_id = ? and tracker_month = ?",
                this::mapMonthlyTrackerEntry,
                planId,
                entry.month().toString()
        ).orElseThrow();
    }

    @Override
    public List<OperationJournalEntry> findOperationJournalEntries(UUID planId, Integer year, Integer month) {
        StringBuilder sql = new StringBuilder("select * from operation_journal_entries where plan_id = ?");
        if (year != null) {
            sql.append(" and extract(year from entry_date) = ?");
        }
        if (month != null) {
            sql.append(" and extract(month from entry_date) = ?");
        }
        sql.append(" order by entry_date desc, created_at desc");

        if (year != null && month != null) {
            return jdbcTemplate.query(sql.toString(), this::mapOperationJournalEntry, planId, year, month);
        }
        if (year != null) {
            return jdbcTemplate.query(sql.toString(), this::mapOperationJournalEntry, planId, year);
        }
        if (month != null) {
            return jdbcTemplate.query(sql.toString(), this::mapOperationJournalEntry, planId, month);
        }
        return jdbcTemplate.query(sql.toString(), this::mapOperationJournalEntry, planId);
    }

    @Override
    public Optional<OperationJournalEntry> findOperationJournalEntry(UUID planId, UUID entryId) {
        return queryOptional(
                "select * from operation_journal_entries where plan_id = ? and id = ?",
                this::mapOperationJournalEntry,
                planId,
                entryId
        );
    }

    @Override
    @Transactional
    public OperationJournalEntry createOperationJournalEntry(OperationJournalEntry entry) {
        OffsetDateTime now = offset(entry.updatedAt());
        jdbcTemplate.update(
                "insert into operation_journal_entries (id, plan_id, entry_date, title, amount, entry_type, status, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entry.id(),
                entry.planId(),
                entry.date(),
                entry.title(),
                entry.amount(),
                dbValue(entry.type()),
                dbValue(entry.status()),
                offset(entry.createdAt()),
                now
        );
        touchPlan(entry.planId(), now);
        return findOperationJournalEntry(entry.planId(), entry.id()).orElseThrow();
    }

    @Override
    @Transactional
    public OperationJournalEntry updateOperationJournalEntry(OperationJournalEntry entry) {
        OffsetDateTime now = offset(entry.updatedAt());
        jdbcTemplate.update(
                "update operation_journal_entries set entry_date = ?, title = ?, amount = ?, entry_type = ?, status = ?, updated_at = ? where plan_id = ? and id = ?",
                entry.date(),
                entry.title(),
                entry.amount(),
                dbValue(entry.type()),
                dbValue(entry.status()),
                now,
                entry.planId(),
                entry.id()
        );
        touchPlan(entry.planId(), now);
        return findOperationJournalEntry(entry.planId(), entry.id()).orElseThrow();
    }

    @Override
    @Transactional
    public boolean deleteOperationJournalEntry(UUID planId, UUID entryId) {
        int deleted = jdbcTemplate.update("delete from operation_journal_entries where plan_id = ? and id = ?", planId, entryId);
        if (deleted > 0) {
            touchPlan(planId, OffsetDateTime.now(ZoneOffset.UTC));
        }
        return deleted > 0;
    }

    private void deleteBudget(UUID planId) {
        jdbcTemplate.update("delete from budget_classifications where plan_id = ?", planId);
        jdbcTemplate.update("delete from budget_envelopes where plan_id = ?", planId);
        jdbcTemplate.update("delete from budget_settings where plan_id = ?", planId);
    }

    private void ensureBudgetSettings(UUID planId, BudgetSettings.Method method, OffsetDateTime now) {
        int updated = jdbcTemplate.update("update budget_settings set method = ?, updated_at = ? where plan_id = ?", dbValue(method), now, planId);
        if (updated == 0) {
            jdbcTemplate.update("insert into budget_settings (plan_id, method, created_at, updated_at) values (?, ?, ?, ?)", planId, dbValue(method), now, now);
        }
    }

    private void insertBudget(UUID planId, BudgetSettings.Method method, List<BudgetEnvelope> envelopes, Map<UUID, BudgetSettings.BudgetClass> classifications, OffsetDateTime now) {
        ensureBudgetSettings(planId, method, now);
        insertBudgetEnvelopes(planId, envelopes, now);
        classifications.forEach((expenseId, budgetClass) -> jdbcTemplate.update(
                "insert into budget_classifications (plan_id, expense_id, budget_class, created_at, updated_at) values (?, ?, ?, ?, ?)",
                planId,
                expenseId,
                dbValue(budgetClass),
                now,
                now
        ));
    }

    private void insertBudgetEnvelopes(UUID planId, List<BudgetEnvelope> envelopes, OffsetDateTime now) {
        for (int index = 0; index < envelopes.size(); index++) {
            BudgetEnvelope envelope = envelopes.get(index);
            jdbcTemplate.update(
                    "insert into budget_envelopes (id, plan_id, name, limit_amount, icon, color, sort_order, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    envelope.id(),
                    planId,
                    envelope.name(),
                    envelope.limit(),
                    envelope.icon(),
                    envelope.color(),
                    index + 1,
                    now,
                    now
            );
        }
    }

    private List<BudgetEnvelope> findBudgetEnvelopes(UUID planId) {
        Map<String, BigDecimal> spentByName = expenseSpentByName(planId);
        return jdbcTemplate.query(
                "select * from budget_envelopes where plan_id = ? order by sort_order, name",
                (rs, rowNum) -> mapBudgetEnvelope(rs, spentByName),
                planId
        );
    }

    private List<BudgetEnvelope> findBudgetEnvelopes(UUID planId, List<ExpenseItem> expenses) {
        Map<String, BigDecimal> spentByName = new LinkedHashMap<>();
        for (ExpenseItem e : expenses) {
            spentByName.merge(e.name(), e.amount(), BigDecimal::add);
        }
        return jdbcTemplate.query(
                "select * from budget_envelopes where plan_id = ? order by sort_order, name",
                (rs, rowNum) -> mapBudgetEnvelope(rs, spentByName),
                planId
        );
    }

    private Map<String, BigDecimal> expenseSpentByName(UUID planId) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        jdbcTemplate.query(
                "select name, amount from expenses where plan_id = ?",
                (RowCallbackHandler) rs -> result.merge(rs.getString("name"), rs.getBigDecimal("amount"), BigDecimal::add),
                planId
        );
        return result;
    }

    @Override
    public List<Scenario> findScenarios(UUID planId) {
        return jdbcTemplate.query(
                "select * from scenarios where plan_id = ? order by created_at, name",
                this::mapScenario,
                planId
        );
    }

    @Override
    public Optional<Scenario> findScenario(UUID planId, UUID scenarioId) {
        return queryOptional(
                "select * from scenarios where plan_id = ? and id = ?",
                this::mapScenario,
                planId,
                scenarioId
        );
    }

    @Override
    @Transactional
    public Scenario createScenario(Scenario scenario) {
        OffsetDateTime now = offset(scenario.createdAt());
        jdbcTemplate.update(
                "insert into scenarios (id, plan_id, name, emoji, description, is_base, income_adj_pct, expense_adj_pct, return_adj_pct, inflation_adj_pct, retirement_age_shift, goals_cost_adj_pct, snapshot_json, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scenario.id(),
                scenario.basePlanId(),
                scenario.name(),
                scenario.emoji(),
                scenario.description(),
                scenario.base(),
                scenario.adjustments().incomeAdjPct(),
                scenario.adjustments().expenseAdjPct(),
                scenario.adjustments().returnAdjPct(),
                scenario.adjustments().inflationAdjPct(),
                scenario.adjustments().retirementAgeShift(),
                scenario.adjustments().goalsCostAdjPct(),
                null,
                now,
                now
        );
        touchPlan(scenario.basePlanId(), now);
        return findScenario(scenario.basePlanId(), scenario.id()).orElseThrow();
    }

    @Override
    @Transactional
    public Scenario updateScenario(Scenario scenario) {
        OffsetDateTime now = offset(scenario.updatedAt());
        jdbcTemplate.update(
                "update scenarios set name = ?, emoji = ?, description = ?, income_adj_pct = ?, expense_adj_pct = ?, return_adj_pct = ?, inflation_adj_pct = ?, retirement_age_shift = ?, goals_cost_adj_pct = ?, updated_at = ? where plan_id = ? and id = ?",
                scenario.name(),
                scenario.emoji(),
                scenario.description(),
                scenario.adjustments().incomeAdjPct(),
                scenario.adjustments().expenseAdjPct(),
                scenario.adjustments().returnAdjPct(),
                scenario.adjustments().inflationAdjPct(),
                scenario.adjustments().retirementAgeShift(),
                scenario.adjustments().goalsCostAdjPct(),
                now,
                scenario.basePlanId(),
                scenario.id()
        );
        touchPlan(scenario.basePlanId(), now);
        return findScenario(scenario.basePlanId(), scenario.id()).orElseThrow();
    }

    @Override
    @Transactional
    public boolean deleteScenario(UUID planId, UUID scenarioId) {
        int deleted = jdbcTemplate.update("delete from scenarios where plan_id = ? and id = ?", planId, scenarioId);
        if (deleted > 0) {
            touchPlan(planId, OffsetDateTime.now(ZoneOffset.UTC));
        }
        return deleted > 0;
    }

    @Override
    @Transactional
    public ModelAssumptions updateModelAssumptions(UUID planId, ModelAssumptions assumptions) {
        jdbcTemplate.update(
                "update model_assumptions set start_year = ?, projection_end_year = ?, horizon_years = ?, birth_year = ?, months_per_year = ?, currency = ?, initial_capital = ?, investment_return_pct = ?, source_model = ? where plan_id = ?",
                assumptions.startYear(),
                assumptions.projectionEndYear(),
                assumptions.horizonYears(),
                assumptions.birthYear(),
                assumptions.monthsPerYear(),
                assumptions.currency(),
                assumptions.initialCapital(),
                assumptions.investmentReturnPct(),
                assumptions.sourceModel(),
                planId
        );
        jdbcTemplate.update("delete from inflation_rates where plan_id = ?", planId);
        jdbcTemplate.batchUpdate(
                "insert into inflation_rates (plan_id, rate_year, rate_pct) values (?, ?, ?)",
                assumptions.inflationSchedule().stream()
                        .map(p -> new Object[]{planId, p.year(), p.ratePct()})
                        .toList()
        );
        touchPlan(planId, OffsetDateTime.now(ZoneOffset.UTC));
        return assumptions;
    }

    @Override
    @Transactional
    public PensionSettings updatePensionSettings(UUID planId, PensionSettings pension) {
        jdbcTemplate.update(
                "update pension_settings set current_age = ?, retirement_age = ?, monthly_expenses = ?, desired_monthly_expenses_current_prices = ?, currency = ?, expected_return_pct = ?, inflation_pct = ?, withdrawal_strategy = ?, state_pension_enabled = ?, state_pension_monthly = ? where plan_id = ?",
                pension.currentAge(),
                pension.retirementAge(),
                pension.monthlyExpenses(),
                pension.desiredMonthlyExpensesCurrentPrices(),
                pension.currency(),
                pension.expectedReturnPct(),
                pension.inflationPct(),
                dbValue(pension.withdrawalStrategy()),
                pension.statePensionEnabled(),
                pension.statePensionMonthly(),
                planId
        );
        touchPlan(planId, OffsetDateTime.now(ZoneOffset.UTC));
        return queryOptional("select * from pension_settings where plan_id = ?", this::mapPension, planId)
                .orElseThrow(() -> new IllegalArgumentException("Pension settings not found for plan " + planId));
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

    private record GoalSavingsTarget(UUID id, BigDecimal currentCost) {
    }

    private record PlanCore(FinancialPlan plan, UserProfile profile, PensionSettings pension, ModelAssumptions assumptions) {
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

    private Contribution mapContribution(ResultSet rs, int rowNum) throws SQLException {
        return new Contribution(
                rs.getObject("id", UUID.class),
                rs.getObject("goal_id", UUID.class),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                localDate(rs, "contribution_date"),
                rs.getString("note"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private BudgetEnvelope mapBudgetEnvelope(ResultSet rs, Map<String, BigDecimal> spentByName) throws SQLException {
        BigDecimal limit = rs.getBigDecimal("limit_amount");
        BigDecimal spent = spentByName.getOrDefault(rs.getString("name"), BigDecimal.ZERO);
        BigDecimal remaining = limit.subtract(spent).max(BigDecimal.ZERO);
        BigDecimal pct = BigDecimal.ZERO;
        if (limit.compareTo(BigDecimal.ZERO) > 0) {
            pct = spent.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP);
        }
        return new BudgetEnvelope(
                rs.getObject("id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getString("name"),
                limit,
                rs.getString("icon"),
                rs.getString("color"),
                spent,
                remaining,
                pct,
                spent.compareTo(limit) > 0
        );
    }

    private MonthlyTrackerEntry mapMonthlyTrackerEntry(ResultSet rs, int rowNum) throws SQLException {
        return new MonthlyTrackerEntry(
                YearMonth.parse(rs.getString("tracker_month")),
                enumValue(MonthlyTrackerEntry.Status.class, rs.getString("status")),
                rs.getBigDecimal("amount"),
                rs.getString("note"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private OperationJournalEntry mapOperationJournalEntry(ResultSet rs, int rowNum) throws SQLException {
        return new OperationJournalEntry(
                rs.getObject("id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                localDate(rs, "entry_date"),
                rs.getString("title"),
                rs.getBigDecimal("amount"),
                enumValue(OperationJournalEntry.Type.class, rs.getString("entry_type")),
                enumValue(OperationJournalEntry.Status.class, rs.getString("status")),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private Scenario mapScenario(ResultSet rs, int rowNum) throws SQLException {
        return new Scenario(
                rs.getObject("id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getString("name"),
                rs.getString("emoji"),
                rs.getString("description"),
                rs.getBoolean("is_base"),
                new Scenario.Adjustments(
                        rs.getBigDecimal("income_adj_pct"),
                        rs.getBigDecimal("expense_adj_pct"),
                        rs.getBigDecimal("return_adj_pct"),
                        rs.getBigDecimal("inflation_adj_pct"),
                        rs.getInt("retirement_age_shift"),
                        rs.getBigDecimal("goals_cost_adj_pct")
                ),
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

    private FinancialPlan mapPlanJoin(ResultSet rs) throws SQLException {
        return new FinancialPlan(
                rs.getObject("fp_id", UUID.class),
                rs.getObject("owner_user_id", UUID.class),
                rs.getString("fp_name"),
                rs.getString("base_currency"),
                instant(rs, "fp_created_at"),
                instant(rs, "fp_updated_at")
        );
    }

    private UserProfile mapProfileJoin(ResultSet rs) throws SQLException {
        return new UserProfile(
                rs.getObject("up_id", UUID.class),
                rs.getString("keycloak_subject"),
                rs.getString("email"),
                rs.getString("up_name"),
                rs.getString("phone"),
                rs.getString("avatar_url"),
                integer(rs, "age"),
                enumValue(UserProfile.Gender.class, rs.getString("gender")),
                rs.getBigDecimal("initial_balance"),
                instant(rs, "up_created_at"),
                instant(rs, "up_updated_at")
        );
    }

    private PensionSettings mapPensionJoin(ResultSet rs) throws SQLException {
        return new PensionSettings(
                rs.getObject("fp_id", UUID.class),
                rs.getInt("current_age"),
                rs.getInt("retirement_age"),
                rs.getBigDecimal("monthly_expenses"),
                rs.getBigDecimal("desired_monthly_expenses_current_prices"),
                rs.getString("ps_currency"),
                rs.getBigDecimal("expected_return_pct"),
                rs.getBigDecimal("inflation_pct"),
                enumValue(PensionSettings.WithdrawalStrategy.class, rs.getString("withdrawal_strategy")),
                rs.getBoolean("state_pension_enabled"),
                rs.getBigDecimal("state_pension_monthly")
        );
    }

    private ModelAssumptions mapAssumptionsJoin(ResultSet rs, List<YearRatePoint> inflationRates) throws SQLException {
        return new ModelAssumptions(
                rs.getInt("start_year"),
                integer(rs, "projection_end_year"),
                integer(rs, "horizon_years"),
                integer(rs, "birth_year"),
                rs.getInt("months_per_year"),
                rs.getString("ma_currency"),
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
                rs.getInt("target_month"),
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
