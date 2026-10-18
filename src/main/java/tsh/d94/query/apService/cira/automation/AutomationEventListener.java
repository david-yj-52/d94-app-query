package tsh.d94.app.server.apService.cira.automation;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.automation.events.IssueAssignedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueCreatedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueStatusChangedEvent;
import tsh.d94.app.server.data.orm.cira.ciraAutoExecution.SnCiraAutoExecutionAccess;
import tsh.d94.app.server.data.orm.cira.ciraAutoExecution.SnCiraAutoExecutionModel;
import tsh.d94.app.server.data.orm.cira.ciraAutoRule.SnCiraAutoRuleAccess;
import tsh.d94.app.server.data.orm.cira.ciraAutoRule.SnCiraAutoRuleModel;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring ApplicationEvent 기반 자동화 이벤트 리스너
 * - @Async: 비동기 처리 (별도 스레드)
 * - 무한루프 방지: 최근 5분 내 동일 (rule_id, issue_id) 실행 이력 확인
 * - 실행 깊이 제한: ThreadLocal로 최대 3단계
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationEventListener {

	private static final int MAX_DEPTH = 3;
	private static final long DEDUP_MINUTES = 5;

	private static final ThreadLocal<Integer> EXECUTION_DEPTH = ThreadLocal.withInitial(() -> 0);

	private final SnCiraAutoRuleAccess ruleAccess;
	private final SnCiraAutoExecutionAccess execAccess;
	private final SnCiraIssueAccess issueAccess;
	private final ConditionEvaluator conditionEvaluator;
	private final ActionExecutor actionExecutor;

	// ─── 이벤트 핸들러 ───────────────────────────────────────────────────

	@Async("automationTaskExecutor")
	@EventListener
	@Transactional
	public void onIssueCreated(IssueCreatedEvent event) {
		log.debug("[자동화] IssueCreated 수신 - issueId={}", event.getIssueId());
		Map<String, Object> ctx = buildContextFromIssue(event.getIssueId());
		ctx.put("projectId", event.getProjectId());
		ctx.put("createdBy", event.getCreatedBy());
		processRules(event.getProjectId(), AutoTriggerType.ISSUE_CREATED.name(), event.getIssueId(), ctx);
	}

	@Async("automationTaskExecutor")
	@EventListener
	@Transactional
	public void onIssueStatusChanged(IssueStatusChangedEvent event) {
		log.debug("[자동화] IssueStatusChanged 수신 - issueId={}, {}→{}", event.getIssueId(), event.getFromStatus(),
			event.getToStatus());
		Map<String, Object> ctx = buildContextFromIssue(event.getIssueId());
		ctx.put("projectId", event.getProjectId());
		ctx.put("fromStatus", event.getFromStatus());
		ctx.put("toStatus", event.getToStatus());
		ctx.put("changedBy", event.getChangedBy());
		processRules(event.getProjectId(), AutoTriggerType.ISSUE_STATUS_CHANGED.name(), event.getIssueId(), ctx);
	}

	@Async("automationTaskExecutor")
	@EventListener
	@Transactional
	public void onIssueAssigned(IssueAssignedEvent event) {
		log.debug("[자동화] IssueAssigned 수신 - issueId={}, assigneeId={}", event.getIssueId(), event.getAssigneeId());
		Map<String, Object> ctx = buildContextFromIssue(event.getIssueId());
		ctx.put("projectId", event.getProjectId());
		ctx.put("assigneeId", event.getAssigneeId());
		ctx.put("assignedBy", event.getAssignedBy());
		processRules(event.getProjectId(), AutoTriggerType.ISSUE_ASSIGNED.name(), event.getIssueId(), ctx);
	}

	// ─── 규칙 처리 ───────────────────────────────────────────────────────

	private void processRules(String projectId, String triggerType, String issueId, Map<String, Object> ctx) {
		int depth = EXECUTION_DEPTH.get();
		if (depth >= MAX_DEPTH) {
			log.warn("[자동화] 실행 깊이 제한 초과 (depth={}) - projectId={}, triggerType={}", depth, projectId, triggerType);
			return;
		}
		EXECUTION_DEPTH.set(depth + 1);
		try {
			List<SnCiraAutoRuleModel> rules = ruleAccess.findActiveByProjectIdAndTriggerType(projectId, triggerType);
			for (SnCiraAutoRuleModel rule : rules) {
				processRule(rule, issueId, ctx);
			}
		} finally {
			EXECUTION_DEPTH.set(depth);
		}
	}

	private void processRule(SnCiraAutoRuleModel rule, String issueId, Map<String, Object> ctx) {
		String ruleId = rule.getObjId();

		// 무한루프 방지: 최근 5분 내 동일 (rule_id, issue_id) 실행 이력 확인
		OffsetDateTime since = OffsetDateTime.now().minusMinutes(DEDUP_MINUTES);
		List<SnCiraAutoExecutionModel> recentExecs = execAccess.findRecentByRuleIdAndIssueId(ruleId, issueId, since);
		if (!recentExecs.isEmpty()) {
			log.info("[자동화] 중복 실행 방지 SKIPPED - ruleId={}, issueId={}", ruleId, issueId);
			saveExecution(ruleId, issueId, "SKIPPED", null);
			return;
		}

		// 조건 평가
		if (!conditionEvaluator.evaluate(rule.getCondConfig(), ctx)) {
			log.debug("[자동화] 조건 불충족 - ruleId={}, issueId={}", ruleId, issueId);
			return;
		}

		// 액션 실행
		try {
			actionExecutor.execute(rule, issueId, ctx);
			saveExecution(ruleId, issueId, "SUCCESS", null);
			log.info("[자동화] 규칙 실행 완료 - ruleId={}, issueId={}", ruleId, issueId);
		} catch (Exception e) {
			log.error("[자동화] 규칙 실행 실패 - ruleId={}, issueId={}: {}", ruleId, issueId, e.getMessage());
			saveExecution(ruleId, issueId, "FAILED", e.getMessage());
		}
	}

	private void saveExecution(String ruleId, String issueId, String status, String errMsg) {
		SnCiraAutoExecutionModel exec = SnCiraAutoExecutionModel.builder()
			.ruleId(ruleId)
			.issueId(issueId)
			.execStat(status)
			.executedAt(OffsetDateTime.now())
			.errMsg(errMsg)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("AUTOMATION")
			.useStatCd(UseStatCd.Usable)
			.evtNm("AutoExecution")
			.prevEvntNm("None")
			.build();
		execAccess.save(exec);
	}

	private Map<String, Object> buildContextFromIssue(String issueId) {
		Map<String, Object> ctx = new HashMap<>();
		try {
			SnCiraIssueModel issue = issueAccess.findById(issueId);
			ctx.put("issueId", issue.getObjId());
			ctx.put("projectId", issue.getProjectId());
			ctx.put("priority", issue.getPriority());
			ctx.put("assigneeId", issue.getAssigneeId());
			ctx.put("statusId", issue.getStatusId());
			ctx.put("issueTypeId", issue.getIssueTypeId());
			ctx.put("reporterId", issue.getReporterId());
		} catch (Exception e) {
			log.warn("[자동화] 이슈 컨텍스트 빌드 실패 - issueId={}: {}", issueId, e.getMessage());
		}
		return ctx;
	}

}
