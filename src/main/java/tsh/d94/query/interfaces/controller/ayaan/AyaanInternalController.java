package tsh.d94.app.server.interfaces.controller.ayaan;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.ayaan.AyaanService;
import tsh.d94.app.server.apService.ayaan.dto.AyaanDtos.*;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/** d94-app-ayaan 전용 내부 API. 서비스 토큰(X-Service-Token)으로 보호된다 — AyaanInternalSecurityConfig. */
@RestController
@RequestMapping("/api/v1/internal/ayaan")
@RequiredArgsConstructor
public class AyaanInternalController {

	private final AyaanService svc;

	@GetMapping("/config")
	public ApiResponse<Map<String, String>> getConfig() {
		return ApiResponse.ok(svc.getConfig());
	}

	@PutMapping("/config")
	public ApiResponse<Map<String, String>> putConfig(@RequestBody Map<String, String> body) {
		return ApiResponse.ok(svc.putConfig(body));
	}

	@GetMapping("/tools")
	public ApiResponse<List<ToolDto>> tools() {
		return ApiResponse.ok(svc.getTools());
	}

	@PutMapping("/tools/{toolCd}")
	public ApiResponse<ToolDto> putTool(@PathVariable String toolCd, @RequestBody ToolDto body) {
		return ApiResponse.ok(svc.putTool(toolCd, body));
	}

	@GetMapping("/workspaces")
	public ApiResponse<List<WorkspaceDto>> workspaces() {
		return ApiResponse.ok(svc.getWorkspaces());
	}

	@PutMapping("/workspaces/{projectKey}")
	public ApiResponse<WorkspaceDto> putWorkspace(@PathVariable String projectKey, @RequestBody WorkspaceDto body) {
		return ApiResponse.ok(svc.putWorkspace(projectKey, body));
	}

	@DeleteMapping("/workspaces/{projectKey}")
	public ApiResponse<Void> deleteWorkspace(@PathVariable String projectKey) {
		svc.deleteWorkspace(projectKey);
		return ApiResponse.ok(null);
	}

	@GetMapping("/quotas")
	public ApiResponse<List<QuotaDto>> quotas() {
		return ApiResponse.ok(svc.getQuotas());
	}

	@PutMapping("/quotas/{toolCd}/{windowCd}")
	public ApiResponse<QuotaDto> putQuota(@PathVariable String toolCd, @PathVariable String windowCd, @RequestBody QuotaDto body) {
		return ApiResponse.ok(svc.putQuota(toolCd, windowCd, body));
	}

	@PostMapping("/tasks")
	public ResponseEntity<ApiResponse<TaskDto>> createTask(@RequestBody TaskDto body) {
		return ResponseEntity.status(201).body(ApiResponse.created(svc.createTask(body)));
	}

	@GetMapping("/tasks")
	public ApiResponse<List<TaskDto>> tasks(@RequestParam(required = false) String status, @RequestParam(required = false) String tool) {
		return ApiResponse.ok(svc.listTasks(status, tool));
	}

	@GetMapping("/tasks/{id}")
	public ApiResponse<TaskDto> task(@PathVariable String id) {
		return ApiResponse.ok(svc.getTask(id));
	}

	@PostMapping("/tasks/{id}/transition")
	public ApiResponse<TaskDto> transition(@PathVariable String id, @RequestBody TransitionReq body) {
		return ApiResponse.ok(svc.transition(id, body));
	}

	@PostMapping("/tasks/claim")
	public ResponseEntity<ApiResponse<TaskDto>> claim(@RequestBody ClaimReq body) {
		TaskDto t = svc.claim(body);
		return t == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(ApiResponse.ok(t));
	}

	@PostMapping("/tasks/{id}/lease")
	public ApiResponse<TaskDto> lease(@PathVariable String id, @RequestBody LeaseReq body) {
		return ApiResponse.ok(svc.extendLease(id, body));
	}

	@PostMapping("/tasks/recover")
	public ApiResponse<List<String>> recover() {
		return ApiResponse.ok(svc.recover());
	}

	@PostMapping("/tasks/{id}/runs")
	public ResponseEntity<ApiResponse<RunDto>> createRun(@PathVariable String id, @RequestBody RunDto body) {
		return ResponseEntity.status(201).body(ApiResponse.created(svc.createRun(id, body)));
	}

	@PutMapping("/runs/{runId}")
	public ApiResponse<RunDto> updateRun(@PathVariable String runId, @RequestBody RunDto body) {
		return ApiResponse.ok(svc.updateRun(runId, body));
	}

	@GetMapping("/issues/by-key/{issueKey}")
	public ApiResponse<IssueDto> issueByKey(@PathVariable String issueKey) {
		return ApiResponse.ok(svc.issueByKey(issueKey));
	}

}
