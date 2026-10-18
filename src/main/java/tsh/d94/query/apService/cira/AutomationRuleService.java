package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.automation.events.IssueAssignedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueCreatedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueStatusChangedEvent;
import tsh.d94.app.server.apService.cira.dto.automation.AutomationExecResponse;
import tsh.d94.app.server.apService.cira.dto.automation.AutomationRuleRequest;
import tsh.d94.app.server.apService.cira.dto.automation.AutomationRuleResponse;
import tsh.d94.app.server.data.orm.cira.ciraAutoExecution.SnCiraAutoExecutionAccess;
import tsh.d94.app.server.data.orm.cira.ciraAutoExecution.SnCiraAutoExecutionModel;
import tsh.d94.app.server.data.orm.cira.ciraAutoRule.SnCiraAutoRuleAccess;
import tsh.d94.app.server.data.orm.cira.ciraAutoRule.SnCiraAutoRuleModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationRuleService {

	private final SnCiraAutoRuleAccess ruleAccess;
	private final SnCiraAutoExecutionAccess execAccess;
	private final ApplicationEventPublisher eventPublisher;

	// ─── CRUD ────────────────────────────────────────────────────────────

	@Transactional
	public AutomationRuleResponse createRule(String projectId, AutomationRuleRequest request) {
		SnCiraAutoRuleModel rule = SnCiraAutoRuleModel.builder()
			.projectId(projectId)
			.ruleNm(request.getRuleNm())
			.triggerType(request.getTriggerType())
			.triggerConfig(request.getTriggerConfig())
			.condConfig(request.getCondConfig())
			.actionType(request.getActionType())
			.actionConfig(request.getActionConfig())
			.isActive(request.getIsActive() != null ? request.getIsActive() : true)
			.sortOrd(request.getSortOrd() != null ? request.getSortOrd() : 0)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("AUTOMATION-RULE")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateRule")
			.prevEvntNm("None")
			.build();
		ruleAccess.save(rule);
		return mapToResponse(rule);
	}

	public List<AutomationRuleResponse> listRules(String projectId) {
		return ruleAccess.findByProjectId(projectId).stream()
			.map(this::mapToResponse)
			.collect(Collectors.toList());
	}

	public AutomationRuleResponse getRule(String ruleId) {
		SnCiraAutoRuleModel rule = ruleAccess.findById(ruleId);
		return mapToResponse(rule);
	}

	@Transactional
	public AutomationRuleResponse updateRule(String ruleId, AutomationRuleRequest request) {
		SnCiraAutoRuleModel rule = ruleAccess.findById(ruleId);
		if (request.getRuleNm() != null)
			rule.setRuleNm(request.getRuleNm());
		if (request.getTriggerType() != null)
			rule.setTriggerType(request.getTriggerType());
		if (request.getTriggerConfig() != null)
			rule.setTriggerConfig(request.getTriggerConfig());
		if (request.getCondConfig() != null)
			rule.setCondConfig(request.getCondConfig());
		if (request.getActionType() != null)
			rule.setActionType(request.getActionType());
		if (request.getActionConfig() != null)
			rule.setActionConfig(request.getActionConfig());
		if (request.getIsActive() != null)
			rule.setIsActive(request.getIsActive());
		if (request.getSortOrd() != null)
			rule.setSortOrd(request.getSortOrd());
		rule.setEvtNm("UpdateRule");
		rule.setPrevEvntNm("CreateRule");
		ruleAccess.save(rule);
		return mapToResponse(rule);
	}

	@Transactional
	public void deleteRule(String ruleId) {
		SnCiraAutoRuleModel rule = ruleAccess.findById(ruleId);
		rule.setIsActive(false);
		rule.setEvtNm("DeleteRule");
		rule.setPrevEvntNm("UpdateRule");
		ruleAccess.save(rule);
	}

	// ─── 실행이력 조회 ────────────────────────────────────────────────────

	public List<AutomationExecResponse> listExecutions(String ruleId) {
		return execAccess.findByRuleId(ruleId).stream()
			.map(this::mapExecToResponse)
			.collect(Collectors.toList());
	}

	// ─── 이벤트 발행 헬퍼 ────────────────────────────────────────────────

	public void publishIssueCreatedEvent(String issueId, String projectId, String createdBy) {
		eventPublisher.publishEvent(new IssueCreatedEvent(this, issueId, projectId, createdBy));
	}

	public void publishIssueStatusChangedEvent(String issueId, String projectId,
		String fromStatus, String toStatus, String changedBy) {
		eventPublisher.publishEvent(
			new IssueStatusChangedEvent(this, issueId, projectId, fromStatus, toStatus, changedBy)
		);
	}

	public void publishIssueAssignedEvent(String issueId, String projectId,
		String assigneeId, String assignedBy) {
		eventPublisher.publishEvent(
			new IssueAssignedEvent(this, issueId, projectId, assigneeId, assignedBy)
		);
	}

	// ─── 매핑 ────────────────────────────────────────────────────────────

	private AutomationRuleResponse mapToResponse(SnCiraAutoRuleModel rule) {
		return AutomationRuleResponse.builder()
			.ruleId(rule.getObjId())
			.projectId(rule.getProjectId())
			.ruleNm(rule.getRuleNm())
			.triggerType(rule.getTriggerType())
			.triggerConfig(rule.getTriggerConfig())
			.condConfig(rule.getCondConfig())
			.actionType(rule.getActionType())
			.actionConfig(rule.getActionConfig())
			.isActive(rule.getIsActive())
			.sortOrd(rule.getSortOrd())
			.build();
	}

	private AutomationExecResponse mapExecToResponse(SnCiraAutoExecutionModel exec) {
		return AutomationExecResponse.builder()
			.execId(exec.getObjId())
			.ruleId(exec.getRuleId())
			.issueId(exec.getIssueId())
			.execStat(exec.getExecStat())
			.executedAt(exec.getExecutedAt())
			.errMsg(exec.getErrMsg())
			.build();
	}

}
