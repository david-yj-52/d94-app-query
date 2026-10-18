package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.AutomationRuleService;
import tsh.d94.app.server.apService.cira.dto.automation.AutomationExecResponse;
import tsh.d94.app.server.apService.cira.dto.automation.AutomationRuleRequest;
import tsh.d94.app.server.apService.cira.dto.automation.AutomationRuleResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AutomationRuleController {

	private final AutomationRuleService automationRuleService;

	@GetMapping("/projects/{projectId}/automation-rules")
	public ApiResponse<List<AutomationRuleResponse>> listRules(
		@PathVariable String projectId
	) {
		return ApiResponse.ok(automationRuleService.listRules(projectId));
	}

	@PostMapping("/projects/{projectId}/automation-rules")
	public ApiResponse<AutomationRuleResponse> createRule(
		@PathVariable String projectId,
		@RequestBody AutomationRuleRequest request
	) {
		return ApiResponse.ok(automationRuleService.createRule(projectId, request));
	}

	@PutMapping("/automation-rules/{ruleId}")
	public ApiResponse<AutomationRuleResponse> updateRule(
		@PathVariable String ruleId,
		@RequestBody AutomationRuleRequest request
	) {
		return ApiResponse.ok(automationRuleService.updateRule(ruleId, request));
	}

	@DeleteMapping("/automation-rules/{ruleId}")
	public ApiResponse<Void> deleteRule(@PathVariable String ruleId) {
		automationRuleService.deleteRule(ruleId);
		return ApiResponse.noContent();
	}

	@GetMapping("/automation-rules/{ruleId}/executions")
	public ApiResponse<List<AutomationExecResponse>> listExecutions(
		@PathVariable String ruleId
	) {
		return ApiResponse.ok(automationRuleService.listExecutions(ruleId));
	}

}
