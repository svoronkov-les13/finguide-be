package les13.finguide.backend.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractCoverageTests {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");

    private static final Set<String> KNOWN_CONTRACT_OPERATIONS_NOT_IN_SPRINGDOC = Set.of(
            "DELETE /api/v1/me/avatar",
            "GET /api/v1/export/{jobId}",
            "GET /api/v1/notifications",
            "PATCH /api/v1/me",
            "POST /api/v1/export",
            "POST /api/v1/import",
            "POST /api/v1/notifications/read",
            "PUT /api/v1/me/avatar",
            "PUT /api/v1/plans/current"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void springdocCoverageMatchesKnownContractGap() throws Exception {
        Set<String> contractOperations = operationsFrom(objectMapper.readTree(Path.of("openapi/openapi.json").toFile()), true);
        Set<String> springdocOperations = operationsFrom(readSpringdoc(), false);

        Set<String> missing = new TreeSet<>(contractOperations);
        missing.removeAll(springdocOperations);

        assertThat(contractOperations).hasSize(58);
        assertThat(springdocOperations).containsAll(contractOperationsWithoutKnownGap(contractOperations));
        assertThat(missing).containsExactlyElementsOf(new TreeSet<>(KNOWN_CONTRACT_OPERATIONS_NOT_IN_SPRINGDOC));
    }

    @Test
    void contributionOperationsAreMarkedDeprecated() throws Exception {
        JsonNode contract = objectMapper.readTree(Path.of("openapi/openapi.json").toFile());
        JsonNode springdoc = readSpringdoc();

        assertContributionOperationsDeprecated(contract);
        assertContributionOperationsDeprecated(springdoc);
    }

    private JsonNode readSpringdoc() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static void assertContributionOperationsDeprecated(JsonNode openApi) {
        JsonNode collection = path(openApi, "/plans/{planId}/contributions");
        JsonNode item = path(openApi, "/plans/{planId}/contributions/{id}");

        assertThat(collection.path("get").path("deprecated").asBoolean()).isTrue();
        assertThat(collection.path("post").path("deprecated").asBoolean()).isTrue();
        assertThat(item.path("get").path("deprecated").asBoolean()).isTrue();
        assertThat(item.path("patch").path("deprecated").asBoolean()).isTrue();
        assertThat(item.path("delete").path("deprecated").asBoolean()).isTrue();
    }

    private static JsonNode path(JsonNode openApi, String path) {
        JsonNode paths = openApi.path("paths");
        JsonNode node = paths.path(path);
        return node.isMissingNode() ? paths.path("/api/v1" + path) : node;
    }

    private static Set<String> contractOperationsWithoutKnownGap(Set<String> contractOperations) {
        Set<String> implemented = new TreeSet<>(contractOperations);
        implemented.removeAll(KNOWN_CONTRACT_OPERATIONS_NOT_IN_SPRINGDOC);
        return implemented;
    }

    private static Set<String> operationsFrom(JsonNode openApi, boolean prefixApiBasePath) {
        Set<String> operations = new TreeSet<>();
        openApi.path("paths").fields().forEachRemaining(pathEntry -> {
            String path = normalizePath(pathEntry.getKey(), prefixApiBasePath);
            pathEntry.getValue().fieldNames().forEachRemaining(method -> {
                if (HTTP_METHODS.contains(method.toLowerCase(Locale.ROOT))) {
                    operations.add(method.toUpperCase(Locale.ROOT) + " " + path);
                }
            });
        });
        return operations;
    }

    private static String normalizePath(String path, boolean prefixApiBasePath) {
        if (!prefixApiBasePath || path.startsWith("/api/v1")) {
            return path;
        }
        return "/api/v1" + path;
    }
}
