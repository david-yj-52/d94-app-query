package tsh.d94.app.server.apService.ayaan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.ayaan.dto.AyaanDtos.*;
import tsh.d94.app.server.data.orm.ayaan.AynRefBase;
import tsh.d94.app.server.data.orm.ayaan.aynConfig.SnAynConfigAccess;
import tsh.d94.app.server.data.orm.ayaan.aynConfig.SnAynConfigModel;
import tsh.d94.app.server.data.orm.ayaan.aynPermission.SnAynPermissionAccess;
import tsh.d94.app.server.data.orm.ayaan.aynPermission.SnAynPermissionModel;
import tsh.d94.app.server.data.orm.ayaan.aynRequest.SnAynRequestAccess;
import tsh.d94.app.server.data.orm.ayaan.aynRequest.SnAynRequestModel;
import tsh.d94.app.server.data.orm.ayaan.aynSkill.SnAynSkillAccess;
import tsh.d94.app.server.data.orm.ayaan.aynSkill.SnAynSkillModel;
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
	private final SnAynSkillAccess skillAccess;
	private final SnAynPermissionAccess permissionAccess;
	private final SnAynRequestAccess requestAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraProjectAccess projectAccess;
	private final ObjectMapper om;
	private final PlatformTransactionManager txManager;

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
		// PUT replaces every nullable field (null clears it); NOT NULL columns keep their value on null
		if (d.enabled() != null) t.setEnabled(d.enabled());
		if (d.authMode() != null) t.setAuthMode(d.authMode());
		t.setPlanName(d.planName());
		t.setApiKeyRef(d.apiKeyRef());
		t.setModel(d.model());
		if (d.maxConcurrency() != null) t.setMaxConcurrency(d.maxConcurrency());
		t.setFallbackResetRule(d.fallbackResetRule());
		t.setBudgetTokens(d.budgetTokens());
		t.setBudgetPeriod(d.budgetPeriod());
		t.setPlanSkillCd(d.planSkillCd());
		t.setDevSkillCd(d.devSkillCd());
		return toDto(toolAccess.save(t));
	}

	// ── skill / permission ─────────────────────────────────────────────────

	public List<SkillDto> getSkills() {
		return skillAccess.findAll().stream().map(AyaanService::toDto).toList();
	}

	/** PUT 은 전체 교체(nullable 은 null 이면 지움). 값 검증은 ayaan 이 한다 */
	@Transactional
	public SkillDto putSkill(String skillCd, SkillDto d) {
		if (d.skillNm() == null || d.toolCd() == null || d.phase() == null || d.invokeText() == null) {
			throw new IllegalArgumentException("skillNm, toolCd, phase, invokeText are required");
		}
		SnAynSkillModel s = skillAccess.find(skillCd).orElseGet(() -> stamp(SnAynSkillModel.builder().skillCd(skillCd).build()));
		s.setSkillNm(d.skillNm());
		s.setToolCd(d.toolCd());
		s.setPhase(d.phase());
		s.setInvokeText(d.invokeText());
		s.setPluginRef(d.pluginRef());
		s.setDescription(d.description());
		s.setEnabled(d.enabled() == null || d.enabled());
		s.setSortNo(d.sortNo() == null ? 0 : d.sortNo());
		return toDto(skillAccess.save(s));
	}

	/** Tool 기본 skill 이 이 skill 을 가리키면 함께 비운다 */
	@Transactional
	public void deleteSkill(String skillCd) {
		skillAccess.find(skillCd).orElseThrow(() -> new EntityNotFoundException("skill not found: " + skillCd));
		toolAccess.findAll().forEach(t -> {
			if (skillCd.equals(t.getPlanSkillCd())) t.setPlanSkillCd(null);
			if (skillCd.equals(t.getDevSkillCd())) t.setDevSkillCd(null);
		});
		skillAccess.delete(skillCd);
	}

	public List<PermissionDto> getPermissions() {
		return permissionAccess.listAll().stream().map(AyaanService::toDto).toList();
	}

	@Transactional
	public List<PermissionDto> putPermissions(List<PermissionDto> rows) {
		List<SnAynPermissionModel> models = rows.stream().map(d -> {
			if (d.toolCd() == null || d.category() == null || d.matcher() == null || d.decision() == null) {
				throw new IllegalArgumentException("toolCd, category, matcher, decision are required");
			}
			SnAynPermissionModel m = stamp(SnAynPermissionModel.builder().build());
			m.setToolCd(d.toolCd());
			m.setCategory(d.category());
			m.setMatcher(d.matcher());
			m.setDecision(d.decision());
			m.setPriority(d.priority() == null ? 0 : d.priority());
			m.setDescription(d.description());
			return m;
		}).toList();
		permissionAccess.replaceAll(models);
		return getPermissions();
	}

	// ── request (사람 응답 대기) ────────────────────────────────────────────

	/** 작업당 OPEN 1건은 DB 부분 유니크 인덱스가 보장한다. 위반 시 기존 OPEN 요청을 409 로 돌려준다. */
	public RequestDto createRequest(String taskId, RequestDto d) {
		findTask(taskId);
		if (d.kind() == null) throw new IllegalArgumentException("kind is required");
		SnAynRequestModel r = stamp(SnAynRequestModel.builder().build());
		r.setTaskId(taskId);
		r.setRunId(d.runId());
		r.setKind(d.kind());
		r.setStatus("OPEN");
		r.setQuestion(d.question());
		r.setOptions(json(d.options() == null ? List.of() : d.options()));
		r.setRecommend(d.recommend());
		r.setContext(d.context());
		r.setToolName(d.toolName());
		r.setInputHash(d.inputHash());
		try {
			return toDto(requestAccess.insert(r));
		} catch (DataIntegrityViolationException e) {
			List<SnAynRequestModel> open = requestAccess.search("OPEN", taskId);
			if (open.isEmpty()) throw e;
			throw new AyaanConflictException("open request already exists for task " + taskId, toDto(open.get(0)));
		}
	}

	public List<RequestDto> listRequests(String status, String taskId) {
		return requestAccess.search(status, taskId).stream().map(this::toDto).toList();
	}

	public RequestDto getRequest(String id) {
		return toDto(findRequest(id));
	}

	@Transactional
	public RequestDto answerRequest(String id, AnswerReq req) {
		findRequest(id);
		if (requestAccess.answer(id, req.answer(), req.decision(), req.answeredBy()) == 0) {
			throw new AyaanConflictException("request " + id + " is not OPEN", toDto(findRequest(id)));
		}
		return toDto(findRequest(id));
	}

	@Transactional
	public RequestDto consumeRequest(String id) {
		findRequest(id);
		if (requestAccess.consume(id) == 0) {
			throw new AyaanConflictException("request " + id + " is not ANSWERED", null);
		}
		return toDto(findRequest(id));
	}

	@Transactional
	public RequestDto cancelRequest(String id) {
		findRequest(id);
		if (requestAccess.cancel(id) == 0) {
			throw new AyaanConflictException("request " + id + " is not OPEN", null);
		}
		return toDto(findRequest(id));
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
		// PUT replaces promptTemplate (null clears it); NOT NULL columns keep their value on null
		if (d.workspacePath() != null) w.setWorkspacePath(d.workspacePath());
		if (d.defaultTool() != null) w.setDefaultTool(d.defaultTool());
		w.setPromptTemplate(d.promptTemplate());
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
		t.setPhase(d.phase() != null ? d.phase() : "DEV");
		t.setSkillCd(d.skillCd());
		t.setParentTaskId(d.parentTaskId());
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
		return TaskDto.from(t, runs, totals(runs), listRequests(null, id));
	}

	/**
	 * 조건부 전이. 같은 작업공간에 이미 RUNNING 이 있어 부분 유니크 인덱스(ux_ayn_task_running_ws)에 걸리면 409.
	 * 위반 예외를 잡아야 해서 @Transactional 대신 TransactionTemplate 으로 트랜잭션 경계를 직접 둔다.
	 */
	public TaskDto transition(String id, TransitionReq req) {
		if (req.expectedStatus() == null || req.nextStatus() == null) {
			throw new IllegalArgumentException("expectedStatus and nextStatus are required");
		}
		try {
			return new TransactionTemplate(txManager).execute(tx -> doTransition(id, req));
		} catch (DataIntegrityViolationException e) {
			throw new AyaanConflictException("transition to " + req.nextStatus() + " violates a uniqueness rule (workspace busy or active issue task)", null);
		}
	}

	private TaskDto doTransition(String id, TransitionReq req) {
		findTask(id); // 없으면 404 (조건 불일치 409 와 구분)
		if (taskAccess.transition(id, req.expectedStatus(), req.nextStatus()) == 0) {
			throw new AyaanConflictException("task " + id + " is not in status " + req.expectedStatus(), null);
		}
		SnAynTaskModel t = findTask(id);
		(req.fields() == null ? Map.<String, Object>of() : req.fields()).forEach((k, v) -> {
			switch (k) {
				case "resumeAt" -> t.setResumeAt(as(v, LocalDateTime.class));
				case "pauseReason" -> t.setPauseReason(as(v, String.class));
				case "resumeCount" -> t.setResumeCount(as(v, Integer.class));
				case "cliSessionId" -> t.setCliSessionId(as(v, String.class));
				case "toolCd" -> t.setToolCd(as(v, String.class));
				case "resultSummary" -> t.setResultSummary(as(v, String.class));
				case "failReason" -> t.setFailReason(as(v, String.class));
				case "leaseOwner" -> t.setLeaseOwner(as(v, String.class));
				case "leaseUntil" -> t.setLeaseUntil(as(v, LocalDateTime.class));
				case "startedAt" -> t.setStartedAt(as(v, LocalDateTime.class));
				case "finishedAt" -> t.setFinishedAt(as(v, LocalDateTime.class));
				case "planDocPath" -> t.setPlanDocPath(as(v, String.class));
				default -> throw new IllegalArgumentException("unknown field: " + k);
			}
		});
		return TaskDto.from(taskAccess.saveTask(t));
	}

	/** 변환 불가 값(객체를 문자열로 등)은 400 */
	private <T> T as(Object v, Class<T> type) {
		try {
			return om.convertValue(v, type);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("invalid value for " + type.getSimpleName() + ": " + v);
		}
	}

	/** 선점 성공 시 RUNNING Task, 대상이 없거나 같은 작업공간 선점 경쟁에서 지면(unique 위반) null */
	public TaskDto claim(ClaimReq req) {
		try {
			String id = new TransactionTemplate(txManager).execute(tx -> {
				List<String> ids = taskAccess.claim(req.leaseOwner(), req.leaseSeconds(), req.toolCd(), req.graceSeconds());
				return ids.isEmpty() ? null : ids.get(0);
			});
			return id == null ? null : TaskDto.from(findTask(id));
		} catch (DataIntegrityViolationException e) {
			return null;
		}
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

	private SnAynRequestModel findRequest(String id) {
		return requestAccess.get(id).orElseThrow(() -> new EntityNotFoundException("request not found: " + id));
	}

	private String json(List<String> v) {
		try {
			return om.writeValueAsString(v);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("invalid options");
		}
	}

	private List<String> options(String raw) {
		if (raw == null || raw.isBlank()) return List.of();
		try {
			return om.readValue(raw, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException e) {
			return List.of();
		}
	}

	private RequestDto toDto(SnAynRequestModel r) {
		return new RequestDto(r.getObjId(), r.getTaskId(), r.getRunId(), r.getKind(), r.getStatus(), r.getQuestion(),
			options(r.getOptions()), r.getRecommend(), r.getContext(), r.getToolName(), r.getInputHash(), r.getAnswer(),
			r.getDecision(), r.getAnsweredBy(), r.getAnsweredAt(), r.getConsumedAt(), r.getCreatedAt());
	}

	private static SkillDto toDto(SnAynSkillModel s) {
		return new SkillDto(s.getSkillCd(), s.getSkillNm(), s.getToolCd(), s.getPhase(), s.getInvokeText(), s.getPluginRef(),
			s.getDescription(), s.getEnabled(), s.getSortNo());
	}

	private static PermissionDto toDto(SnAynPermissionModel p) {
		return new PermissionDto(p.getObjId(), p.getToolCd(), p.getCategory(), p.getMatcher(), p.getDecision(), p.getPriority(),
			p.getDescription());
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
			t.getMaxConcurrency(), t.getFallbackResetRule(), t.getBudgetTokens(), t.getBudgetPeriod(), t.getPlanSkillCd(),
			t.getDevSkillCd());
	}

	private static WorkspaceDto toDto(SnAynWorkspaceModel w) {
		return new WorkspaceDto(w.getProjectKey(), w.getWorkspacePath(), w.getDefaultTool(), w.getPromptTemplate());
	}

	private static QuotaDto toDto(SnAynToolQuotaModel q) {
		return new QuotaDto(q.getToolCd(), q.getWindowCd(), q.getUsedPct(), q.getUsedTokens(), q.getLimitTokens(), q.getResetsAt(),
			q.getStatus(), q.getSource(), q.getObservedAt());
	}

}
