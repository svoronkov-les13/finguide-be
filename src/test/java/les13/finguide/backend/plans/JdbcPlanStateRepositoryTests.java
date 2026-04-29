package les13.finguide.backend.plans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JdbcPlanStateRepositoryTests {
    @Autowired
    private PlanStateRepository repository;

    @Test
    void loadsCurrentPlanFromEmbeddedH2() {
        PlanState planState = repository.findCurrent().orElseThrow();

        assertThat(planState.plan().name()).isEqualTo("Основной план");
        assertThat(planState.profile().name()).isEqualTo("Александр Петров");
        assertThat(planState.pension().retirementAge()).isEqualTo(60);
        assertThat(planState.modelAssumptions().startYear()).isEqualTo(2024);
        assertThat(planState.modelAssumptions().inflationSchedule()).hasSize(4);
        assertThat(planState.incomes()).hasSize(3);
        assertThat(planState.expenses()).hasSize(3);
        assertThat(planState.goals()).hasSize(3);
    }
}
