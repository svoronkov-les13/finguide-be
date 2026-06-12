package les13.finguide.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
class LiquibaseMigrationTests {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesInitialSchemaThroughLiquibase() {
        Integer appliedChanges = jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog where id = '001-initial-schema'",
                Integer.class
        );
        Integer userProfileTables = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name) = 'user_profiles'",
                Integer.class
        );

        assertThat(appliedChanges).isEqualTo(1);
        assertThat(userProfileTables).isEqualTo(1);
    }
}
