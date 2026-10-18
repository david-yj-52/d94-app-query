package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.MilestoneService;
import tsh.d94.app.server.apService.cira.dto.milestone.MilestoneProgressResponse;
import tsh.d94.app.server.apService.cira.dto.milestone.MilestoneRequest;
import tsh.d94.app.server.apService.cira.dto.milestone.MilestoneResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MilestoneController {

	private final MilestoneService milestoneService;

	@PostMapping("/api/v1/projects/{projectId}/milestones")
	public ApiResponse<MilestoneResponse> createMilestone(
		@PathVariable String projectId,
		@RequestBody MilestoneRequest request
	) {
		return ApiResponse.created(milestoneService.createMilestone(projectId, request));
	}

	@GetMapping("/api/v1/projects/{projectId}/milestones")
	public ApiResponse<List<MilestoneResponse>> getMilestones(@PathVariable String projectId) {
		return ApiResponse.ok(milestoneService.getMilestones(projectId));
	}

	@GetMapping("/api/v1/milestones/{milestoneId}")
	public ApiResponse<MilestoneResponse> getMilestone(@PathVariable String milestoneId) {
		return ApiResponse.ok(milestoneService.getMilestone(milestoneId));
	}

	@PutMapping("/api/v1/milestones/{milestoneId}")
	public ApiResponse<MilestoneResponse> updateMilestone(
		@PathVariable String milestoneId,
		@RequestBody MilestoneRequest request
	) {
		return ApiResponse.ok(milestoneService.updateMilestone(milestoneId, request));
	}

	@DeleteMapping("/api/v1/milestones/{milestoneId}")
	public ApiResponse<Void> deleteMilestone(@PathVariable String milestoneId) {
		milestoneService.deleteMilestone(milestoneId);
		return ApiResponse.ok(null);
	}

	@GetMapping("/api/v1/milestones/{milestoneId}/progress")
	public ApiResponse<MilestoneProgressResponse> getProgress(@PathVariable String milestoneId) {
		return ApiResponse.ok(milestoneService.getMilestoneProgress(milestoneId));
	}

	/** 마일스톤에 이슈 추가 */
	@PostMapping("/api/v1/milestones/{milestoneId}/issues/{issueId}")
	public ApiResponse<Void> addIssue(
		@PathVariable String milestoneId,
		@PathVariable String issueId
	) {
		milestoneService.addIssueToMilestone(milestoneId, issueId);
		return ApiResponse.ok(null);
	}

	/** 마일스톤에서 이슈 제거 */
	@DeleteMapping("/api/v1/milestones/{milestoneId}/issues/{issueId}")
	public ApiResponse<Void> removeIssue(
		@PathVariable String milestoneId,
		@PathVariable String issueId
	) {
		milestoneService.removeIssueFromMilestone(milestoneId, issueId);
		return ApiResponse.ok(null);
	}
}
