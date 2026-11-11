package tsh.d94.app.server.apService.ayaan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.ayaan.dto.AyaanDtos.*;
import tsh.d94.app.server.data.orm.ayaan.AynRefBase;
import tsh.d94.app.server.data.orm.ayaan.aynConfig.SnAynConfigAccess;
import tsh.d94.app.server.data.orm.ayaan.aynConfig.SnAynConfigModel;
import tsh.d94.app.server.data.orm.ayaan.aynTask.SnAynTaskAccess;
import tsh.d94.app.server.data.orm.ayaan.aynTask.SnAynTaskModel;
import tsh.d94.app.server.data.orm.ayaan.aynTaskRun.SnAynTaskRunAccess;
import tsh.d94.app.server.data.orm.ayaan.aynTaskRun.SnAynTaskRunModel;
import tsh.d94.app.server.data.orm.ayaan.aynTool.SnAynToolAccess;
import tsh.d94.app.server.data.orm.ayaan.aynTool.SnAynToolModel;
import tsh.d94.app.server.data.orm.ayaan.aynToolQuota.SnAynToolQuotaAccess;
import tsh.d94.app.server.data.orm.ayaan.aynToolQuota.SnAynToolQuotaModel;
import tsh.d94.app.server.data.orm.ayaan.aynWorkspace.SnAynWorkspaceAccess;
import tsh.d94.app.server.data.orm.ayaan.aynWorkspace.SnAynWorkspaceModel;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/** ayaan 내부 API 로직. 상태 전이·선점·lease 는 DB 의 조건부 UPDATE 한 문장으로 경쟁을 막는다. */
@Service
@RequiredArgsConstructor
public class AyaanService {

	private static final String TENANT = "TSH";

	private final SnAynConfigAccess configAccess;
	private final SnAynToolAccess toolAccess;
	private final SnAynWorkspaceAccess workspaceAccess;
	private final SnAynToolQuotaAccess quotaAccess;
	private final SnAynTaskAccess taskAccess;
	private final SnAynTaskRunAccess runAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraProjectAccess projectAccess;
	private final ObjectMapper om;

	// ── config / tool / workspace / quota ──────────────────────────────────

	public Map<String, String> getConfig() {
		Map<String, String> m = new LinkedHashMap<>();
		configAccess.findAll().forEach(c -> m.put(c.getConfigKey(), c.getConfigValue()));
		return m;
	}

	@Transactional
	public Map<String, String> putConfig(Map<String, String> values) {
		values.forEach((k, v) -> {
			SnAynConfigModel c = configAccess.find(k).orElseGet(() -> stamp(SnAynConfigModel.builder().configKey(k).build()));
			c.setConfigValue(v);
			configAccess.save(c);
		});
		return getConfig();
	}

	public List<ToolDto> getTools() {
		return toolAccess.findAll().stream().map(AyaanService::toDto).toList();
	}

	@Transactional
	public ToolDto putTool(String toolCd, ToolDto d) {
		SnAynToolModel t = toolAccess.find(toolCd).orElseGet(() -> {
			SnAynToolModel n = stamp(SnAynToolModel.builder().toolCd(toolCd).build());
			n.setEnabled(false);
			n.setAuthMode("ACCOUNT");
			n.setMaxConcurrency(1);
			return n;
		});
		if (d.enabled() != null) t.setEnabled(d.enabled());
		if (d.authMode() != null) t.setAuthMode(d.authMode());
		if (d.planName() != null) t.setPlanName(d.planName());
		if (d.apiKeyRef() != null) t.setApiKeyRef(d.apiKeyRef());
		if (d.model() != null) t.setModel(d.model());
		if (d.maxConcurrency() != null) t.setMaxConcurrency(d.maxConcurrency());
		if (d.fallbackResetRule() != null) t.setFallbackResetRule(d.fallbackResetRule());
		if (d.budgetTokens() != null) t.setBudgetTokens(d.budgetTokens());
		if (d.budgetPeriod() != null) t.setBudgetPeriod(d.budgetPeriod());
		return toDto(toolAccess.save(t));
	}

	public List<WorkspaceDto> getWorkspaces() {
		return workspaceAccess.findAll().stream().map(AyaanService::toDto).toList();
	}

	@Transactional
	public WorkspaceDto putWorkspace(String projectKey, WorkspaceDto d) {
		SnAynWorkspaceModel w = workspaceAccess.find(projectKey).orElseGet(() -> {
			SnAynWorkspaceModel n = stamp(SnAynWorkspaceModel.builder().projectKey(projectKey).build());
			n.setDefaultTool("CLAUDE");
			return n;
		});
		if (d.workspacePath() != null) w.setWorkspacePath(d.workspacePath());
		if (d.defaultTool() != null) w.setDefaultTool(d.defaultTool());
		if (d.promptTemplate() != null) w.setPromptTemplate(d.promptTemplate());
		if (w.getWorkspacePath() == null) throw new IllegalArgumentException("workspacePath is required");
		return toDto(workspaceAccess.save(w));
	}

	@Transactional
	public void deleteWorkspace(String projectKey) {
		workspaceAccess.find(projectKey).orElseThrow(() -> new EntityNotFoundException("workspace not found: " + projectKey));
		workspaceAccess.delete(projectKey);
	}

	public List<QuotaDto> getQuotas() {
		return quotaAccess.findAll().stream().map(AyaanService::toDto).toList();
	}

	@Transactional
	public QuotaDto putQuota(String toolCd, String windowCd, QuotaDto d) {
		SnAynToolQuotaModel q = quotaAccess.find(toolCd, windowCd)
			.orElseGet(() -> stamp(SnAynToolQuotaModel.builder().toolCd(toolCd).windowCd(windowCd).build()));
		q.setUsedPct(d.usedPct());
		q.setUsedTokens(d.usedTokens());
		q.setLimitTokens(d.limitTokens());
		q.setResetsAt(d.resetsAt());
		if (d.status() == null || d.source() == null) throw new IllegalArgumentException("status and source are required");
		q.setStatus(d.status());
		q.setSource(d.source());
		q.setObservedAt(d.observedAt() != null ? d.observedAt() : LocalDateTime.now());
		return toDto(quotaAccess.save(q));
	}

	// ── task ───────────────────────────────────────────────────────────────

	/** 이슈당 활성 작업은 DB 부분 유니크 인덱스가 보장한다. 위반 시 기존 활성 Task 를 409 로 돌려준다. */
	public TaskDto createTask(TaskDto d) {
		SnAynTaskModel t = SnAynTaskModel.builder().build();
		stamp(t);
		t.setIssueKey(d.issueKey());
		t.setSource(d.source());
		t.setProjectKey(d.projectKey());
		t.setWorkspacePath(d.workspacePath());
		t.setPrompt(d.prompt());
		t.setToolCd(d.toolCd());
		t.setExhaustPolicy(d.exhaustPolicy());
		t.setFallbackTools(d.fallbackTools());
		t.setStatus(d.status() != null ? d.status() : "QUEUED");
		t.setCliSessionId(d.cliSessionId());
		t.setResumeAt(d.resumeAt());
		t.setPauseReason(d.pauseReason());
		t.setResumeCount(d.resumeCount() != null ? d.resumeCount() : 0);
		try {
			return TaskDto.from(taskAccess.insert(t));
		} catch (DataIntegrityViolationException e) {
			// 이 메서드는 트랜잭션 밖이라 저장 실패가 조회를 막지 않는다
			List<SnAynTaskModel> existing = d.issueKey() == null ? List.of() : taskAccess.activeByIssueKey(d.issueKey());
			if (existing.isEmpty()) throw e;
			throw new AyaanConflictException("active task already exists for " + d.issueKey(), TaskDto.from(existing.get(0)));
		}
	}

	public List<TaskDto> listTasks(String status, String tool) {
		return taskAccess.search(status, tool).stream().map(TaskDto::from).toList();
	}

	public TaskDto getTask(String id) {
		SnAynTaskModel t = findTask(id);
		List<RunDto> runs = runAccess.findByTaskId(id).stream().map(RunDto::from).toList();
		return TaskDto.from(t, runs, totals(runs));
	}

	@Transactional
	public TaskDto transition(String id, TransitionReq req) {
		if (taskAccess.transition(id, req.expectedStatus(), req.nextStatus()) == 0) {
			throw new AyaanConflictException("task " + id + " is not in status " + req.expectedStatus(), null);
		}
		SnAynTaskModel t = findTask(id);
		(req.fields() == null ? Map.<String, Object>of() : req.fields()).forEach((k, v) -> {
			switch (k) {
				case "resumeAt" -> t.setResumeAt(om.convertValue(v, LocalDateTime.class));
				case "pauseReason" -> t.setPauseReason((String)v);
				case "resumeCount" -> t.setResumeCount(om.convertValue(v, Integer.class));
				case "cliSessionId" -> t.setCliSessionId((String)v);
				case "toolCd" -> t.setToolCd((String)v);
				case "resultSummary" -> t.setResultSummary((String)v);
				case "failReason" -> t.setFailReason((String)v);
				case "leaseOwner" -> t.setLeaseOwner((String)v);
				case "leaseUntil" -> t.setLeaseUntil(om.convertValue(v, LocalDateTime.class));
				case "startedAt" -> t.setStartedAt(om.convertValue(v, LocalDateTime.class));
				case "finishedAt" -> t.setFinishedAt(om.convertValue(v, LocalDateTime.class));
				default -> throw new IllegalArgumentException("unknown field: " + k);
			}
		});
		return TaskDto.from(taskAccess.saveTask(t));
	}

	/** 선점 성공 시 RUNNING Task, 대상이 없으면 null */
	@Transactional
	public TaskDto claim(ClaimReq req) {
		List<String> ids = taskAccess.claim(req.leaseOwner(), req.leaseSeconds(), req.toolCd(), req.graceSeconds());
		return ids.isEmpty() ? null : TaskDto.from(findTask(ids.get(0)));
	}

	@Transactional
	public TaskDto extendLease(String id, LeaseReq req) {
		if (taskAccess.extendLease(id, req.leaseOwner(), req.leaseSeconds()) == 0) {
			throw new AyaanConflictException("lease not held by " + req.leaseOwner() + " on task " + id, null);
		}
		return TaskDto.from(findTask(id));
	}

	@Transactional
	public List<String> recover() {
		return taskAccess.recoverExpired();
	}

	// ── run ────────────────────────────────────────────────────────────────

	public RunDto createRun(String taskId, RunDto d) {
		findTask(taskId);
		SnAynTaskRunModel r = SnAynTaskRunModel.builder().build();
		stamp(r);
		r.setTaskId(taskId);
		r.setRunSeq(d.runSeq());
		r.setToolCd(d.toolCd());
		r.setStartedAt(LocalDateTime.now());
		return RunDto.from(runAccess.saveRun(r));
	}

	@Transactional
	public RunDto updateRun(String runId, RunDto d) {
		SnAynTaskRunModel r = runAccess.get(runId).orElseThrow(() -> new EntityNotFoundException("run not found: " + runId));
		if (d.outcome() != null) r.setOutcome(d.outcome());
		if (d.inputTokens() != null) r.setInputTokens(d.inputTokens());
		if (d.outputTokens() != null) r.setOutputTokens(d.outputTokens());
		if (d.cacheReadTokens() != null) r.setCacheReadTokens(d.cacheReadTokens());
		if (d.cacheWriteTokens() != null) r.setCacheWriteTokens(d.cacheWriteTokens());
		if (d.costUsd() != null) r.setCostUsd(d.costUsd());
		if (d.exitCode() != null) r.setExitCode(d.exitCode());
		if (d.logPath() != null) r.setLogPath(d.logPath());
		if (d.cliSessionId() != null) r.setCliSessionId(d.cliSessionId());
		if (d.endedAt() != null) r.setEndedAt(d.endedAt());
		return RunDto.from(runAccess.saveRun(r));
	}

	// ── CIRA ───────────────────────────────────────────────────────────────

	public IssueDto issueByKey(String issueKey) {
		var i = issueAccess.findByIssueKey(issueKey).orElseThrow(() -> new EntityNotFoundException("issue not found: " + issueKey));
		String projectKey = projectAccess.findProjectKeyById(i.getProjectId()).orElse(null);
		return new IssueDto(i.getIssueKey(), i.getTitle(), i.getContent(), projectKey);
	}

	// ── helpers ────────────────────────────────────────────────────────────

	private SnAynTaskModel findTask(String id) {
		return taskAccess.get(id).orElseThrow(() -> new EntityNotFoundException("task not found: " + id));
	}

	private static TokenTotals totals(List<RunDto> runs) {
		long in = 0, out = 0, cr = 0, cw = 0;
		BigDecimal cost = BigDecimal.ZERO;
		for (RunDto r : runs) {
			in += nz(r.inputTokens());
			out += nz(r.outputTokens());
			cr += nz(r.cacheReadTokens());
			cw += nz(r.cacheWriteTokens());
			if (r.costUsd() != null) cost = cost.add(r.costUsd());
		}
		return new TokenTotals(in, out, cr, cw, cost);
	}

	private static long nz(Long v) {
		return v == null ? 0 : v;
	}

	/** 내부 API 는 요청 컨텍스트(tenant·traceId)가 없어 신규 행의 공통 컬럼을 직접 채운다 */
	private static <T extends AynRefBase> T stamp(T m) {
		m.setSrvId(ApplicationProperties.getApplicationServiceName());
		m.setTenant(TENANT);
		m.setTraceId(UUID.randomUUID().toString());
		m.setEvntNm("AyaanApi");
		m.setPrevEvntNm("None");
		m.setUseStatCd("Usable");
		return m;
	}

	private static <T extends BaseModel> T stamp(T m) {
		m.setSrvId(ApplicationProperties.getApplicationServiceName());
		m.setTenant(TENANT);
		m.setTraceId(UUID.randomUUID().toString());
		m.setEvtNm("AyaanApi");
		m.setPrevEvntNm("None");
		m.setUseStatCd(UseStatCd.Usable);
		return m;
	}

	private static ToolDto toDto(SnAynToolModel t) {
		return new ToolDto(t.getToolCd(), t.getEnabled(), t.getAuthMode(), t.getPlanName(), t.getApiKeyRef(), t.getModel(),
			t.getMaxConcurrency(), t.getFallbackResetRule(), t.getBudgetTokens(), t.getBudgetPeriod());
	}

	private static WorkspaceDto toDto(SnAynWorkspaceModel w) {
		return new WorkspaceDto(w.getProjectKey(), w.getWorkspacePath(), w.getDefaultTool(), w.getPromptTemplate());
	}

	private static QuotaDto toDto(SnAynToolQuotaModel q) {
		return new QuotaDto(q.getToolCd(), q.getWindowCd(), q.getUsedPct(), q.getUsedTokens(), q.getLimitTokens(), q.getResetsAt(),
			q.getStatus(), q.getSource(), q.getObservedAt());
	}

}
