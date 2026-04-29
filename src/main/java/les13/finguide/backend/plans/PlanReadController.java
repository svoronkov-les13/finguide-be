package les13.finguide.backend.plans;

import les13.finguide.backend.api.ApiEnvelope;
import les13.finguide.backend.api.PlanApiMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PlanReadController {
    private final PlanReadService planReadService;
    private final PlanApiMapper mapper;

    public PlanReadController(PlanReadService planReadService, PlanApiMapper mapper) {
        this.planReadService = planReadService;
        this.mapper = mapper;
    }

    @GetMapping("/plans/current")
    public ApiEnvelope<Map<String, Object>> currentPlan() {
        return ApiEnvelope.of(mapper.planState(planReadService.currentPlan()));
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

    @GetMapping("/scenarios")
    public ApiEnvelope<Object> scenarios() {
        UUID currentPlanId = planReadService.currentPlan().plan().id();
        return ApiEnvelope.of(planReadService.scenarios(currentPlanId));
    }
}
