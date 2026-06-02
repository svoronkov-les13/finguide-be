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
class PlanManagementOpenApiTests {
    private static final Map<String, Set<String>> EXPECTED_METHODS = Map.of(
            "/api/v1/plans", Set.of("get", "post"),
            "/api/v1/plans/current", Set.of("put"),
            "/api/v1/plans/{planId}/copy", Set.of("post")
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesPlanManagementEndpointsInOpenApi() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode api = objectMapper.readTree(body);

        EXPECTED_METHODS.forEach((path, methods) -> methods.forEach(method ->
                assertThat(api.at("/paths/" + path.replace("/", "~1") + "/" + method).isMissingNode())
                        .as("%s %s", method.toUpperCase(), path)
                        .isFalse()
        ));
        assertThat(api.at("/paths/~1api~1v1~1plans/post/responses/201").isMissingNode()).isFalse();
        assertThat(api.at("/paths/~1api~1v1~1plans~1{planId}~1copy/post/responses/201").isMissingNode()).isFalse();
    }
}
