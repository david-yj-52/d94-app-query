package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import tsh.d94.app.server.apService.cira.dto.UpdateCommentRequest;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraComment.SnCiraCommentAccess;
import tsh.d94.app.server.data.orm.cira.ciraComment.SnCiraCommentModel;
import tsh.d94.app.server.data.orm.cira.ciraCommentReaction.SnCiraCommentReactionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

	private static final String USER_EMAIL = "user@test.com";
	private static final String USER_ID = "user-001";
	private static final String ISSUE_ID = "issue-001";
	private static final String COMMENT_ID = "comment-001";
	private static final String ISSUE_KEY = "PROJ-1";
	@Mock
	private SnCiraCommentAccess commentAccess;
	@Mock
	private SnCiraIssueAccess issueAccess;
	@Mock
	private GsUserAccess userAccess;
	@Mock
	private SnCiraCommentReactionAccess commentReactionAccess;
	@Mock
	private NotificationService notificationService;
	@InjectMocks
	private CommentService commentService;

	@BeforeEach
	void setUpSecurityContext() {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(USER_EMAIL, null, List.of())
		);
	}

	// ─── getComments ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("getComments - 삭제된 이슈는 ISSUE_NOT_FOUND 에러")
	void getComments_deletedIssue_throwsCiraException() {
		SnCiraIssueModel deletedIssue = issueModel(ISSUE_ID, ISSUE_KEY);
		deletedIssue.setDeletedAt(java.time.LocalDateTime.now());

		given(userAccess.findByEmail(USER_EMAIL)).willReturn(Optional.of(userModel(USER_ID)));
		given(issueAccess.findById(ISSUE_ID)).willReturn(deletedIssue);

		assertThatThrownBy(() -> commentService.getComments(ISSUE_ID))
			.isInstanceOf(CiraException.class)
			.satisfies(ex -> assertThat(((CiraException)ex).getErrorCode())
				.isEqualTo(ErrorCode.ISSUE_NOT_FOUND));
	}

	@Test
	@DisplayName("getComments - 최상위 댓글만 반환, 삭제된 댓글 제외")
	void getComments_returnsOnlyRootActiveComments() {
		SnCiraIssueModel issue = issueModel(ISSUE_ID, ISSUE_KEY);

		SnCiraCommentModel root = commentModel(COMMENT_ID, ISSUE_ID, USER_ID, null, UseStatCd.Usable);
		SnCiraCommentModel deleted = commentModel("comment-del", ISSUE_ID, USER_ID, null, UseStatCd.Delete);

		given(userAccess.findByEmail(USER_EMAIL)).willReturn(Optional.of(userModel(USER_ID)));
		given(issueAccess.findById(ISSUE_ID)).willReturn(issue);
		given(commentAccess.findByIssueId(ISSUE_ID)).willReturn(List.of(root, deleted));
		given(commentReactionAccess.findByCommentId(any())).willReturn(List.of());
		given(userAccess.findByIdOptional(USER_ID)).willReturn(Optional.of(userModel(USER_ID)));

		var results = commentService.getComments(ISSUE_ID);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(COMMENT_ID);
	}

	// ─── updateComment ─────────────────────────────────────────────────────────

	@Test
	@DisplayName("updateComment - 작성자가 아닌 경우 COMMENT_NOT_AUTHOR 에러")
	void updateComment_notAuthor_throwsCiraException() {
		SnCiraCommentModel comment = commentModel(COMMENT_ID, ISSUE_ID, "other-user", null, UseStatCd.Usable);

		given(userAccess.findByEmail(USER_EMAIL)).willReturn(Optional.of(userModel(USER_ID)));
		given(commentAccess.findById(COMMENT_ID)).willReturn(comment);

		assertThatThrownBy(() -> commentService.updateComment(COMMENT_ID, new UpdateCommentRequest("수정 내용")))
			.isInstanceOf(CiraException.class)
			.satisfies(ex -> assertThat(((CiraException)ex).getErrorCode())
				.isEqualTo(ErrorCode.COMMENT_NOT_AUTHOR));

		then(commentAccess).should(never()).save(any());
	}

	@Test
	@DisplayName("updateComment - 삭제된 댓글은 COMMENT_NOT_FOUND 에러")
	void updateComment_deletedComment_throwsCiraException() {
		SnCiraCommentModel deleted = commentModel(COMMENT_ID, ISSUE_ID, USER_ID, null, UseStatCd.Delete);

		given(userAccess.findByEmail(USER_EMAIL)).willReturn(Optional.of(userModel(USER_ID)));
		given(commentAccess.findById(COMMENT_ID)).willReturn(deleted);

		assertThatThrownBy(() -> commentService.updateComment(COMMENT_ID, new UpdateCommentRequest("수정")))
			.isInstanceOf(CiraException.class)
			.satisfies(ex -> assertThat(((CiraException)ex).getErrorCode())
				.isEqualTo(ErrorCode.COMMENT_NOT_FOUND));
	}

	// ─── deleteComment ─────────────────────────────────────────────────────────

	@Test
	@DisplayName("deleteComment - 작성자가 아닌 경우 COMMENT_NOT_AUTHOR 에러")
	void deleteComment_notAuthor_throwsCiraException() {
		SnCiraCommentModel comment = commentModel(COMMENT_ID, ISSUE_ID, "other-user", null, UseStatCd.Usable);

		given(userAccess.findByEmail(USER_EMAIL)).willReturn(Optional.of(userModel(USER_ID)));
		given(commentAccess.findById(COMMENT_ID)).willReturn(comment);

		assertThatThrownBy(() -> commentService.deleteComment(COMMENT_ID))
			.isInstanceOf(CiraException.class)
			.satisfies(ex -> assertThat(((CiraException)ex).getErrorCode())
				.isEqualTo(ErrorCode.COMMENT_NOT_AUTHOR));

		then(commentAccess).should(never()).save(any());
	}

	// ─── Helpers ───────────────────────────────────────────────────────────────

	private SnCiraIssueModel issueModel(String id, String issueKey) {
		return SnCiraIssueModel.builder()
			.objId(id)
			.issueKey(issueKey)
			.reporterId(USER_ID)
			.build();
	}

	private SnCiraCommentModel commentModel(String id, String issueId, String authorId, String parentId,
		UseStatCd statCd) {
		return SnCiraCommentModel.builder()
			.objId(id)
			.issueId(issueId)
			.authorId(authorId)
			.parentId(parentId)
			.content("테스트 댓글")
			.useStatCd(statCd)
			.build();
	}

	private GsUserModel userModel(String userId) {
		return GsUserModel.builder()
			.objId(userId)
			.email(USER_EMAIL)
			.build();
	}
}
