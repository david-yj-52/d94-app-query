package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tsh.d94.app.server.apService.cira.dto.IssueStatusResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionModel;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

	private static final String PROJECT_ID = "project-001";
	private static final String STATUS_TODO = "status-todo";
	private static final String STATUS_IN_PROGRESS = "status-in-progress";
	private static final String STATUS_IN_REVIEW = "status-in-review";
	private static final String STATUS_DONE = "status-done";
	private static final String STATUS_UNKNOWN = "status-unknown";
	@Mock
	private SnCiraIssueTransitionAccess transitionAccess;
	@Mock
	private SnCiraIssueStatusAccess statusAccess;
	@InjectMocks
	private WorkflowService workflowService;

	@Test
	@DisplayName("허용된 전이 - 성공")
	void validateTransition_allowed_success() {
		given(statusAccess.findByIdOptional(STATUS_IN_PROGRESS))
			.willReturn(Optional.of(statusModel(STATUS_IN_PROGRESS, "In Progress")));
		given(transitionAccess.findByProjectId(PROJECT_ID))
			.willReturn(List.of(
				transitionModel(PROJECT_ID, STATUS_TODO, STATUS_IN_PROGRESS, "Y")
			));

		workflowService.validateTransition(PROJECT_ID, STATUS_TODO, STATUS_IN_PROGRESS);
		// 예외 없이 통과하면 성공
	}

	@Test
	@DisplayName("허용되지 않은 전이 - ISSUE_INVALID_TRANSITION 에러")
	void validateTransition_notAllowed_throwsCiraException() {
		// from=DONE, to=TODO → toStatusId는 TODO이므로 TODO를 스텁
		given(statusAccess.findByIdOptional(STATUS_TODO))
			.willReturn(Optional.of(statusModel(STATUS_TODO, "To Do")));
		given(transitionAccess.findByProjectId(PROJECT_ID))
			.willReturn(List.of(
				transitionModel(PROJECT_ID, STATUS_TODO, STATUS_IN_PROGRESS, "Y")
				// Done → Todo 전이 없음
			));

		assertThatThrownBy(() -> workflowService.validateTransition(PROJECT_ID, STATUS_DONE, STATUS_TODO))
			.isInstanceOf(CiraException.class)
			.satisfies(ex -> assertThat(((CiraException)ex).getErrorCode())
				.isEqualTo(ErrorCode.ISSUE_INVALID_TRANSITION));
	}

	@Test
	@DisplayName("allowYn=N 전이 - ISSUE_INVALID_TRANSITION 에러")
	void validateTransition_allowYnNo_throwsCiraException() {
		given(statusAccess.findByIdOptional(STATUS_IN_PROGRESS))
			.willReturn(Optional.of(statusModel(STATUS_IN_PROGRESS, "In Progress")));
		given(transitionAccess.findByProjectId(PROJECT_ID))
			.willReturn(List.of(
				transitionModel(PROJECT_ID, STATUS_TODO, STATUS_IN_PROGRESS, "N")
			));

		assertThatThrownBy(() -> workflowService.validateTransition(PROJECT_ID, STATUS_TODO, STATUS_IN_PROGRESS))
			.isInstanceOf(CiraException.class)
			.satisfies(ex -> assertThat(((CiraException)ex).getErrorCode())
				.isEqualTo(ErrorCode.ISSUE_INVALID_TRANSITION));
	}

	@Test
	@DisplayName("존재하지 않는 상태 ID - ISSUE_STATUS_NOT_FOUND 에러")
	void validateTransition_unknownStatusId_throwsCiraException() {
		given(statusAccess.findByIdOptional(STATUS_UNKNOWN))
			.willReturn(Optional.empty());

		assertThatThrownBy(() -> workflowService.validateTransition(PROJECT_ID, STATUS_TODO, STATUS_UNKNOWN))
			.isInstanceOf(CiraException.class)
			.satisfies(ex -> assertThat(((CiraException)ex).getErrorCode())
				.isEqualTo(ErrorCode.ISSUE_STATUS_NOT_FOUND));
	}

	@Test
	@DisplayName("동일 상태로 전이 - 검증 없이 통과")
	void validateTransition_sameStatus_noValidation() {
		workflowService.validateTransition(PROJECT_ID, STATUS_TODO, STATUS_TODO);
		// transitionAccess 호출 없이 통과
	}

	@Test
	@DisplayName("getAvailableTransitions - 현재 상태에서 이동 가능한 상태 목록 반환")
	void getAvailableTransitions_returnsAllowedStatuses() {
		SnCiraIssueStatusModel inProgressStatus = statusModel(STATUS_IN_PROGRESS, "In Progress");
		SnCiraIssueStatusModel doneStatus = statusModel(STATUS_DONE, "Done");

		given(transitionAccess.findByProjectId(PROJECT_ID))
			.willReturn(List.of(
				transitionModel(PROJECT_ID, STATUS_TODO, STATUS_IN_PROGRESS, "Y"),
				transitionModel(PROJECT_ID, STATUS_TODO, STATUS_DONE, "Y"),
				transitionModel(PROJECT_ID, STATUS_IN_PROGRESS, STATUS_IN_REVIEW, "Y")
			));
		given(statusAccess.findByIdOptional(STATUS_IN_PROGRESS)).willReturn(Optional.of(inProgressStatus));
		given(statusAccess.findByIdOptional(STATUS_DONE)).willReturn(Optional.of(doneStatus));

		List<IssueStatusResponse> result = workflowService.getAvailableTransitions(PROJECT_ID, STATUS_TODO);

		assertThat(result).hasSize(2);
		assertThat(result).extracting(IssueStatusResponse::getStatusNm)
			.containsExactlyInAnyOrder("In Progress", "Done");
	}

	@Test
	@DisplayName("getAvailableTransitions - Done 상태는 전이 가능한 상태 없음")
	void getAvailableTransitions_doneStatus_emptyList() {
		given(transitionAccess.findByProjectId(PROJECT_ID))
			.willReturn(List.of(
				transitionModel(PROJECT_ID, STATUS_TODO, STATUS_IN_PROGRESS, "Y")
				// Done에서 나가는 전이 없음
			));

		List<IssueStatusResponse> result = workflowService.getAvailableTransitions(PROJECT_ID, STATUS_DONE);

		assertThat(result).isEmpty();
	}

	private SnCiraIssueTransitionModel transitionModel(String projectId, String fromStatusId, String toStatusId,
		String allowYn) {
		SnCiraIssueTransitionModel model = new SnCiraIssueTransitionModel();
		model.setProjectId(projectId);
		model.setFromStatusId(fromStatusId);
		model.setToStatusId(toStatusId);
		model.setAllowYn(allowYn);
		return model;
	}

	private SnCiraIssueStatusModel statusModel(String id, String statusNm) {
		SnCiraIssueStatusModel model = new SnCiraIssueStatusModel();
		model.setStatusNm(statusNm);
		return model;
	}
}
