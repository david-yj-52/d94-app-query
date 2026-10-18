package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.VersionService;
import tsh.d94.app.server.apService.cira.dto.version.ReleaseNotesResponse;
import tsh.d94.app.server.apService.cira.dto.version.VersionRequest;
import tsh.d94.app.server.apService.cira.dto.version.VersionResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class VersionController {

	private final VersionService versionService;

	@PostMapping("/api/v1/projects/{projectId}/versions")
	public ApiResponse<VersionResponse> createVersion(
		@PathVariable String projectId,
		@RequestBody VersionRequest request
	) {
		return ApiResponse.created(versionService.createVersion(projectId, request));
	}

	@GetMapping("/api/v1/projects/{projectId}/versions")
	public ApiResponse<List<VersionResponse>> getVersions(@PathVariable String projectId) {
		return ApiResponse.ok(versionService.getVersions(projectId));
	}

	@GetMapping("/api/v1/versions/{versionId}")
	public ApiResponse<VersionResponse> getVersion(@PathVariable String versionId) {
		return ApiResponse.ok(versionService.getVersion(versionId));
	}

	@PutMapping("/api/v1/versions/{versionId}")
	public ApiResponse<VersionResponse> updateVersion(
		@PathVariable String versionId,
		@RequestBody VersionRequest request
	) {
		return ApiResponse.ok(versionService.updateVersion(versionId, request));
	}

	@DeleteMapping("/api/v1/versions/{versionId}")
	public ApiResponse<Void> deleteVersion(@PathVariable String versionId) {
		versionService.deleteVersion(versionId);
		return ApiResponse.ok(null);
	}

	@PostMapping("/api/v1/versions/{versionId}/release")
	public ApiResponse<VersionResponse> releaseVersion(@PathVariable String versionId) {
		return ApiResponse.ok(versionService.releaseVersion(versionId));
	}

	@PostMapping("/api/v1/versions/{versionId}/archive")
	public ApiResponse<VersionResponse> archiveVersion(@PathVariable String versionId) {
		return ApiResponse.ok(versionService.archiveVersion(versionId));
	}

	@GetMapping("/api/v1/versions/{versionId}/release-notes")
	public ApiResponse<ReleaseNotesResponse> getReleaseNotes(@PathVariable String versionId) {
		return ApiResponse.ok(versionService.getReleaseNotes(versionId));
	}

	/** 이슈 → 버전 연결 (type: FIX_VERSION | AFFECTS_VERSION) */
	@PostMapping("/api/v1/versions/{versionId}/issues/{issueId}")
	public ApiResponse<Void> linkIssue(
		@PathVariable String versionId,
		@PathVariable String issueId,
		@RequestParam(defaultValue = "FIX_VERSION") String type
	) {
		versionService.linkIssueToVersion(versionId, issueId, type);
		return ApiResponse.ok(null);
	}

	/** 이슈 → 버전 연결 해제 */
	@DeleteMapping("/api/v1/versions/{versionId}/issues/{issueId}")
	public ApiResponse<Void> unlinkIssue(
		@PathVariable String versionId,
		@PathVariable String issueId,
		@RequestParam(defaultValue = "FIX_VERSION") String type
	) {
		versionService.unlinkIssueFromVersion(versionId, issueId, type);
		return ApiResponse.ok(null);
	}
}
