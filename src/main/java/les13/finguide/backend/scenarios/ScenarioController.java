package les13.finguide.backend.scenarios;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import les13.finguide.backend.api.ApiEnvelope;
import les13.finguide.backend.api.PlanApiMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {
    private final ScenarioService service;
    private final PlanApiMapper mapper;

    public ScenarioController(ScenarioService service, PlanApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public ApiEnvelope<Object> scenarios() {
        return ApiEnvelope.of(service.scenarios().stream().map(mapper::scenario).toList());
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Scenario was created")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> createScenario(@RequestBody ScenarioRequests.ScenarioRequest request) {
        Scenario scenario = service.createScenario(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(scenario.id());
        return ResponseEntity.created(location).body(ApiEnvelope.of(mapper.scenario(scenario)));
    }

    @GetMapping("/{scenarioId}")
    public ApiEnvelope<Map<String, Object>> scenario(@PathVariable String scenarioId) {
        return ApiEnvelope.of(mapper.scenario(service.scenario(scenarioId)));
    }

    @PatchMapping("/{scenarioId}")
    public ApiEnvelope<Map<String, Object>> updateScenario(@PathVariable String scenarioId, @RequestBody ScenarioRequests.ScenarioRequest request) {
        return ApiEnvelope.of(mapper.scenario(service.updateScenario(scenarioId, request)));
    }

    @DeleteMapping("/{scenarioId}")
    @ApiResponse(responseCode = "204", description = "Scenario was deleted")
    public ResponseEntity<Void> deleteScenario(@PathVariable String scenarioId) {
        service.deleteScenario(scenarioId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/compare")
    public ApiEnvelope<Map<String, Object>> compare(@RequestBody ScenarioRequests.CompareRequest request) {
        return ApiEnvelope.of(mapper.scenarioComparison(service.compare(request)));
    }
}
