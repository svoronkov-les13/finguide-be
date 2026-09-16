package les13.finguide.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class FinGuideMockServerTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pensionProjectionMatchesRequiredCapitalContract() throws Exception {
        Field field = FinGuideMockServer.class.getDeclaredField("PENSION_PROJECTION");
        field.setAccessible(true);
        JsonNode projection = objectMapper.readTree((String) field.get(null));

        assertThat(projection.at("/preserveCapital/requiredCapitalAtRetirement").isNumber()).isTrue();
        assertThat(projection.at("/preserveCapital/requiredCapitalStatus").textValue()).isEqualTo("calculated");
        assertThat(projection.at("/spendDown/requiredCapitalAtRetirement").isNumber()).isTrue();
    }
}
