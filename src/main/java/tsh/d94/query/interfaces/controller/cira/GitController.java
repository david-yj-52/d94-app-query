package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.GitRepositoryService;
import tsh.d94.app.server.apService.cira.GitWebhookService;
import tsh.d94.app.server.apService.cira.dto.GitActivityResponse;
import tsh.d94.app.server.apService.cira.dto.GitRepositoryRequest;
import tsh.d94.app.server.apService.cira.dto.GitRepositoryResponse;
import tsh.d94.app.server.data.orm.cira.ciraGitRepo.SnCiraGitRepoAccess;
import tsh.d94.app.server.data.orm.cira.ciraGitRepo.SnCiraGitRepoModel;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GitHub Webhook 수신 및 Git 저장소 관리 REST Controller.
 *
 * <pre>
 * POST   /api/v1/webhooks/github                              — Webhook 수신 (URL 매핑 방식, 인증 제외)
 * POST   /api/v1/webhooks/github/{repoId}                    — Webhook 수신 (repoId 직접 지정)
 * POST   /api/v1/projects/{projectId}/git-repositories       — 저장소 등록
 * GET    /api/v1/projects/{projectId}/git-repositories       — 저장소 목록
 * DELETE /api/v1/git-repositories/{repoId}                   — 저장소 삭제
 * GET    /api/v1/issues/{issueId}/git-activity               — 이슈 커밋+PR 목록
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GitController {

	private final GitWebhookService gitWebhookService;
	private final GitRepositoryService gitRepositoryService;
	private final SnCiraGitRepoAccess gitRepoAccess;

	// -------------------------------------------------------------------------
	// Webhook 수신 — repoId를 URL에 포함하는 방식 (권장)
	// GitHub Webhook Payload URL 예: https://your-host/api/v1/webhooks/github/{repoId}
	// -------------------------------------------------------------------------

	@PostMapping(value = "/webhooks/github/{repoId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<Void> receiveWebhookByRepoId(
		@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
		@RequestHeader(value = "X-GitHub-Event", defaultValue = "push") String event,
		@PathVariable String repoId,
		@RequestBody Map<String, Object> payload
	) {
		handleWebhook(signature, event, repoId, payload);
		return ApiResponse.ok(null);
	}

	// -------------------------------------------------------------------------
	// Webhook 수신 — payload 내 repository URL 으로 자동 탐색 (범용)
	// GitHub Webhook Payload URL 예: https://your-host/api/v1/webhooks/github
	// -------------------------------------------------------------------------

	@PostMapping(value = "/webhooks/github", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ApiResponse<Void> receiveWebhook(
		@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
		@RequestHeader(value = "X-GitHub-Event", defaultValue = "push") String event,
		@RequestBody Map<String, Object> payload
	) {
		String repoId = resolveRepoIdFromPayload(payload);
		if (repoId == null) {
			log.warn("Webhook: payload에서 저장소 식별 불가 — 이벤트 무시");
			return ApiResponse.ok(null);
		}
		handleWebhook(signature, event, repoId, payload);
		return ApiResponse.ok(null);
	}

	// -------------------------------------------------------------------------
	// 저장소 CRUD
	// -------------------------------------------------------------------------

	/** 프로젝트에 Git 저장소를 등록합니다. */
	@PostMapping("/projects/{projectId}/git-repositories")
	public ApiResponse<GitRepositoryResponse> registerRepository(
		@PathVariable String projectId,
		@RequestBody GitRepositoryRequest request
	) {
		return ApiResponse.ok(gitRepositoryService.registerRepository(projectId, request));
	}

	/** 프로젝트의 Git 저장소 목록을 조회합니다. */
	@GetMapping("/projects/{projectId}/git-repositories")
	public ApiResponse<List<GitRepositoryResponse>> listRepositories(
		@PathVariable String projectId
	) {
		return ApiResponse.ok(gitRepositoryService.listRepositories(projectId));
	}

	/** Git 저장소를 삭제합니다. */
	@DeleteMapping("/git-repositories/{repoId}")
	public ApiResponse<Void> deleteRepository(@PathVariable String repoId) {
		gitRepositoryService.deleteRepository(repoId);
		return ApiResponse.noContent();
	}

	/** 이슈에 연결된 커밋 및 PR 목록을 조회합니다. */
	@GetMapping("/issues/{issueId}/git-activity")
	public ApiResponse<GitActivityResponse> getGitActivity(@PathVariable String issueId) {
		return ApiResponse.ok(gitRepositoryService.getGitActivity(issueId));
	}

	// -------------------------------------------------------------------------
	// 내부 유틸리티
	// -------------------------------------------------------------------------

	private void handleWebhook(String signature, String event, String repoId, Map<String, Object> payload) {
		// HMAC 서명 검증 (운영 환경: rawBody 캐싱 필터를 통해 정확한 검증 권장)
		// 현재는 webhookSecret 이 설정된 저장소에 한해 로그만 기록합니다.
		try {
			SnCiraGitRepoModel repo = gitRepoAccess.findById(repoId);
			String secret = repo.getWebhookSecret();
			if (secret != null && !secret.isBlank() && signature != null) {
				// rawBody 미확보 상태이므로 서명 검증 경고만 출력
				log.debug("Webhook 수신 (HMAC 전체검증은 RawBodyFilter 설치 후 활성화): repoId={}", repoId);
			}
		} catch (Exception e) {
			log.error("Webhook 저장소 조회 실패: repoId={}, error={}", repoId, e.getMessage());
			return;
		}

		// 이벤트 라우팅
		try {
			switch (event) {
				case "push" -> gitWebhookService.processGithubPushEvent(payload, repoId);
				case "pull_request" -> gitWebhookService.processGithubPrEvent(payload, repoId);
				default -> log.debug("미지원 GitHub 이벤트: {}", event);
			}
		} catch (Exception e) {
			log.error("Webhook 이벤트 처리 실패: event={}, repoId={}, error={}",
				event, repoId, e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private String resolveRepoIdFromPayload(Map<String, Object> payload) {
		try {
			Map<String, Object> repository = (Map<String, Object>)payload.get("repository");
			if (repository == null)
				return null;
			String htmlUrl = (String)repository.get("html_url");
			if (htmlUrl == null)
				return null;
			return gitRepoAccess.findByRepoUrl(htmlUrl)
				.map(r -> r.getObjId())
				.orElse(null);
		} catch (Exception e) {
			log.warn("payload에서 저장소 탐색 실패: {}", e.getMessage());
			return null;
		}
	}
}
