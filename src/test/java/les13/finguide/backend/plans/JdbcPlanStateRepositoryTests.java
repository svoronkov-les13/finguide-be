package les13.finguide.backend.plans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class JdbcPlanStateRepositoryTests {
    private static final UUID SEED_PLAN_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Autowired
    private PlanStateRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsCurrentPlanFromEmbeddedH2() {
        PlanState planState = repository.findCurrent().orElseThrow();

        assertThat(planState.plan().id()).isEqualTo(SEED_PLAN_ID);
        assertThat(planState.plan().name()).isEqualTo("Основной план");
        assertThat(planState.profile().name()).isEqualTo("Александр Петров");
        assertThat(planState.pension().retirementAge()).isEqualTo(60);
        assertThat(planState.modelAssumptions().startYear()).isEqualTo(2024);
        assertThat(planState.modelAssumptions().inflationSchedule()).hasSize(4);
        assertThat(planState.incomes()).hasSize(3);
        assertThat(planState.expenses()).hasSize(3);
        assertThat(planState.goals()).hasSize(3);
    }

    @Test
    void clonesSeedPlanForUserOwnerAndKeepsItIdempotent() {
        UUID ownerId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        insertProfile(ownerId, "clone-user");

        PlanState first = repository.findOrCreateCurrentForOwner(ownerId);
        PlanState second = repository.findOrCreateCurrentForOwner(ownerId);

        assertThat(first.plan().id()).isNotEqualTo(SEED_PLAN_ID);
        assertThat(first.plan().ownerUserId()).isEqualTo(ownerId);
        assertThat(second.plan().id()).isEqualTo(first.plan().id());
        assertThat(first.incomes()).hasSize(3);
        assertThat(first.expenses()).hasSize(3);
        assertThat(first.goals()).hasSize(3);
        assertThat(first.modelAssumptions().inflationSchedule()).hasSize(4);
        assertThat(ids("incomes", first.plan().id())).doesNotContainAnyElementsOf(ids("incomes", SEED_PLAN_ID));
        assertThat(planCount(ownerId)).isEqualTo(1);
    }

    private void insertProfile(UUID ownerId, String subject) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "insert into user_profiles (id, keycloak_subject, email, name, phone, avatar_url, age, gender, initial_balance, created_at, updated_at) values (?, ?, ?, ?, null, null, null, null, ?, ?, ?)",
                ownerId,
                subject,
                subject + "@example.com",
                "Clone User",
                BigDecimal.ZERO,
                now,
                now
        );
    }

    private java.util.List<UUID> ids(String table, UUID planId) {
        return jdbcTemplate.query("select id from " + table + " where plan_id = ?", (rs, rowNum) -> rs.getObject("id", UUID.class), planId);
    }

    private int planCount(UUID ownerId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from financial_plans where owner_user_id = ?", Integer.class, ownerId);
        return count == null ? 0 : count;
    }
}
