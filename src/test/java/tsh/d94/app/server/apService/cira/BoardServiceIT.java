package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.MoveIssueRequest;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/** 보드 이동(컬럼·순서·상태 동기화)을 실제 DB 에서 확인 */
@IntegrationTest
class BoardServiceIT {

	@Autowired
	private BoardService boardService;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;

	private ProjectResponse project;
	private String boardId;

	@BeforeEach
	void setUp() {
		String email = CiraFixtures.uniqueEmail("board");
		TestContexts.login(email);
		fixtures.user(email);
		project = fixtures.project("KANBAN");
		boardId = jdbc.queryForObject("select obj_id from sn_cira_board where project_id = ?", String.class,
			project.getId());
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private String columnId(String columnNm) {
		return jdbc.queryForObject("select obj_id from sn_cira_board_column where board_id = ? and column_nm = ?",
			String.class, boardId, columnNm);
	}

	private MoveIssueRequest move(String columnNm, String afterIssueId, String beforeIssueId) {
		MoveIssueRequest req = new MoveIssueRequest();
		req.setTargetColumnId(columnId(columnNm));
		req.setAfterIssueId(afterIssueId);
		req.setBeforeIssueId(beforeIssueId);
		return req;
	}

	/** 컬럼 안 이슈 id 를 rank 순서대로 */
	private List<String> columnOrder(String columnNm) {
		return jdbc.queryForList(
			"select issue_id from sn_cira_issue_position where column_id = ? order by rank_str", String.class,
			columnId(columnNm));
	}

	@Test
	@DisplayName("다른 컬럼으로 옮기면 위치 행이 그 컬럼으로 바뀌고 이슈 상태도 컬럼 상태로 바뀐다")
	void moveIssue_toOtherColumn_updatesPositionAndStatus() {
		IssueResponse issue = fixtures.issue(project.getId(), "옮길 이슈", null);

		boardService.moveIssue(boardId, issue.getId(), move("In Progress", null, null));

		assertThat(columnOrder("In Progress")).containsExactly(issue.getId());
		assertThat(columnOrder("To Do")).isEmpty();
		assertThat(jdbc.queryForObject("select status_id from sn_cira_issue where obj_id = ?", String.class,
			issue.getId())).isEqualTo(fixtures.statusId(project.getId(), "In Progress"));
	}

	@Test
	@DisplayName("같은 컬럼 안에서 다른 이슈 앞으로 옮기면 rank 순서가 바뀐다")
	void moveIssue_withinColumn_reordersByRank() {
		IssueResponse a = fixtures.issue(project.getId(), "A", null);
		IssueResponse b = fixtures.issue(project.getId(), "B", null);
		assertThat(columnOrder("To Do")).containsExactly(a.getId(), b.getId());

		boardService.moveIssue(boardId, b.getId(), move("To Do", null, a.getId()));

		assertThat(columnOrder("To Do")).containsExactly(b.getId(), a.getId());
	}

	@Test
	@DisplayName("[D9] 보드 이동도 워크플로우 전이 규칙을 따른다 (To Do → In Review 는 허용 전이가 아님)")
	void moveIssue_disallowedTransition_isRejected() {
		IssueResponse issue = fixtures.issue(project.getId(), "전이 검사", null);

		assertThatThrownBy(() -> boardService.moveIssue(boardId, issue.getId(), move("In Review", null, null)))
			.as("IssueService.changeStatus 는 validateTransition 을 하지만 BoardService.moveIssue 는 하지 않음")
			.isInstanceOf(CiraException.class);
	}

	@Test
	@DisplayName("[D9] 보드 이동으로 상태가 바뀌면 이슈 변경 이력(sn_cira_issue_log)이 남는다")
	void moveIssue_statusChange_isLogged() {
		IssueResponse issue = fixtures.issue(project.getId(), "이력 검사", null);

		boardService.moveIssue(boardId, issue.getId(), move("In Progress", null, null));

		Integer statusLogs = jdbc.queryForObject(
			"select count(*) from sn_cira_issue_log where issue_id = ? and field_nm <> 'Issue'", Integer.class,
			issue.getId());
		assertThat(statusLogs).as("상태 변경 로그").isPositive();
	}

}
