package tsh.d94.app.server.apService.ayaan.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import tsh.d94.app.server.data.orm.ayaan.aynTask.SnAynTaskModel;
import tsh.d94.app.server.data.orm.ayaan.aynTaskRun.SnAynTaskRunModel;

/** ayaan 내부 API 입출력. 요청·응답에 같은 record 를 쓴다(PUT 은 null 필드를 건드리지 않는 부분 갱신). */
public final class AyaanDtos {

	private AyaanDtos() {
	}

	public record ToolDto(String toolCd, Boolean enabled, String authMode, String planName, String apiKeyRef, String model,
		Integer maxConcurrency, String fallbackResetRule, Long budgetTokens, String budgetPeriod) {
	}

	public record WorkspaceDto(String projectKey, String workspacePath, String defaultTool, String promptTemplate) {
	}

	public record QuotaDto(String toolCd, String windowCd, BigDecimal usedPct, Long usedTokens, Long limitTokens,
		LocalDateTime resetsAt, String status, String source, LocalDateTime observedAt) {
	}

	public record TokenTotals(long input, long output, long cacheRead, long cacheWrite, BigDecimal costUsd) {
	}

	public record RunDto(String id, String taskId, Integer runSeq, String toolCd, String cliSessionId, String outcome,
		Long inputTokens, Long outputTokens, Long cacheReadTokens, Long cacheWriteTokens, BigDecimal costUsd,
		Integer exitCode, String logPath, LocalDateTime startedAt, LocalDateTime endedAt) {

		public static RunDto from(SnAynTaskRunModel r) {
			return new RunDto(r.getObjId(), r.getTaskId(), r.getRunSeq(), r.getToolCd(), r.getCliSessionId(), r.getOutcome(),
				r.getInputTokens(), r.getOutputTokens(), r.getCacheReadTokens(), r.getCacheWriteTokens(), r.getCostUsd(),
				r.getExitCode(), r.getLogPath(), r.getStartedAt(), r.getEndedAt());
		}
	}

	/** runs·tokenTotals 는 상세 조회에서만 채워진다 */
	public record TaskDto(String id, String issueKey, String source, String projectKey, String workspacePath, String prompt,
		String toolCd, String exhaustPolicy, String fallbackTools, String status, String cliSessionId, LocalDateTime resumeAt,
		String pauseReason, Integer resumeCount, String leaseOwner, LocalDateTime leaseUntil, String resultSummary,
		String failReason, LocalDateTime startedAt, LocalDateTime finishedAt, LocalDateTime createdAt,
		List<RunDto> runs, TokenTotals tokenTotals) {

		public static TaskDto from(SnAynTaskModel t) {
			return from(t, null, null);
		}

		public static TaskDto from(SnAynTaskModel t, List<RunDto> runs, TokenTotals totals) {
			return new TaskDto(t.getObjId(), t.getIssueKey(), t.getSource(), t.getProjectKey(), t.getWorkspacePath(), t.getPrompt(),
				t.getToolCd(), t.getExhaustPolicy(), t.getFallbackTools(), t.getStatus(), t.getCliSessionId(), t.getResumeAt(),
				t.getPauseReason(), t.getResumeCount(), t.getLeaseOwner(), t.getLeaseUntil(), t.getResultSummary(),
				t.getFailReason(), t.getStartedAt(), t.getFinishedAt(), t.getCreatedAt(), runs, totals);
		}
	}

	/** fields 키: resumeAt pauseReason resumeCount cliSessionId toolCd resultSummary failReason leaseOwner leaseUntil startedAt finishedAt (키가 있으면 null 도 그대로 반영) */
	public record TransitionReq(String expectedStatus, String nextStatus, Map<String, Object> fields) {
	}

	public record ClaimReq(String toolCd, String leaseOwner, long leaseSeconds, long graceSeconds) {
	}

	public record LeaseReq(String leaseOwner, long leaseSeconds) {
	}

	public record IssueDto(String issueKey, String title, String content, String projectKey) {
	}

}
