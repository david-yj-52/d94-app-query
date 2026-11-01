package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.apService.cira.dto.ChangeStatusRequest;
import tsh.d94.app.server.apService.cira.dto.CompleteSprintRequest;
import tsh.d94.app.server.apService.cira.dto.CreateSprintRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.dto.SprintResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/** 스프린트 생성 → 이슈 배정 → 시작 → 완료(미완료 이슈 처리)를 실제 DB 에서 확인 */
@IntegrationTest
class SprintServiceIT {

	@Autowired
	private SprintService sprintService;
	@Autowired
	private IssueService issueService;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;

	private ProjectResponse project;

	@BeforeEach
	void setUp() {
		String email = CiraFixtures.uniqueEmail("sprint");
		TestContexts.login(email);
		fixtures.user(email);
		project = fixtures.project("SCRUM");
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private SprintResponse sprint(String name) {
		CreateSprintRequest req = new CreateSprintRequest();
		req.setSprintNm(name);
		req.setGoal("goal");
		req.setStartDt(LocalDate.now());
		req.setEndDt(LocalDate.now().plusWeeks(2));
		return sprintService.createSprint(project.getId(), req);
	}

	private String sprintOf(String issueId) {
		return jdbc.queryForObject("select sprint_id from sn_cira_issue where obj_id = ?", String.class, issueId);
	}

	private void moveToDone(String issueId) {
		// 허용 전이: To Do → Done
		// ChangeStatusRequest 는 getter 만 있다 (요청 본문처럼 Jackson 으로 만든다)
		ChangeStatusRequest req = new com.fasterxml.jackson.databind.ObjectMapper()
			.convertValue(java.util.Map.of("statusId", fixtures.statusId(project.getId(), "Done")),
				ChangeStatusRequest.class);
		issueService.changeStatus(issueId, req);
	}

	@Test
	@DisplayName("시작 → 완료(BACKLOG): 완료 이슈는 스프린트에 남고 미완료 이슈는 백로그로 빠진다")
	void sprintLifecycle_completeToBacklog() {
		SprintResponse s = sprint("Sprint 1");
		IssueResponse done = fixtures.issue(project.getId(), "끝난 일", null);
		IssueResponse open = fixtures.issue(project.getId(), "남은 일", null);
		sprintService.assignIssueToSprint(s.getId(), done.getId());
		sprintService.assignIssueToSprint(s.getId(), open.getId());
		moveToDone(done.getId());

		assertThat(sprintService.startSprint(s.getId()).getSprintStat()).isEqualTo("Active");
		CompleteSprintRequest complete = new CompleteSprintRequest();
		complete.setIncompleteIssueAction("BACKLOG");
		assertThat(sprintService.completeSprint(s.getId(), complete).getSprintStat()).isEqualTo("Completed");

		assertThat(sprintOf(done.getId())).isEqualTo(s.getId());
		assertThat(sprintOf(open.getId())).isNull();
		assertThat(jdbc.queryForObject("select sprint_stat from sn_cira_sprint where obj_id = ?", String.class,
			s.getId())).isEqualTo("Completed");
	}

	@Test
	@DisplayName("완료(NEXT_SPRINT): 미완료 이슈가 다음 스프린트로 옮겨진다")
	void completeSprint_moveIncompleteToNextSprint() {
		SprintResponse current = sprint("Sprint A");
		SprintResponse next = sprint("Sprint B");
		IssueResponse open = fixtures.issue(project.getId(), "이월", null);
		sprintService.assignIssueToSprint(current.getId(), open.getId());
		sprintService.startSprint(current.getId());

		CompleteSprintRequest complete = new CompleteSprintRequest();
		complete.setIncompleteIssueAction("NEXT_SPRINT");
		complete.setNextSprintId(next.getId());
		sprintService.completeSprint(current.getId(), complete);

		assertThat(sprintOf(open.getId())).isEqualTo(next.getId());
	}

	@Test
	@DisplayName("활성 스프린트가 있으면 다른 스프린트를 시작할 수 없다")
	void startSprint_whenAnotherActive_isRejected() {
		SprintResponse a = sprint("A");
		SprintResponse b = sprint("B");
		sprintService.startSprint(a.getId());

		assertThatThrownBy(() -> sprintService.startSprint(b.getId())).isInstanceOf(CiraException.class);
	}

	@Test
	@DisplayName("시작하지 않은 스프린트는 완료할 수 없다")
	void completeSprint_notActive_isRejected() {
		SprintResponse s = sprint("planned");
		CompleteSprintRequest complete = new CompleteSprintRequest();
		complete.setIncompleteIssueAction("BACKLOG");

		assertThatThrownBy(() -> sprintService.completeSprint(s.getId(), complete)).isInstanceOf(CiraException.class);
	}

}
