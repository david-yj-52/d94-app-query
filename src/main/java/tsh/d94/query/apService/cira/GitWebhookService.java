package tsh.d94.app.server.apService.cira;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.data.orm.cira.ciraComment.SnCiraCommentAccess;
import tsh.d94.app.server.data.orm.cira.ciraComment.SnCiraCommentModel;
import tsh.d94.app.server.data.orm.cira.ciraGitCommit.SnCiraGitCommitAccess;
import tsh.d94.app.server.data.orm.cira.ciraGitCommit.SnCiraGitCommitModel;
import tsh.d94.app.server.data.orm.cira.ciraGitPr.SnCiraGitPrAccess;
import tsh.d94.app.server.data.orm.cira.ciraGitPr.SnCiraGitPrModel;
import tsh.d94.app.server.data.orm.cira.ciraGitRepo.SnCiraGitRepoAccess;
import tsh.d94.app.server.data.orm.cira.ciraGitRepo.SnCiraGitRepoModel;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GitHub Webhook 이벤트(Push, PR) 처리 서비스.
 * <p>
 * 스마트 커밋 명령어:
 * <ul>
 *   <li>{@code #comment <text>}  — 이슈에 댓글 추가</li>
 *   <li>{@code #in-progress}     — 이슈 상태를 "진행 중" 카테고리로 전환</li>
 *   <li>{@code #resolve}         — 이슈 상태를 "완료" 카테고리로 전환</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitWebhookService {

	// 이슈 키 패턴: PROJ-123
	private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("[A-Z]+-\\d+");
	// 스마트 커밋 명령어 패턴
	private static final Pattern COMMAND_PATTERN = Pattern.compile(
		"#(comment|in-progress|resolve)(?:\\s+(.+?))?(?=#|$)", Pattern.CASE_INSENSITIVE);

	private final SnCiraGitRepoAccess gitRepoAccess;
	private final SnCiraGitCommitAccess gitCommitAccess;
	private final SnCiraGitPrAccess gitPrAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraIssueStatusAccess issueStatusAccess;
	private final SnCiraCommentAccess commentAccess;

	// -------------------------------------------------------------------------
	// HMAC-SHA256 서명 검증
	// -------------------------------------------------------------------------

	private static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
			sb.append(String.format("%02x", b));
		return sb.toString();
	}

	// -------------------------------------------------------------------------
	// Push 이벤트 처리
	// -------------------------------------------------------------------------

	/** 타이밍 어택 방지를 위한 상수 시간 문자열 비교 */
	private static boolean constantTimeEquals(String a, String b) {
		if (a.length() != b.length())
			return false;
		int result = 0;
		for (int i = 0; i < a.length(); i++) {
			result |= a.charAt(i) ^ b.charAt(i);
		}
		return result == 0;
	}

	// -------------------------------------------------------------------------
	// PR 이벤트 처리
	// -------------------------------------------------------------------------

	/**
	 * GitHub Webhook 서명(X-Hub-Signature-256)을 검증합니다.
	 *
	 * @param signature GitHub 가 보낸 "sha256=xxxx" 헤더값
	 * @param payload   raw request body
	 * @param secret    저장된 webhookSecret
	 * @return 서명 일치 여부
	 */
	public boolean verifyGithubSignature(String signature, String payload, String secret) {
		if (signature == null || !signature.startsWith("sha256=")) {
			return false;
		}
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			String computed = "sha256=" + bytesToHex(hash);
			return constantTimeEquals(computed, signature);
		} catch (Exception e) {
			log.error("HMAC 서명 검증 오류: {}", e.getMessage(), e);
			return false;
		}
	}

	// -------------------------------------------------------------------------
	// 스마트 커밋 명령어 처리
	// -------------------------------------------------------------------------

	/**
	 * GitHub Push 이벤트를 처리합니다.
	 *
	 * @param payload      GitHub 가 전달한 JSON payload (이미 파싱된 Map)
	 * @param repositoryId DB 상의 SN_CIRA_GIT_REPO.OBJ_ID
	 */
	@Transactional
	public void processGithubPushEvent(Map<String, Object> payload, String repositoryId) {
		SnCiraGitRepoModel repo = gitRepoAccess.findById(repositoryId);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> commits = (List<Map<String, Object>>)payload.getOrDefault("commits", List.of());

		for (Map<String, Object> commitData : commits) {
			String hash = (String)commitData.get("id");
			if (hash == null)
				continue;

			// 중복 저장 방지
			if (gitCommitAccess.findByRepoIdAndCommitHash(repositoryId, hash).isPresent()) {
				log.debug("커밋 중복 스킵: repo={}, hash={}", repositoryId, hash);
				continue;
			}

			String message = (String)commitData.getOrDefault("message", "");
			String authorName = extractAuthorField(commitData, "name");
			String authorEmail = extractAuthorField(commitData, "email");
			LocalDateTime commitDt = parseGitTimestamp((String)commitData.get("timestamp"));

			// 이슈 키 추출 및 첫 번째 연결
			List<String> issueKeys = extractIssueKeys(message);
			String issueId = resolveFirstIssueId(issueKeys);

			SnCiraGitCommitModel commit = SnCiraGitCommitModel.builder()
				.repoId(repositoryId)
				.issueId(issueId)
				.commitHash(hash)
				.msg(message)
				.authorNm(authorName)
				.authorEmail(authorEmail)
				.commitDt(commitDt)
				.srvId(ApplicationProperties.getApplicationServiceName())
				.tenant(ApplicationProperties.getApplicationTenant())
				.traceId("GIT-PUSH")
				.useStatCd(UseStatCd.Usable)
				.evtNm("GitPushCommit")
				.prevEvntNm("None")
				.build();

			gitCommitAccess.save(commit);
			log.info("커밋 저장: repo={}, hash={}, issue={}", repositoryId, hash, issueId);

			// 스마트 커밋 처리
			for (String issueKey : issueKeys) {
				issueAccess.findByIssueKey(issueKey).ifPresent(issue ->
					processSmartCommitCommands(message, issue)
				);
			}
		}
	}

	/**
	 * GitHub Pull Request 이벤트를 처리합니다.
	 *
	 * @param payload      GitHub 가 전달한 JSON payload
	 * @param repositoryId DB 상의 SN_CIRA_GIT_REPO.OBJ_ID
	 */
	@Transactional
	public void processGithubPrEvent(Map<String, Object> payload, String repositoryId) {
		@SuppressWarnings("unchecked")
		Map<String, Object> prData = (Map<String, Object>)payload.get("pull_request");
		if (prData == null) {
			log.warn("PR 데이터 없음, repositoryId={}", repositoryId);
			return;
		}

		Integer prNo = toInteger(prData.get("number"));
		String title = (String)prData.getOrDefault("title", "");
		String body = (String)prData.getOrDefault("body", "");
		String state = (String)prData.getOrDefault("state", "open");
		String action = (String)payload.getOrDefault("action", "");

		@SuppressWarnings("unchecked")
		Map<String, Object> head = (Map<String, Object>)prData.getOrDefault("head", Map.of());
		@SuppressWarnings("unchecked")
		Map<String, Object> base = (Map<String, Object>)prData.getOrDefault("base", Map.of());
		String srcBranch = (String)head.getOrDefault("ref", "");
		String tgtBranch = (String)base.getOrDefault("ref", "");

		@SuppressWarnings("unchecked")
		Map<String, Object> authorData = (Map<String, Object>)prData.getOrDefault("user", Map.of());
		String authorLogin = (String)authorData.getOrDefault("login", "");

		LocalDateTime mergedAt = null;
		if ("closed".equals(state) && Boolean.TRUE.equals(prData.get("merged"))) {
			mergedAt = parseGitTimestamp((String)prData.get("merged_at"));
			state = "merged";
		}
		LocalDateTime closedAt = "closed".equals(state) ? parseGitTimestamp((String)prData.get("closed_at")) : null;

		// 이슈 키 연결 (제목 + 본문에서 추출)
		List<String> issueKeys = extractIssueKeys(title + " " + (body != null ? body : ""));
		String issueId = resolveFirstIssueId(issueKeys);

		// PR upsert
		Optional<SnCiraGitPrModel> existing = gitPrAccess.findByRepoIdAndPrNo(repositoryId, prNo);
		if (existing.isPresent()) {
			SnCiraGitPrModel pr = existing.get();
			pr.setPrStat(state);
			pr.setTitle(title);
			pr.setDescr(body);
			pr.setMergedAt(mergedAt);
			pr.setClosedAt(closedAt);
			if (issueId != null)
				pr.setIssueId(issueId);
			gitPrAccess.save(pr);
			log.info("PR 업데이트: repo={}, pr#{}, state={}", repositoryId, prNo, state);
		} else {
			SnCiraGitPrModel pr = SnCiraGitPrModel.builder()
				.repoId(repositoryId)
				.issueId(issueId)
				.prNo(prNo)
				.title(title)
				.descr(body)
				.prStat(state)
				.srcBranch(srcBranch)
				.tgtBranch(tgtBranch)
				.authorNm(authorLogin)
				.mergedAt(mergedAt)
				.closedAt(closedAt)
				.srvId(ApplicationProperties.getApplicationServiceName())
				.tenant(ApplicationProperties.getApplicationTenant())
				.traceId("GIT-PR")
				.useStatCd(UseStatCd.Usable)
				.evtNm("GitPrEvent")
				.prevEvntNm("None")
				.build();
			gitPrAccess.save(pr);
			log.info("PR 저장: repo={}, pr#{}, state={}", repositoryId, prNo, state);
		}

		// merged PR → 연결 이슈 상태를 resolve 처리
		if ("merged".equals(state)) {
			for (String issueKey : issueKeys) {
				issueAccess.findByIssueKey(issueKey).ifPresent(issue ->
					resolveIssue(issue, "GitPrMerged-" + prNo)
				);
			}
		}
	}

	private void processSmartCommitCommands(String message, SnCiraIssueModel issue) {
		Matcher m = COMMAND_PATTERN.matcher(message);
		while (m.find()) {
			String cmd = m.group(1).toLowerCase();
			String args = m.group(2) != null ? m.group(2).trim() : "";

			switch (cmd) {
				case "comment" -> {
					if (!args.isEmpty()) {
						addSmartComment(issue, args);
					}
				}
				case "in-progress" -> changeIssueStatusByCategory(issue, "IN_PROGRESS");
				case "resolve" -> resolveIssue(issue, "SmartCommit-resolve");
				default -> log.debug("알 수 없는 스마트 커밋 명령어: {}", cmd);
			}
		}
	}

	private void addSmartComment(SnCiraIssueModel issue, String text) {
		SnCiraCommentModel comment = SnCiraCommentModel.builder()
			.issueId(issue.getObjId())
			.authorId("GIT_BOT")
			.content("[스마트커밋] " + text)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("SMART-COMMENT")
			.useStatCd(UseStatCd.Usable)
			.evtNm("SmartCommitComment")
			.prevEvntNm("None")
			.build();
		commentAccess.save(comment);
		log.info("스마트커밋 댓글 추가: issue={}, text={}", issue.getIssueKey(), text);
	}

	// -------------------------------------------------------------------------
	// 내부 유틸리티
	// -------------------------------------------------------------------------

	private void resolveIssue(SnCiraIssueModel issue, String traceId) {
		changeIssueStatusByCategory(issue, "DONE");
	}

	/**
	 * 이슈의 프로젝트에서 특정 카테고리에 해당하는 첫 번째 상태로 이슈 상태를 변경합니다.
	 */
	private void changeIssueStatusByCategory(SnCiraIssueModel issue, String category) {
		List<SnCiraIssueStatusModel> statuses = issueStatusAccess.findByProjectId(issue.getProjectId());
		Optional<SnCiraIssueStatusModel> target = statuses.stream()
			.filter(s -> category.equalsIgnoreCase(s.getCategory()))
			.findFirst();

		if (target.isEmpty()) {
			log.warn("카테고리 '{}' 에 해당하는 상태 없음: project={}", category, issue.getProjectId());
			return;
		}

		if (target.get().getObjId().equals(issue.getStatusId())) {
			log.debug("이미 해당 상태: issue={}, category={}", issue.getIssueKey(), category);
			return;
		}

		issue.setStatusId(target.get().getObjId());
		if ("DONE".equalsIgnoreCase(category)) {
			issue.setResolvedAt(LocalDateTime.now());
		}
		issueAccess.save(issue);
		log.info("이슈 상태 변경: issue={}, category={}, statusId={}",
			issue.getIssueKey(), category, target.get().getObjId());
	}

	private List<String> extractIssueKeys(String text) {
		List<String> keys = new ArrayList<>();
		if (text == null)
			return keys;
		Matcher m = ISSUE_KEY_PATTERN.matcher(text);
		while (m.find())
			keys.add(m.group());
		return keys;
	}

	private String resolveFirstIssueId(List<String> issueKeys) {
		for (String key : issueKeys) {
			Optional<SnCiraIssueModel> issue = issueAccess.findByIssueKey(key);
			if (issue.isPresent())
				return issue.get().getObjId();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private String extractAuthorField(Map<String, Object> commitData, String field) {
		Object author = commitData.get("author");
		if (author instanceof Map) {
			return (String)((Map<String, Object>)author).getOrDefault(field, "");
		}
		return "";
	}

	private LocalDateTime parseGitTimestamp(String ts) {
		if (ts == null || ts.isBlank())
			return LocalDateTime.now();
		try {
			return OffsetDateTime.parse(ts).toLocalDateTime();
		} catch (Exception e) {
			log.warn("타임스탬프 파싱 실패: {}", ts);
			return LocalDateTime.now();
		}
	}

	private Integer toInteger(Object val) {
		if (val instanceof Integer i)
			return i;
		if (val instanceof Number n)
			return n.intValue();
		return 0;
	}
}
