package les13.finguide.backend.plans;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import les13.finguide.backend.api.ApiEnvelope;
import les13.finguide.backend.api.PlanApiMapper;
import les13.finguide.backend.analytics.ModelAssumptions;
import les13.finguide.backend.pension.PensionSettings;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping("/api/v1")
public class PlanReadController {
    private final PlanReadService planReadService;
    private final PlanApiMapper mapper;

    public PlanReadController(PlanReadService planReadService, PlanApiMapper mapper) {
        this.planReadService = planReadService;
        this.mapper = mapper;
    }

    @GetMapping({"", "/"})
    public ApiEnvelope<Map<String, Object>> apiRoot() {
        return ApiEnvelope.of(Map.of(
                "name", "FinGuide API",
                "version", "v1",
                "status", "ok",
                "basePath", "/api/v1",
                "publicBasePath", "/finguide-api/api/v1",
                "swaggerUi", "/finguide-api/swagger-ui.html",
                "openApi", "/finguide-api/v3/api-docs",
                "endpoints", Map.of(
                        "currentPlan", "/api/v1/plans/current",
                        "dashboard", "/api/v1/plans/{planId}/dashboard",
                        "health", "/api/v1/plans/{planId}/analytics/health",
                        "cashflow", "/api/v1/plans/{planId}/analytics/cashflow",
                        "scenarios", "/api/v1/scenarios"
                )
        ));
    }

    @GetMapping("/plans/current")
    public ApiEnvelope<Map<String, Object>> currentPlan() {
        PlanState state = planReadService.currentPlan();
        return ApiEnvelope.of(mapper.planState(state, planReadService.goalAllocations(state)));
    }

    @GetMapping("/plans/{planId}/dashboard")
    public ApiEnvelope<Map<String, Object>> dashboard(@PathVariable UUID planId) {
        return ApiEnvelope.of(planReadService.dashboard(planId));
    }

    @GetMapping("/plans/{planId}/analytics/health")
    public ApiEnvelope<Map<String, Object>> health(@PathVariable UUID planId) {
        return ApiEnvelope.of(mapper.health(planReadService.health(planId)));
    }

    @GetMapping("/plans/{planId}/analytics/cashflow")
    public ApiEnvelope<Object> cashflow(@PathVariable UUID planId) {
        return ApiEnvelope.of(planReadService.cashflow(planId).stream().map(mapper::cashflow).toList());
    }

    @GetMapping("/plans/{planId}/analytics/cashflow/monthly")
    public ApiEnvelope<Object> monthlyCashflow(@PathVariable UUID planId) {
        return ApiEnvelope.of(planReadService.monthlyCashflow(planId));
    }

    @GetMapping("/plans/{planId}/analytics/assumptions")
    public ApiEnvelope<Map<String, Object>> assumptions(@PathVariable UUID planId) {
        return ApiEnvelope.of(mapper.assumptions(planReadService.assumptions(planId)));
    }

    @PatchMapping("/plans/{planId}/analytics/assumptions")
    public ApiEnvelope<Map<String, Object>> updateAssumptions(
            @PathVariable UUID planId,
            @RequestBody ModelAssumptions request
    ) {
        return ApiEnvelope.of(mapper.assumptions(planReadService.updateAssumptions(planId, request)));
    }

    @GetMapping("/plans/{planId}/analytics/balance/current")
    public ApiEnvelope<Map<String, Object>> currentBalance(@PathVariable UUID planId) {
        return ApiEnvelope.of(mapper.balance(planReadService.currentBalance(planId)));
    }

    @GetMapping("/plans/{planId}/analytics/projection")
    public ApiEnvelope<Object> projection(@PathVariable UUID planId, @RequestParam(defaultValue = "30") int years) {
        return ApiEnvelope.of(planReadService.projection(planId, years).stream().map(mapper::yearlyProjection).toList());
    }

    @GetMapping("/plans/{planId}/pension")
    public ApiEnvelope<Map<String, Object>> pension(@PathVariable UUID planId) {
        return ApiEnvelope.of(mapper.pension(planReadService.pension(planId)));
    }

    @PatchMapping("/plans/{planId}/pension")
    public ApiEnvelope<Map<String, Object>> updatePension(
            @PathVariable UUID planId,
            @RequestBody PensionSettings request
    ) {
        return ApiEnvelope.of(mapper.pension(planReadService.updatePension(planId, request)));
    }

    @GetMapping("/plans/{planId}/pension/projection")
    public ApiEnvelope<Map<String, Object>> pensionProjection(@PathVariable UUID planId) {
        return ApiEnvelope.of(mapper.pensionProjection(planReadService.pensionProjection(planId)));
    }
}
