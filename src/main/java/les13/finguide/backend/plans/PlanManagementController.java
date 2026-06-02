package les13.finguide.backend.plans;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import les13.finguide.backend.api.ApiEnvelope;
import les13.finguide.backend.api.PlanApiMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1/plans")
public class PlanManagementController {
    private final PlanManagementService service;
    private final PlanApiMapper mapper;

    public PlanManagementController(PlanManagementService service, PlanApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public ApiEnvelope<Object> plans() {
        return ApiEnvelope.of(service.plans().stream().map(mapper::planSummary).toList());
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Plan created")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> createPlan(@RequestBody PlanManagementService.PlanNameRequest request) {
        Map<String, Object> summary = mapper.planSummary(service.createBlank(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .build(summary.get("id"));
        return ResponseEntity.created(location).body(ApiEnvelope.of(summary));
    }

    @PostMapping("/{planId}/copy")
    @ApiResponse(responseCode = "201", description = "Plan copied")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> copyPlan(
            @PathVariable UUID planId,
            @RequestBody PlanManagementService.PlanNameRequest request
    ) {
        Map<String, Object> summary = mapper.planSummary(service.copy(planId, request));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/plans/{id}")
                .build(summary.get("id"));
        return ResponseEntity.created(location).body(ApiEnvelope.of(summary));
    }

    @PutMapping("/current")
    public ApiEnvelope<Map<String, Object>> switchCurrent(@RequestBody PlanManagementService.CurrentPlanRequest request) {
        return ApiEnvelope.of(mapper.planSummary(service.switchCurrent(request)));
    }
}
