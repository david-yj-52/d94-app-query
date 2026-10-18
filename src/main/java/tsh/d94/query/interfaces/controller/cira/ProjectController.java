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

import tsh.d94.app.server.apService.cira.ProjectService;
import tsh.d94.app.server.apService.cira.dto.AddMemberRequest;
import tsh.d94.app.server.apService.cira.dto.AddStatusRequest;
import tsh.d94.app.server.apService.cira.dto.CreateProjectRequest;
import tsh.d94.app.server.apService.cira.dto.ProjectMemberResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.dto.UpdateMemberRoleRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateProjectRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateTransitionsRequest;
import tsh.d94.app.server.apService.cira.dto.WorkflowStatusResponse;
import tsh.d94.app.server.apService.cira.dto.WorkflowTransitionResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

	private final ProjectService projectService;

	// ── 프로젝트 기본 ────────────────────────────────────────────────────────

	@PostMapping
	public ApiResponse<ProjectResponse> createProject(@RequestBody CreateProjectRequest request) {
		return ApiResponse.ok(projectService.createProject(request));
	}

	@GetMapping
	public ApiResponse<List<ProjectResponse>> getMyProjects() {
		return ApiResponse.ok(projectService.getMyProjects());
	}

	@GetMapping("/{projectId}")
	public ApiResponse<ProjectResponse> getProject(@PathVariable String projectId) {
		return ApiResponse.ok(projectService.getProject(projectId));
	}

	@PutMapping("/{projectId}")
	public ApiResponse<ProjectResponse> updateProject(
		@PathVariable String projectId,
		@RequestBody UpdateProjectRequest request
	) {
		return ApiResponse.ok(projectService.updateProject(projectId, request));
	}

	// ── 멤버 관리 ────────────────────────────────────────────────────────────

	@GetMapping("/{projectId}/members")
	public ApiResponse<List<ProjectMemberResponse>> getMembers(@PathVariable String projectId) {
		return ApiResponse.ok(projectService.getMembers(projectId));
	}

	@PostMapping("/{projectId}/members")
	public ApiResponse<ProjectMemberResponse> addMember(
		@PathVariable String projectId,
		@RequestBody AddMemberRequest request
	) {
		return ApiResponse.ok(projectService.addMember(projectId, request));
	}

	@PutMapping("/{projectId}/members/{userId}/role")
	public ApiResponse<ProjectMemberResponse> updateMemberRole(
		@PathVariable String projectId,
		@PathVariable String userId,
		@RequestBody UpdateMemberRoleRequest request
	) {
		return ApiResponse.ok(projectService.updateMemberRole(projectId, userId, request));
	}

	@DeleteMapping("/{projectId}/members/{userId}")
	public ApiResponse<Void> removeMember(
		@PathVariable String projectId,
		@PathVariable String userId
	) {
		projectService.removeMember(projectId, userId);
		return ApiResponse.noContent();
	}

	// ── 워크플로우 관리 ──────────────────────────────────────────────────────

	@GetMapping("/{projectId}/workflow/statuses")
	public ApiResponse<List<WorkflowStatusResponse>> getWorkflowStatuses(@PathVariable String projectId) {
		return ApiResponse.ok(projectService.getWorkflowStatuses(projectId));
	}

	@PostMapping("/{projectId}/workflow/statuses")
	public ApiResponse<WorkflowStatusResponse> addWorkflowStatus(
		@PathVariable String projectId,
		@RequestBody AddStatusRequest request
	) {
		return ApiResponse.ok(projectService.addWorkflowStatus(projectId, request));
	}

	@DeleteMapping("/{projectId}/workflow/statuses/{statusId}")
	public ApiResponse<Void> deleteWorkflowStatus(
		@PathVariable String projectId,
		@PathVariable String statusId
	) {
		projectService.deleteWorkflowStatus(projectId, statusId);
		return ApiResponse.noContent();
	}

	@GetMapping("/{projectId}/workflow/transitions")
	public ApiResponse<List<WorkflowTransitionResponse>> getWorkflowTransitions(@PathVariable String projectId) {
		return ApiResponse.ok(projectService.getWorkflowTransitions(projectId));
	}

	@PutMapping("/{projectId}/workflow/transitions")
	public ApiResponse<List<WorkflowTransitionResponse>> updateWorkflowTransitions(
		@PathVariable String projectId,
		@RequestBody UpdateTransitionsRequest request
	) {
		return ApiResponse.ok(projectService.updateWorkflowTransitions(projectId, request));
	}
}
