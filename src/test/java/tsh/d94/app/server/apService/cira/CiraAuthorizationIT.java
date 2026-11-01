package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.apService.cira.dto.CreateCommentRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.milestone.MilestoneRequest;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.dto.version.VersionRequest;
import tsh.d94.app.server.apService.cira.dto.wiki.WikiPageRequest;
import tsh.d94.app.server.apService.cira.dto.automation.AutomationRuleRequest;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/**
 * [D24] 프로젝트 멤버가 아닌 사용자가 다른 프로젝트의 데이터를 만들 수 있는지 서비스별로 확인한다.
 * 같은 tenant 안의 남의 프로젝트에 대해, 쓰기 요청이 거절되고 DB 에 행이 남지 않아야 한다.
 */
@IntegrationTest
class CiraAuthorizationIT {

	@Autowired
	private CommentService commentService;
	@Autowired
	private VersionService versionService;
	@Autowired
	private MilestoneService milestoneService;
	@Autowired
	private WikiService wikiService;
	@Autowired
	private AutomationRuleService automationRuleService;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;

	private ProjectResponse project;
	private IssueResponse issue;

	@BeforeEach
	void setUp() {
		String ownerEmail = CiraFixtures.uniqueEmail("authz-owner");
		TestContexts.login(ownerEmail);
		fixtures.user(ownerEmail);
		project = fixtures.project("KANBAN");
		issue = fixtures.issue(project.getId(), "남의 프로젝트 이슈", null);

		// 같은 tenant 의 다른 사용자 (이 프로젝트 멤버 아님)
		String strangerEmail = CiraFixtures.uniqueEmail("authz-stranger");
		TestContexts.login(strangerEmail);
		fixtures.user(strangerEmail);
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	/** 쓰기가 거절되고(예외) 결과 행이 생기지 않았는지 확인 */
	private void assertRejected(String what, Supplier<?> write, String countSql) {
		Throwable thrown = catchThrowable(write::get);
		Integer rows = jdbc.queryForObject(countSql, Integer.class, project.getId());
		assertThat(Map.of("rejected", thrown != null, "rows", rows))
			.as(what + " — 비멤버의 쓰기가 성공함")
			.isEqualTo(Map.of("rejected", true, "rows", 0));
	}

	@Test
	@DisplayName("[D24] 비멤버는 남의 이슈에 댓글을 달 수 없다")
	void comment() {
		CreateCommentRequest req = new CreateCommentRequest();
		req.setContent("지나가던 사람");
		assertRejected("CommentService.createComment", () -> commentService.createComment(issue.getId(), req),
			"select count(*) from sn_cira_comment c join sn_cira_issue i on i.obj_id = c.issue_id where i.project_id = ?");
	}

	@Test
	@DisplayName("[D24] 비멤버는 남의 프로젝트에 버전을 만들 수 없다")
	void version() {
		VersionRequest req = new VersionRequest();
		req.setVersionNm("v-hack");
		assertRejected("VersionService.createVersion", () -> versionService.createVersion(project.getId(), req),
			"select count(*) from sn_cira_version where project_id = ?");
	}

	@Test
	@DisplayName("[D24] 비멤버는 남의 프로젝트에 마일스톤을 만들 수 없다")
	void milestone() {
		MilestoneRequest req = new MilestoneRequest();
		req.setName("m-hack");
		req.setDueDate(LocalDate.now().plusDays(7));
		assertRejected("MilestoneService.createMilestone", () -> milestoneService.createMilestone(project.getId(), req),
			"select count(*) from sn_cira_milestone where project_id = ?");
	}

	@Test
	@DisplayName("[D24] 비멤버는 남의 프로젝트에 위키 페이지를 만들 수 없다")
	void wiki() {
		WikiPageRequest req = new WikiPageRequest();
		req.setTitle("w-hack");
		req.setContent("x");
		assertRejected("WikiService.createPage", () -> wikiService.createPage(project.getId(), req),
			"select count(*) from sn_cira_wiki_page where project_id = ?");
	}

	@Test
	@DisplayName("[D24] 비멤버는 남의 프로젝트에 자동화 규칙을 만들 수 없다 (D11 이 고쳐져도 막혀야 함)")
	void automationRule() {
		AutomationRuleRequest req = new AutomationRuleRequest();
		req.setRuleNm("r-hack");
		req.setTriggerType("ISSUE_CREATED");
		req.setActionType("ADD_LABEL");
		Throwable thrown = catchThrowable(() -> automationRuleService.createRule(project.getId(), req));

		// D11(jsonb) 때문에 지금은 DB 오류로 실패할 수 있다 → 권한 오류로 거절돼야 통과
		assertThat(thrown).as("권한 검사 없이 DB 단계까지 감").isNotNull()
			.isNotInstanceOf(org.springframework.dao.DataAccessException.class);
	}

}
