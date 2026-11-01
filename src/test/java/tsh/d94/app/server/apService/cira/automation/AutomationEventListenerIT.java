package tsh.d94.app.server.apService.cira.automation;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.apService.cira.AutomationRuleService;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.dto.automation.AutomationRuleRequest;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/**
 * 자동화 규칙이 이슈 이벤트 뒤에 비동기로 실행되는지 실제 DB·스레드풀로 확인.
 * 리스너는 {@code automationTaskExecutor} 에서 돌므로 결과 행이 생길 때까지 잠시 기다린다.
 */
@IntegrationTest
class AutomationEventListenerIT {

	private static final long WAIT_MS = 10_000;

	@Autowired
	private AutomationRuleService ruleService;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;

	private ProjectResponse project;
	private GsUserModel assignee;

	@BeforeEach
	void setUp() {
		String email = CiraFixtures.uniqueEmail("auto");
		TestContexts.login(email);
		fixtures.user(email);
		assignee = fixtures.user(CiraFixtures.uniqueEmail("auto-assignee"));
		project = fixtures.project("KANBAN");
		fixtures.member(project.getId(), assignee);
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private String rule(String triggerType, String actionType, String actionConfig) {
		AutomationRuleRequest req = new AutomationRuleRequest();
		req.setRuleNm("IT " + triggerType);
		req.setTriggerType(triggerType);
		req.setActionType(actionType);
		req.setActionConfig(actionConfig);
		req.setIsActive(true);
		return ruleService.createRule(project.getId(), req).getRuleId();
	}

	/**
	 * 규칙을 SQL 로 직접 넣는다. 서비스 경로(createRule)는 D11(jsonb ↔ String 매핑) 때문에 실패하므로,
	 * 리스너 동작 검증을 D11 과 분리하기 위해 jsonb 캐스트를 붙여 저장한다.
	 */
	private String ruleBySql(String triggerType, String actionType, String actionConfig) {
		String id = java.util.UUID.randomUUID().toString();
		jdbc.update("""
			insert into sn_cira_auto_rule (obj_id, project_id, rule_nm, trigger_type, action_type, action_config,
			  is_active, sort_ord, srv_id, tenant, trace_id, use_stat_cd, evnt_nm, prev_evnt_nm, created_at, modified_at)
			values (?, ?, ?, ?, ?, cast(? as jsonb), true, 0, 'it', ?, 'IT', 'Usable', 'ItFixture', 'None', now(), now())
			""", id, project.getId(), "IT " + triggerType, triggerType, actionType, actionConfig, TestContexts.TENANT);
		return id;
	}

	/** 조건이 참이 될 때까지 최대 WAIT_MS 동안 기다린다 */
	private List<String> waitForRows(String sql, Object... args) throws InterruptedException {
		long until = System.currentTimeMillis() + WAIT_MS;
		List<String> rows = jdbc.queryForList(sql, String.class, args);
		while (rows.isEmpty() && System.currentTimeMillis() < until) {
			Thread.sleep(200);
			rows = jdbc.queryForList(sql, String.class, args);
		}
		return rows;
	}

	@Test
	@DisplayName("[D11 재현] 서비스로 자동화 규칙을 만들 수 있다 (action_config 는 jsonb 컬럼)")
	void createRule_viaService_isStored() {
		String ruleId = rule("ISSUE_CREATED", "ADD_LABEL", "{}");

		assertThat(jdbc.queryForObject("select count(*) from sn_cira_auto_rule where obj_id = ?", Integer.class,
			ruleId)).isEqualTo(1);
	}

	@Test
	@DisplayName("ISSUE_CREATED 규칙이 실행되고 실행 이력이 SUCCESS 로 남는다")
	void issueCreated_ruleExecutes() throws InterruptedException {
		String ruleId = ruleBySql("ISSUE_CREATED", "ADD_LABEL", "{}");

		IssueResponse issue = fixtures.issue(project.getId(), "자동화 대상", assignee.getObjId());

		List<String> stats = waitForRows(
			"select exec_stat from sn_cira_auto_execution where rule_id = ? and issue_id = ?", ruleId, issue.getId());
		assertThat(stats).containsExactly("SUCCESS");
	}

	@Test
	@DisplayName("[D2 재현] ISSUE_CREATED → SEND_NOTIFICATION(대상 미지정) 은 새 이슈의 담당자에게 알림을 보낸다")
	void issueCreated_sendNotification_reachesAssignee() throws InterruptedException {
		ruleBySql("ISSUE_CREATED", "SEND_NOTIFICATION", "{\"title\":\"새 이슈\"}");

		IssueResponse issue = fixtures.issue(project.getId(), "알림 대상", assignee.getObjId());

		List<String> notifs = waitForRows(
			"select notif_type from sn_cira_notification where user_id = ? and resource_id = ? and notif_type = 'AUTOMATION'",
			assignee.getObjId(), issue.getId());
		assertThat(notifs)
			.as("리스너가 커밋 전에 돌아 이슈를 못 찾으면(NOT_FOUND) 컨텍스트에 assigneeId 가 없어 알림이 안 간다")
			.hasSize(1);
	}

}
