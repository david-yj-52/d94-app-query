package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.apService.cira.dto.CommentResponse;
import tsh.d94.app.server.apService.cira.dto.CreateCommentRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/** 댓글 저장·답글 트리·알림(담당자/보고자/멘션)을 실제 DB 에서 확인 */
@IntegrationTest
class CommentServiceIT {

	@Autowired
	private CommentService commentService;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;

	private GsUserModel reporter;
	private GsUserModel assignee;
	private IssueResponse issue;

	@BeforeEach
	void setUp() {
		String reporterEmail = CiraFixtures.uniqueEmail("reporter");
		TestContexts.login(reporterEmail);
		reporter = fixtures.user(reporterEmail);
		assignee = fixtures.user(CiraFixtures.uniqueEmail("assignee"));
		ProjectResponse project = fixtures.project("KANBAN");
		fixtures.member(project.getId(), assignee);
		issue = fixtures.issue(project.getId(), "댓글 대상", assignee.getObjId());
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private CommentResponse comment(String content, String parentId) {
		CreateCommentRequest req = new CreateCommentRequest();
		req.setContent(content);
		req.setParentId(parentId);
		return commentService.createComment(issue.getId(), req);
	}

	private List<String> notificationTypes(String userId) {
		return jdbc.queryForList("select notif_type from sn_cira_notification where user_id = ? and resource_id = ?",
			String.class, userId, issue.getId());
	}

	@Test
	@DisplayName("댓글·답글 저장 → 조회 시 최상위 댓글 아래에 답글이 붙는다")
	void createComment_andReply_formTree() {
		CommentResponse root = comment("첫 댓글", null);
		comment("답글", root.getId());

		List<CommentResponse> list = commentService.getComments(issue.getId());

		assertThat(list).hasSize(1);
		assertThat(list.get(0).getContent()).isEqualTo("첫 댓글");
		assertThat(list.get(0).getReplies()).extracting(CommentResponse::getContent).containsExactly("답글");
		assertThat(jdbc.queryForObject("select count(*) from sn_cira_comment where issue_id = ?", Integer.class,
			issue.getId())).isEqualTo(2);
	}

	@Test
	@DisplayName("보고자가 댓글을 달면 담당자에게 COMMENT_ADDED 알림, 작성자 본인은 받지 않는다")
	void createComment_notifiesAssigneeNotAuthor() {
		comment("확인 부탁드립니다", null);

		assertThat(notificationTypes(assignee.getObjId())).containsExactly("COMMENT_ADDED");
		assertThat(notificationTypes(reporter.getObjId())).isEmpty();
	}

	@Test
	@DisplayName("[D8 재현] @이메일 멘션은 멘션된 사용자에게 COMMENT_MENTIONED 알림을 보낸다")
	void createComment_mentionByEmail_notifiesMentionedUser() {
		GsUserModel mentioned = fixtures.user(CiraFixtures.uniqueEmail("mentioned"));

		comment("@" + mentioned.getEmail() + " 이것 좀 봐주세요", null);

		assertThat(notificationTypes(mentioned.getObjId()))
			.as("멘션 정규식 @([\\w.-]+) 은 이메일의 '@' 앞에서 끊겨 사용자를 찾지 못함")
			.containsExactly("COMMENT_MENTIONED");
	}

	@Test
	@DisplayName("[D5 재현] 255자를 넘는 댓글도 저장된다")
	void createComment_longContent_isStored() {
		CommentResponse saved = comment("나".repeat(600), null);

		assertThat(jdbc.queryForObject("select length(content) from sn_cira_comment where obj_id = ?",
			Integer.class, saved.getId())).isEqualTo(600);
	}

	@Test
	@DisplayName("없는 부모 댓글에 답글을 달면 거절된다")
	void createComment_withUnknownParent_isRejected() {
		assertThatThrownBy(() -> comment("고아 답글", "no-such-comment"))
			.isInstanceOf(tsh.d94.app.server.apService.cira.exception.CiraException.class);
	}

}
