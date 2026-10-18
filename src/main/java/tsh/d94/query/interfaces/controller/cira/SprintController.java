package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.SprintService;
import tsh.d94.app.server.apService.cira.dto.CompleteSprintRequest;
import tsh.d94.app.server.apService.cira.dto.CreateSprintRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.SprintResponse;
import tsh.d94.app.server.apService.cira.dto.UpdateSprintRequest;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SprintController {

	private final SprintService sprintService;

	@PostMapping("/api/v1/projects/{projectId}/sprints")
	public ApiResponse<SprintResponse> createSprint(
		@PathVariable String projectId,
		@RequestBody CreateSprintRequest request
	) {
		return ApiResponse.created(sprintService.createSprint(projectId, request));
	}

	@GetMapping("/api/v1/projects/{projectId}/sprints")
	public ApiResponse<List<SprintResponse>> getSprints(@PathVariable String projectId) {
		return ApiResponse.ok(sprintService.getSprints(projectId));
	}

	@GetMapping("/api/v1/projects/{projectId}/sprints/active")
	public ResponseEntity<ApiResponse<SprintResponse>> getActiveSprint(@PathVariable String projectId) {
		return sprintService.getActiveSprint(projectId)
			.map(s -> ResponseEntity.ok(ApiResponse.ok(s)))
			.orElseGet(() -> ResponseEntity.noContent().<ApiResponse<SprintResponse>>build());
	}

	@GetMapping("/api/v1/projects/{projectId}/backlog")
	public ApiResponse<Page<IssueResponse>> getBacklog(
		@PathVariable String projectId,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		return ApiResponse.ok(sprintService.getBacklog(projectId,
			PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "priority"))));
	}

	@GetMapping("/api/v1/sprints/{sprintId}")
	public ApiResponse<SprintResponse> getSprint(@PathVariable String sprintId) {
		return ApiResponse.ok(sprintService.getSprint(sprintId));
	}

	@PutMapping("/api/v1/sprints/{sprintId}")
	public ApiResponse<SprintResponse> updateSprint(
		@PathVariable String sprintId,
		@RequestBody UpdateSprintRequest request
	) {
		return ApiResponse.ok(sprintService.updateSprint(sprintId, request));
	}

	@DeleteMapping("/api/v1/sprints/{sprintId}")
	public ApiResponse<Void> deleteSprint(@PathVariable String sprintId) {
		sprintService.deleteSprint(sprintId);
		return ApiResponse.noContent();
	}

	@PostMapping("/api/v1/sprints/{sprintId}/start")
	public ApiResponse<SprintResponse> startSprint(@PathVariable String sprintId) {
		return ApiResponse.ok(sprintService.startSprint(sprintId));
	}

	@PostMapping("/api/v1/sprints/{sprintId}/complete")
	public ApiResponse<SprintResponse> completeSprint(
		@PathVariable String sprintId,
		@RequestBody CompleteSprintRequest request
	) {
		return ApiResponse.ok(sprintService.completeSprint(sprintId, request));
	}

	@PostMapping("/api/v1/sprints/{sprintId}/issues/{issueId}")
	public ApiResponse<Void> assignIssueToSprint(
		@PathVariable String sprintId,
		@PathVariable String issueId
	) {
		sprintService.assignIssueToSprint(sprintId, issueId);
		return ApiResponse.ok(null);
	}

	@DeleteMapping("/api/v1/sprints/{sprintId}/issues/{issueId}")
	public ApiResponse<Void> removeIssueFromSprint(
		@PathVariable String sprintId,
		@PathVariable String issueId
	) {
		sprintService.removeIssueFromSprint(sprintId, issueId);
		return ApiResponse.noContent();
	}
}
