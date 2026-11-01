package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.UnexpectedRollbackException;

import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/**
 * 이슈 생성의 트랜잭션·DB 결과 검증. 테스트 메서드에 트랜잭션을 걸지 않는다
 * → 서비스가 운영처럼 직접 커밋하고, 결과는 JdbcTemplate 로 DB 에서 다시 읽는다.
 * 정상 경로 요청은 상태·유형을 채워 보내 결함(D3)과 분리하고, 결함은 각각 따로 재현한다.
 */
@IntegrationTest
class IssueServiceIT {

	@Autowired
	private IssueService issueService;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;

	private GsUserModel reporter;
	private GsUserModel assignee;
	private ProjectResponse project;
	private String todoStatusId;
	private String taskTypeId;

	@BeforeEach
	void setUp() {
		String reporterEmail = CiraFixtures.uniqueEmail("reporter");
		TestContexts.login(reporterEmail);
		reporter = fixtures.user(reporterEmail);
		assignee = fixtures.user(CiraFixtures.uniqueEmail("assignee"));
		project = fixtures.project("SCRUM");
		todoStatusId = fixtures.statusId(project.getId(), "To Do");
		taskTypeId = fixtures.issueType("Task");
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	/** 웹 CreateIssueModal 이 보내는 것과 같은 모양 (상태·유형 없음) */
	private CreateIssueRequest webRequest(String assigneeId) {
		CreateIssueRequest req = new CreateIssueRequest();
		req.setTitle("이슈 1");
		req.setContent("이슈 1 에 대한 설명이에요");
		req.setPriority("MEDIUM");
		req.setAssigneeId(assigneeId);
		req.setStoryPnt(BigDecimal.ONE);
		req.setDueDt(LocalDate.of(2026, 12, 30));
		return req;
	}

	/** 상태·유형까지 채운 요청 (정상 경로) */
	private CreateIssueRequest fullRequest(String assigneeId) {
		CreateIssueRequest req = webRequest(assigneeId);
		req.setStatusId(todoStatusId);
		req.setIssueTypeId(taskTypeId);
		return req;
	}

	private int issueCount() {
		return jdbc.queryForObject("select count(*) from sn_cira_issue where project_id = ?", Integer.class,
			project.getId());
	}

	@Test
	@DisplayName("정상 생성 → 이슈·로그·보드 위치 행이 커밋되고 키는 {projectKey}-1")
	void createIssue_full_commitsIssueLogAndPosition() {
		IssueResponse res = issueService.createIssue(project.getId(), fullRequest(assignee.getObjId()));

		assertThat(res.getIssueKey()).isEqualTo(project.getKey() + "-1");
		assertThat(res.getAssignee().getEmail()).isEqualTo(assignee.getEmail());
		assertThat(res.getStatusNm()).isEqualTo("To Do");
		assertThat(res.getIssueTypeNm()).isEqualTo("Task");

		Map<String, Object> row = jdbc.queryForMap(
			"select issue_key, assignee_id, reporter_id, status_id, tenant from sn_cira_issue where obj_id = ?",
			res.getId());
		assertThat(row.get("issue_key")).isEqualTo(project.getKey() + "-1");
		assertThat(row.get("assignee_id")).isEqualTo(assignee.getObjId());
		assertThat(row.get("reporter_id")).isEqualTo(reporter.getObjId());
		assertThat(row.get("status_id")).isEqualTo(todoStatusId);
		assertThat(row.get("tenant")).isEqualTo(TestContexts.TENANT);

		assertThat(jdbc.queryForObject("select issue_sequence from sn_cira_project where obj_id = ?",
			Integer.class, project.getId())).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from sn_cira_issue_log where issue_id = ?",
			Integer.class, res.getId())).isEqualTo(1);
		// 상태 "To Do" 와 매핑된 기본 보드 컬럼에 놓인다
		assertThat(jdbc.queryForObject("""
			select c.column_nm from sn_cira_issue_position p
			join sn_cira_board_column c on c.obj_id = p.column_id
			where p.issue_id = ?""", String.class, res.getId())).isEqualTo("To Do");
	}

	@Test
	@DisplayName("[D3 재현] 웹과 같은 요청(상태·유형 없음)도 생성된다 — 현재 UnexpectedRollbackException(500)")
	void createIssue_webShapedRequest_succeeds() {
		IssueResponse res = issueService.createIssue(project.getId(), webRequest(null));

		assertThat(res.getIssueKey()).isEqualTo(project.getKey() + "-1");
		assertThat(issueCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("[D1 재현] 담당자 이메일을 assigneeId 로 보내면 클라이언트 오류로 거절되고 아무것도 저장되지 않는다")
	void createIssue_withAssigneeEmail_isRejected() {
		Throwable thrown = catchThrowable(
			() -> issueService.createIssue(project.getId(), fullRequest(assignee.getEmail())));

		assertThat(thrown).as("오류 없이 이메일이 assignee_id 로 저장되면 결함").isNotNull();
		assertThat(thrown).isNotInstanceOf(UnexpectedRollbackException.class)
			.isInstanceOfAny(IllegalArgumentException.class, CiraException.class);
		assertThat(issueCount()).isZero();
	}

	@Test
	@DisplayName("[D5 재현] 설명이 255자를 넘어도 저장된다 (Flyway 는 text, 엔티티는 기본 varchar(255))")
	void createIssue_withLongContent_isStored() {
		CreateIssueRequest req = fullRequest(null);
		req.setContent("가".repeat(1000));

		IssueResponse res = issueService.createIssue(project.getId(), req);

		String stored = jdbc.queryForObject(
			"select content from sn_cira_issue where obj_id = ?", String.class, res.getId());
		assertThat(stored).hasSize(1000);
	}

	@Test
	@DisplayName("프로젝트 멤버가 아니면 생성 거부, 시퀀스도 증가하지 않음")
	void createIssue_byNonMember_isRejectedAndSequenceUnchanged() {
		String outsiderEmail = CiraFixtures.uniqueEmail("outsider");
		fixtures.user(outsiderEmail);
		TestContexts.login(outsiderEmail);

		assertThatThrownBy(() -> issueService.createIssue(project.getId(), fullRequest(null)))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(jdbc.queryForObject("select issue_sequence from sn_cira_project where obj_id = ?",
			Integer.class, project.getId())).isZero();
	}

	@Test
	@DisplayName("연속 생성 시 이슈 키가 1씩 증가")
	void createIssue_twice_incrementsKey() {
		IssueResponse first = issueService.createIssue(project.getId(), fullRequest(null));
		IssueResponse second = issueService.createIssue(project.getId(), fullRequest(null));

		assertThat(first.getIssueKey()).isEqualTo(project.getKey() + "-1");
		assertThat(second.getIssueKey()).isEqualTo(project.getKey() + "-2");
	}

}
