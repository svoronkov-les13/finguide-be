package les13.finguide.backend.plans;

import les13.finguide.backend.api.ApiEnvelope;
import les13.finguide.backend.api.PlanApiMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans/{planId}")
public class FinancialItemController {
    private final FinancialItemService service;
    private final PlanApiMapper mapper;

    public FinancialItemController(FinancialItemService service, PlanApiMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/incomes")
    public ApiEnvelope<Object> incomes(@PathVariable UUID planId) {
        return ApiEnvelope.of(service.incomes(planId).stream().map(mapper::income).toList());
    }

    @PostMapping("/incomes")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> createIncome(
            @PathVariable UUID planId,
            @RequestBody FinancialItemRequests.IncomeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(mapper.income(service.createIncome(planId, request))));
    }

    @GetMapping("/incomes/{id}")
    public ApiEnvelope<Map<String, Object>> income(@PathVariable UUID planId, @PathVariable UUID id) {
        return ApiEnvelope.of(mapper.income(service.income(planId, id)));
    }

    @PatchMapping("/incomes/{id}")
    public ApiEnvelope<Map<String, Object>> updateIncome(
            @PathVariable UUID planId,
            @PathVariable UUID id,
            @RequestBody FinancialItemRequests.IncomeRequest request
    ) {
        return ApiEnvelope.of(mapper.income(service.updateIncome(planId, id, request)));
    }

    @DeleteMapping("/incomes/{id}")
    public ResponseEntity<Void> deleteIncome(@PathVariable UUID planId, @PathVariable UUID id) {
        service.deleteIncome(planId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/expenses")
    public ApiEnvelope<Object> expenses(@PathVariable UUID planId) {
        return ApiEnvelope.of(service.expenses(planId).stream().map(mapper::expense).toList());
    }

    @PostMapping("/expenses")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> createExpense(
            @PathVariable UUID planId,
            @RequestBody FinancialItemRequests.ExpenseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(mapper.expense(service.createExpense(planId, request))));
    }

    @GetMapping("/expenses/{id}")
    public ApiEnvelope<Map<String, Object>> expense(@PathVariable UUID planId, @PathVariable UUID id) {
        return ApiEnvelope.of(mapper.expense(service.expense(planId, id)));
    }

    @PatchMapping("/expenses/{id}")
    public ApiEnvelope<Map<String, Object>> updateExpense(
            @PathVariable UUID planId,
            @PathVariable UUID id,
            @RequestBody FinancialItemRequests.ExpenseRequest request
    ) {
        return ApiEnvelope.of(mapper.expense(service.updateExpense(planId, id, request)));
    }

    @DeleteMapping("/expenses/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable UUID planId, @PathVariable UUID id) {
        service.deleteExpense(planId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/goals")
    public ApiEnvelope<Object> goals(@PathVariable UUID planId) {
        return ApiEnvelope.of(service.goals(planId).stream().map(mapper::goal).toList());
    }

    @PostMapping("/goals")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> createGoal(
            @PathVariable UUID planId,
            @RequestBody FinancialItemRequests.GoalRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiEnvelope.of(mapper.goal(service.createGoal(planId, request))));
    }

    @GetMapping("/goals/{id}")
    public ApiEnvelope<Map<String, Object>> goal(@PathVariable UUID planId, @PathVariable UUID id) {
        return ApiEnvelope.of(mapper.goal(service.goal(planId, id)));
    }

    @PatchMapping("/goals/{id}")
    public ApiEnvelope<Map<String, Object>> updateGoal(
            @PathVariable UUID planId,
            @PathVariable UUID id,
            @RequestBody FinancialItemRequests.GoalRequest request
    ) {
        return ApiEnvelope.of(mapper.goal(service.updateGoal(planId, id, request)));
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<Void> deleteGoal(@PathVariable UUID planId, @PathVariable UUID id) {
        service.deleteGoal(planId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/goals/reorder")
    public ApiEnvelope<Object> reorderGoals(
            @PathVariable UUID planId,
            @RequestBody FinancialItemRequests.GoalReorderRequest request
    ) {
        return ApiEnvelope.of(service.reorderGoals(planId, request).stream().map(mapper::goal).toList());
    }
}
