package tsh.d94.app.server.apService.cira;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.GitActivityResponse;
import tsh.d94.app.server.apService.cira.dto.GitCommitResponse;
import tsh.d94.app.server.apService.cira.dto.GitPrResponse;
import tsh.d94.app.server.apService.cira.dto.GitRepositoryRequest;
import tsh.d94.app.server.apService.cira.dto.GitRepositoryResponse;
import tsh.d94.app.server.data.orm.cira.ciraGitCommit.SnCiraGitCommitAccess;
import tsh.d94.app.server.data.orm.cira.ciraGitPr.SnCiraGitPrAccess;
import tsh.d94.app.server.data.orm.cira.ciraGitRepo.SnCiraGitRepoAccess;
import tsh.d94.app.server.data.orm.cira.ciraGitRepo.SnCiraGitRepoModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Git 저장소 등록 / 조회 / 삭제 CRUD 서비스.
 * <p>
 * Access Token 은 AES-256/CBC 로 암호화하여 저장합니다.
 * 암호화 키는 {@code application.cira.git.encryption-key} (32바이트 Base64 인코딩) 에서 읽습니다.
 * 키가 없으면 기동 시 경고만 출력하고, 암호화를 건너뜁니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitRepositoryService {

	private static final String AES_ALGO = "AES/CBC/PKCS5Padding";
	private final SnCiraGitRepoAccess gitRepoAccess;
	private final SnCiraGitCommitAccess gitCommitAccess;
	private final SnCiraGitPrAccess gitPrAccess;
	// Base64-encoded 32-byte AES key (256-bit). Override via application.yml or env.
	@Value("${cira.git.encryption-key:}")
	private String encryptionKeyBase64;

	// -------------------------------------------------------------------------
	// CRUD
	// -------------------------------------------------------------------------

	/**
	 * Git 저장소를 프로젝트에 등록합니다.
	 *
	 * @param projectId 프로젝트 ID
	 * @param request   등록 요청 DTO
	 * @return 등록된 저장소 정보 (webhookSecret 일회 노출)
	 */
	@Transactional
	public GitRepositoryResponse registerRepository(String projectId, GitRepositoryRequest request) {
		// 중복 체크
		gitRepoAccess.findByRepoUrl(request.getRepoUrl()).ifPresent(existing -> {
			if (projectId.equals(existing.getProjectId())) {
				throw new IllegalArgumentException("이미 등록된 저장소 URL입니다: " + request.getRepoUrl());
			}
		});

		String webhookSecret = generateWebhookSecret();
		String encToken = encryptToken(request.getAccessToken());

		SnCiraGitRepoModel model = SnCiraGitRepoModel.builder()
			.projectId(projectId)
			.repoNm(request.getRepoNm())
			.repoUrl(request.getRepoUrl())
			.provider(request.getProvider() != null ? request.getProvider().toUpperCase() : "GITHUB")
			.defaultBranch(request.getDefaultBranch() != null ? request.getDefaultBranch() : "main")
			.webhookSecret(webhookSecret)
			.accessTokenEnc(encToken)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("REGISTER-GIT-REPO")
			.useStatCd(UseStatCd.Usable)
			.evtNm("RegisterGitRepo")
			.prevEvntNm("None")
			.build();

		gitRepoAccess.save(model);
		log.info("Git 저장소 등록: project={}, repo={}", projectId, request.getRepoUrl());

		return toResponse(model, webhookSecret);
	}

	/**
	 * 프로젝트에 등록된 저장소 목록을 조회합니다.
	 */
	public List<GitRepositoryResponse> listRepositories(String projectId) {
		return gitRepoAccess.findByProjectId(projectId).stream()
			.filter(r -> UseStatCd.Usable.equals(r.getUseStatCd()))
			.map(r -> toResponse(r, null))   // webhookSecret 미노출
			.collect(Collectors.toList());
	}

	/**
	 * 저장소를 논리 삭제합니다.
	 */
	@Transactional
	public void deleteRepository(String repoId) {
		SnCiraGitRepoModel model = gitRepoAccess.findById(repoId);
		if (!UseStatCd.Usable.equals(model.getUseStatCd())) {
			throw new EntityNotFoundException("저장소를 찾을 수 없습니다: " + repoId);
		}
		model.setUseStatCd(UseStatCd.Delete);
		gitRepoAccess.save(model);
		log.info("Git 저장소 삭제: repoId={}", repoId);
	}

	/**
	 * 이슈에 연결된 커밋 및 PR 목록을 조회합니다.
	 */
	public GitActivityResponse getGitActivity(String issueId) {
		List<GitCommitResponse> commits = gitCommitAccess.findByIssueId(issueId)
			.stream()
			.map(c -> GitCommitResponse.builder()
				.objId(c.getObjId())
				.repoId(c.getRepoId())
				.issueId(c.getIssueId())
				.commitHash(c.getCommitHash())
				.msg(c.getMsg())
				.authorNm(c.getAuthorNm())
				.authorEmail(c.getAuthorEmail())
				.commitDt(c.getCommitDt())
				.build())
			.collect(Collectors.toList());

		List<GitPrResponse> prs = gitPrAccess.findByIssueId(issueId)
			.stream()
			.map(pr -> GitPrResponse.builder()
				.objId(pr.getObjId())
				.repoId(pr.getRepoId())
				.issueId(pr.getIssueId())
				.prNo(pr.getPrNo())
				.title(pr.getTitle())
				.prStat(pr.getPrStat())
				.srcBranch(pr.getSrcBranch())
				.tgtBranch(pr.getTgtBranch())
				.authorNm(pr.getAuthorNm())
				.mergedAt(pr.getMergedAt())
				.closedAt(pr.getClosedAt())
				.build())
			.collect(Collectors.toList());

		return GitActivityResponse.builder()
			.issueId(issueId)
			.commits(commits)
			.pullRequests(prs)
			.build();
	}

	// -------------------------------------------------------------------------
	// 암호화
	// -------------------------------------------------------------------------

	/**
	 * UUID 기반 Webhook Secret을 생성합니다.
	 */
	public String generateWebhookSecret() {
		return UUID.randomUUID().toString().replace("-", "") +
			UUID.randomUUID().toString().replace("-", "");
	}

	/**
	 * AES-256/CBC 로 토큰을 암호화합니다.
	 * 결과 형식: Base64(IV) + ":" + Base64(CipherText)
	 */
	public String encryptToken(String plainText) {
		if (plainText == null || plainText.isBlank())
			return null;

		byte[] keyBytes = resolveKeyBytes();
		if (keyBytes == null) {
			log.warn("암호화 키 미설정 — 토큰을 평문으로 저장합니다. 운영 환경에서는 반드시 cira.git.encryption-key를 설정하세요.");
			return plainText;
		}

		try {
			SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
			Cipher cipher = Cipher.getInstance(AES_ALGO);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec);
			byte[] iv = cipher.getIV();
			byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(iv)
				+ ":"
				+ Base64.getEncoder().encodeToString(cipherText);
		} catch (Exception e) {
			log.error("토큰 암호화 실패: {}", e.getMessage(), e);
			throw new IllegalStateException("토큰 암호화에 실패했습니다.", e);
		}
	}

	/**
	 * AES-256/CBC 로 토큰을 복호화합니다.
	 */
	public String decryptToken(String encryptedText) {
		if (encryptedText == null || encryptedText.isBlank())
			return null;

		byte[] keyBytes = resolveKeyBytes();
		if (keyBytes == null)
			return encryptedText; // 키 없으면 평문 반환

		try {
			String[] parts = encryptedText.split(":");
			byte[] iv = Base64.getDecoder().decode(parts[0]);
			byte[] cipherText = Base64.getDecoder().decode(parts[1]);
			SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
			Cipher cipher = Cipher.getInstance(AES_ALGO);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
			return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
		} catch (Exception e) {
			log.error("토큰 복호화 실패: {}", e.getMessage(), e);
			throw new IllegalStateException("토큰 복호화에 실패했습니다.", e);
		}
	}

	// -------------------------------------------------------------------------
	// 내부 유틸리티
	// -------------------------------------------------------------------------

	private byte[] resolveKeyBytes() {
		if (encryptionKeyBase64 == null || encryptionKeyBase64.isBlank())
			return null;
		byte[] decoded = Base64.getDecoder().decode(encryptionKeyBase64);
		if (decoded.length != 32) {
			log.warn("AES 키가 32바이트가 아닙니다 ({}바이트). 암호화를 건너뜁니다.", decoded.length);
			return null;
		}
		return decoded;
	}

	private GitRepositoryResponse toResponse(SnCiraGitRepoModel model, String webhookSecret) {
		return GitRepositoryResponse.builder()
			.objId(model.getObjId())
			.projectId(model.getProjectId())
			.repoNm(model.getRepoNm())
			.repoUrl(model.getRepoUrl())
			.provider(model.getProvider())
			.defaultBranch(model.getDefaultBranch())
			.webhookSecret(webhookSecret)   // 등록 시만 노출, 조회 시 null
			.webhookUrl("/api/v1/webhooks/github")
			.build();
	}
}
