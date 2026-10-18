package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.IssueService;
import tsh.d94.app.server.apService.cira.dto.ChangeStatusRequest;
import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.IssueFilterRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.IssueStatusResponse;
import tsh.d94.app.server.apService.cira.dto.UpdateIssueRequest;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IssueController {

	private final IssueService issueService;

	@PostMapping("/projects/{projectId}/issues")
	public ApiResponse<IssueResponse> createIssue(
		@PathVariable String projectId,
		@RequestBody CreateIssueRequest request
	) {
		return ApiResponse.ok(issueService.createIssue(projectId, request));
	}

	@GetMapping("/projects/{projectId}/issues")
	public ApiResponse<Page<IssueResponse>> listIssues(
		@PathVariable String projectId,
		IssueFilterRequest filter,
		Pageable pageable
	) {
		return ApiResponse.ok(issueService.listIssues(projectId, filter, pageable));
	}

	@GetMapping("/issues/{issueId}")
	public ApiResponse<IssueResponse> getIssue(@PathVariable String issueId) {
		return ApiResponse.ok(issueService.getIssue(issueId));
	}

	@PutMapping("/issues/{issueId}")
	public ApiResponse<IssueResponse> updateIssue(
		@PathVariable String issueId,
		@RequestBody UpdateIssueRequest request
	) {
		return ApiResponse.ok(issueService.updateIssue(issueId, request));
	}

	@DeleteMapping("/issues/{issueId}")
	public ApiResponse<Void> deleteIssue(@PathVariable String issueId) {
		issueService.deleteIssue(issueId);
		return ApiResponse.noContent();
	}

	@PutMapping("/issues/{issueId}/status")
	public ApiResponse<IssueResponse> changeStatus(
		@PathVariable String issueId,
		@Valid @RequestBody ChangeStatusRequest request
	) {
		return ApiResponse.ok(issueService.changeStatus(issueId, request));
	}

	@GetMapping("/issues/{issueId}/available-transitions")
	public ApiResponse<List<IssueStatusResponse>> getAvailableTransitions(@PathVariable String issueId) {
		return ApiResponse.ok(issueService.getAvailableTransitions(issueId));
	}
}
