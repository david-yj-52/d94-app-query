package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import tsh.d94.app.server.apService.cira.dto.MoveIssueRequest;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardModel;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnModel;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssuePosition.SnCiraIssuePositionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssuePosition.SnCiraIssuePositionModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberModel;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/** BoardService.moveIssue 의 순서(rank) 계산·WIP 제한·컬럼 검증·상태 동기화 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardServiceTest {

	private static final String EMAIL = "me@test.d94";
	private static final String ME = "user-me";
	private static final String PROJECT_ID = "project-1";
	private static final String BOARD_ID = "board-1";
	private static final String COL_TODO = "col-todo";
	private static final String COL_DONE = "col-done";
	private static final String MOVING = "issue-moving";

	@Mock
	private SnCiraBoardAccess boardAccess;
	@Mock
	private SnCiraBoardColumnAccess boardColumnAccess;
	@Mock
	private SnCiraIssuePositionAccess issuePositionAccess;
	@Mock
	private SnCiraIssueAccess issueAccess;
	@Mock
	private SnCiraIssueStatusAccess statusAccess;
	@Mock
	private SnCiraCiraIssueTypeAccess issueTypeAccess;
	@Mock
	private GsUserAccess userAccess;
	@Mock
	private SnCiraProjectMemberAccess projectMemberAccess;
	@InjectMocks
	private BoardService boardService;

	/** COL_DONE 컬럼의 기존 위치들 (MOVING 제외) */
	private final List<SnCiraIssuePositionModel> donePositions = new ArrayList<>();
	private SnCiraBoardColumnModel doneColumn;
	private SnCiraIssueModel movingIssue;

	private static <T> T withId(T model, String id) {
		ReflectionTestUtils.setField(model, "objId", id);
		return model;
	}

	@BeforeEach
	void setUp() {
		TestContexts.login(EMAIL);
		given(userAccess.findByEmail(EMAIL)).willReturn(Optional.of(withId(new GsUserModel(), ME)));
		SnCiraProjectMemberModel member = new SnCiraProjectMemberModel();
		member.setProjectId(PROJECT_ID);
		given(projectMemberAccess.findAllByUserId(ME)).willReturn(List.of(member));

		SnCiraBoardModel board = withId(new SnCiraBoardModel(), BOARD_ID);
		board.setProjectId(PROJECT_ID);
		given(boardAccess.findById(BOARD_ID)).willReturn(board);

		doneColumn = withId(new SnCiraBoardColumnModel(), COL_DONE);
		doneColumn.setBoardId(BOARD_ID);
		doneColumn.setStatusId("status-done");
		SnCiraBoardColumnModel todoColumn = withId(new SnCiraBoardColumnModel(), COL_TODO);
		todoColumn.setBoardId(BOARD_ID);
		todoColumn.setStatusId("status-todo");
		given(boardColumnAccess.findById(COL_DONE)).willReturn(doneColumn);
		given(boardColumnAccess.findByBoardIdOrderBySortOrd(BOARD_ID)).willReturn(List.of(todoColumn, doneColumn));
		given(issuePositionAccess.findByColumnIdOrderByRankStr(COL_DONE)).willReturn(donePositions);

		movingIssue = withId(new SnCiraIssueModel(), MOVING);
		movingIssue.setStatusId("status-todo");
		given(issueAccess.findById(MOVING)).willReturn(movingIssue);
		given(statusAccess.findByIdOptional(any())).willReturn(Optional.empty());
		given(issueTypeAccess.findByIdOptional(any())).willReturn(Optional.empty());
		given(userAccess.findByIdOptional(any())).willReturn(Optional.empty());
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	/** COL_DONE 에 a, b, c 를 순서대로 놓는다 */
	private void doneHas(String... issueIds) {
		String rank = LexoRankUtil.initial();
		for (String id : issueIds) {
			SnCiraIssuePositionModel p = withId(new SnCiraIssuePositionModel(), "pos-" + id);
			p.setIssueId(id);
			p.setColumnId(COL_DONE);
			p.setRankStr(rank);
			p.setUseStatCd(UseStatCd.Usable);
			donePositions.add(p);
			rank = LexoRankUtil.after(rank);
		}
	}

	private String rankOf(String issueId) {
		return donePositions.stream().filter(p -> p.getIssueId().equals(issueId)).findFirst().orElseThrow()
			.getRankStr();
	}

	private String movedRank(String after, String before) {
		MoveIssueRequest req = new MoveIssueRequest();
		req.setTargetColumnId(COL_DONE);
		req.setAfterIssueId(after);
		req.setBeforeIssueId(before);
		boardService.moveIssue(BOARD_ID, MOVING, req);
		ArgumentCaptor<SnCiraIssuePositionModel> saved = ArgumentCaptor.forClass(SnCiraIssuePositionModel.class);
		then(issuePositionAccess).should(atLeastOnce()).save(saved.capture());
		return saved.getAllValues().stream().filter(p -> MOVING.equals(p.getIssueId())).reduce((a, b) -> b)
			.orElseThrow().getRankStr();
	}

	@Test
	@DisplayName("빈 컬럼으로 옮기면 initial rank")
	void emptyColumn_initialRank() {
		assertThat(movedRank(null, null)).isEqualTo(LexoRankUtil.initial());
	}

	@Test
	@DisplayName("위치 지정 없으면 맨 뒤")
	void noAnchor_appendsToEnd() {
		doneHas("a", "b");
		assertThat(movedRank(null, null)).isGreaterThan(rankOf("b"));
	}

	@Test
	@DisplayName("afterIssueId 만: 그 이슈와 다음 이슈 사이")
	void afterOnly_betweenNeighbours() {
		doneHas("a", "b", "c");
		assertThat(movedRank("a", null)).isGreaterThan(rankOf("a")).isLessThan(rankOf("b"));
	}

	@Test
	@DisplayName("beforeIssueId 만: 맨 앞이면 첫 이슈보다 앞")
	void beforeFirst_prepends() {
		doneHas("a", "b");
		assertThat(movedRank(null, "a")).isLessThan(rankOf("a"));
	}

	@Test
	@DisplayName("after·before 둘 다: 둘 사이")
	void bothAnchors_between() {
		doneHas("a", "b", "c");
		assertThat(movedRank("b", "c")).isGreaterThan(rankOf("b")).isLessThan(rankOf("c"));
	}

	@Test
	@DisplayName("모르는 afterIssueId 는 맨 뒤로")
	void unknownAnchor_appends() {
		doneHas("a", "b");
		assertThat(movedRank("ghost", null)).isGreaterThan(rankOf("b"));
	}

	@Test
	@DisplayName("다른 컬럼으로 옮기면 이슈 상태를 컬럼 상태로 바꿔 저장")
	void syncsStatusWithColumn() {
		movedRank(null, null);

		assertThat(movingIssue.getStatusId()).isEqualTo("status-done");
		then(issueAccess).should().save(movingIssue);
	}

	@Test
	@DisplayName("WIP 제한에 도달한 컬럼으로는 옮길 수 없다 (옮기는 이슈 자신은 세지 않음)")
	void wipLimit() {
		doneColumn.setWipLimit((short)2);
		doneHas("a", "b");

		assertThatThrownBy(() -> movedRank(null, null))
			.isInstanceOf(CiraException.class)
			.extracting("errorCode").isEqualTo(ErrorCode.BOARD_WIP_LIMIT_EXCEEDED);
	}

	@Test
	@DisplayName("WIP 제한: 이미 그 컬럼에 있는 이슈의 재정렬은 허용")
	void wipLimit_reorderWithinColumnAllowed() {
		doneColumn.setWipLimit((short)2);
		doneHas("a", MOVING);

		assertThatCode(() -> movedRank(null, "a")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("다른 보드의 컬럼이면 BOARD_COLUMN_NOT_FOUND")
	void columnOfOtherBoard_rejected() {
		doneColumn.setBoardId("other-board");

		assertThatThrownBy(() -> movedRank(null, null))
			.extracting("errorCode").isEqualTo(ErrorCode.BOARD_COLUMN_NOT_FOUND);
	}

	@Test
	@DisplayName("프로젝트 멤버가 아니면 PROJECT_NOT_MEMBER")
	void nonMember_rejected() {
		given(projectMemberAccess.findAllByUserId(ME)).willReturn(List.of());

		assertThatThrownBy(() -> movedRank(null, null))
			.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_NOT_MEMBER);
		then(issuePositionAccess).should(never()).save(any());
	}

	@Test
	@DisplayName("[D22] 상태가 매핑되지 않은 컬럼으로 옮겨도 500(NPE)이 나지 않는다")
	void columnWithoutStatus_noNpe() {
		doneColumn.setStatusId(null);

		assertThatCode(() -> movedRank(null, null)).doesNotThrowAnyException();
	}

}
