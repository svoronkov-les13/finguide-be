package les13.finguide.backend.plans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FinancialItemOpenApiTests {
    private static final Map<String, Set<String>> EXPECTED_METHODS = Map.of(
            "/api/v1/plans/{planId}/incomes", Set.of("get", "post"),
            "/api/v1/plans/{planId}/incomes/{id}", Set.of("get", "patch", "delete"),
            "/api/v1/plans/{planId}/expenses", Set.of("get", "post"),
            "/api/v1/plans/{planId}/expenses/{id}", Set.of("get", "patch", "delete"),
            "/api/v1/plans/{planId}/goals", Set.of("get", "post"),
            "/api/v1/plans/{planId}/goals/{id}", Set.of("get", "patch", "delete"),
            "/api/v1/plans/{planId}/goals/reorder", Set.of("post")
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void swaggerListsFinancialItemCrudEndpoints() throws Exception {
        JsonNode paths = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("paths");

        EXPECTED_METHODS.forEach((path, methods) -> {
            assertThat(paths.has(path)).as(path).isTrue();
            methods.forEach(method -> assertThat(paths.get(path).has(method)).as(path + " " + method).isTrue());
        });
    }

    @Test
    void swaggerDocumentsFinancialItemSchemasAndErrors() throws Exception {
        JsonNode api = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());

        assertThat(api.at("/paths/~1api~1v1~1plans~1{planId}~1goals~1{id}/patch/requestBody/content/application~1json/schema/$ref").asText())
                .contains("GoalRequest");
        assertThat(api.at("/paths/~1api~1v1~1plans~1{planId}~1goals~1{id}/patch/responses/400").isMissingNode())
                .isFalse();
        assertThat(api.at("/paths/~1api~1v1~1plans~1{planId}~1goals~1{id}/patch/responses/404").isMissingNode())
                .isFalse();
        assertThat(api.at("/components/schemas/GoalRequest/properties/targetYear/minimum").asInt())
                .isEqualTo(2024);
        assertThat(api.at("/components/schemas/GoalRequest/description").asText())
                .contains("omitted fields keep current values");
    }
}
