package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;
import tsh.d94.app.server.apService.cira.automation.events.IssueAssignedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueCreatedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueStatusChangedEvent;
import tsh.d94.app.server.apService.cira.dto.ChangeStatusRequest;
import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.UpdateIssueRequest;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardModel;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnModel;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueLog.SnCiraIssueLogAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueLog.SnCiraIssueLogModel;
import tsh.d94.app.server.data.orm.cira.ciraIssuePosition.SnCiraIssuePositionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssuePosition.SnCiraIssuePositionModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberModel;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/** IssueService BIZ 로직 (생성·수정·삭제·상태 변경·조회 권한). DB·트랜잭션은 IssueServiceIT 가 본다 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // 공통 stub(사용자·멤버십)을 setUp 에 모아 둔다
class IssueServiceTest {

	private static final String EMAIL = "me@test.d94";
	private static final String ME = "user-me";
	private static final String OTHER = "user-other";
	private static final String PROJECT_ID = "project-1";
	private static final String ISSUE_ID = "issue-1";
	private static final String TODO = "status-todo";
	private static final String DONE = "status-done";

	@Mock
	private SnCiraIssueAccess issueAccess;
	@Mock
	private SnCiraProjectAccess projectAccess;
	@Mock
	private GsUserAccess userAccess;
	@Mock
	private SnCiraCiraIssueTypeAccess issueTypeAccess;
	@Mock
	private SnCiraIssueStatusAccess statusAccess;
	@Mock
	private SnCiraIssueLogAccess issueLogAccess;
	@Mock
	private SnCiraIssuePositionAccess issuePositionAccess;
	@Mock
	private SnCiraProjectMemberAccess projectMemberAccess;
	@Mock
	private WorkflowService workflowService;
	@Mock
	private SnCiraBoardColumnAccess boardColumnAccess;
	@Mock
	private SnCiraBoardAccess boardAccess;
	@Mock
	private NotificationService notificationService;
	@Mock
	private ApplicationEventPublisher eventPublisher;
	@InjectMocks
	private IssueService issueService;

	private SnCiraProjectModel project;

	@BeforeEach
	void setUp() {
		TestContexts.login(EMAIL);
		GsUserModel me = new GsUserModel();
		org.springframework.test.util.ReflectionTestUtils.setField(me, "objId", ME);
		me.setEmail(EMAIL);
		given(userAccess.findByEmail(EMAIL)).willReturn(Optional.of(me));
		given(userAccess.findByIdOptional(any())).willReturn(Optional.empty());
		given(issueTypeAccess.findByIdOptional(any())).willReturn(Optional.empty());
		given(statusAccess.findByIdOptional(any())).willReturn(Optional.empty());
		memberAs("MEMBER");

		project = new SnCiraProjectModel();
		org.springframework.test.util.ReflectionTestUtils.setField(project, "objId", PROJECT_ID);
		project.setProjectKey("CIRA");
		project.setIssueSequence(14);
		given(projectAccess.findById(PROJECT_ID)).willReturn(project);
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private void memberAs(String role) {
		SnCiraProjectMemberModel m = new SnCiraProjectMemberModel();
		m.setProjectId(PROJECT_ID);
		m.setUserId(ME);
		m.setRole(role);
		given(projectMemberAccess.findAllByUserId(ME)).willReturn(List.of(m));
	}

	private void notMember() {
		given(projectMemberAccess.findAllByUserId(ME)).willReturn(List.of());
	}

	private SnCiraIssueModel existingIssue() {
		SnCiraIssueModel issue = new SnCiraIssueModel();
		org.springframework.test.util.ReflectionTestUtils.setField(issue, "objId", ISSUE_ID);
		issue.setProjectId(PROJECT_ID);
		issue.setIssueKey("CIRA-1");
		issue.setTitle("old title");
		issue.setContent("old content");
		issue.setStatusId(TODO);
		issue.setReporterId(OTHER);
		issue.setPriority("LOW");
		given(issueAccess.findById(ISSUE_ID)).willReturn(issue);
		return issue;
	}

	private static SnCiraIssueStatusModel status(String id, String nm) {
		SnCiraIssueStatusModel s = new SnCiraIssueStatusModel();
		org.springframework.test.util.ReflectionTestUtils.setField(s, "objId", id);
		s.setStatusNm(nm);
		return s;
	}

	private static ChangeStatusRequest changeTo(String statusId) {
		return new ObjectMapper().convertValue(java.util.Map.of("statusId", statusId), ChangeStatusRequest.class);
	}

	@Nested
	@DisplayName("createIssue")
	class Create {

		@BeforeEach
		void workflowAndTypes() {
			SnCiraIssueStatusModel todo = status(TODO, "To Do");
			todo.setSortOrd((short)1);
			todo.setUseStatCd(tsh.d94.lib.core.data.constant.UseStatCd.Usable);
			SnCiraIssueStatusModel done = status(DONE, "Done");
			done.setSortOrd((short)4);
			done.setUseStatCd(tsh.d94.lib.core.data.constant.UseStatCd.Usable);
			given(statusAccess.findByProjectId(PROJECT_ID)).willReturn(List.of(done, todo));
			tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeModel task =
				new tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeModel();
			org.springframework.test.util.ReflectionTestUtils.setField(task, "objId", "type-task");
			given(issueTypeAccess.findByTypeNm("Task")).willReturn(Optional.of(task));
		}

		@Test
		@DisplayName("[D3] 상태·유형을 안 보내면 첫 상태(sortOrd 최소)·기본 유형 Task 로 만든다")
		void defaultsStatusAndType() {
			CreateIssueRequest req = request();
			req.setStatusId(null);

			issueService.createIssue(PROJECT_ID, req);

			ArgumentCaptor<SnCiraIssueModel> saved = ArgumentCaptor.forClass(SnCiraIssueModel.class);
			then(issueAccess).should().save(saved.capture());
			assertThat(saved.getValue().getStatusId()).isEqualTo(TODO);
			assertThat(saved.getValue().getIssueTypeId()).isEqualTo("type-task");
		}

		@Test
		@DisplayName("다른 프로젝트의 상태 id 는 거절")
		void foreignStatus_rejected() {
			CreateIssueRequest req = request();
			req.setStatusId("status-of-other-project");

			assertThatThrownBy(() -> issueService.createIssue(PROJECT_ID, req))
				.isInstanceOf(IllegalArgumentException.class);
			then(issueAccess).should(never()).save(any());
		}

		@Test
		@DisplayName("프로젝트 멤버인 담당자는 저장된다")
		void memberAssignee_saved() {
			SnCiraProjectMemberModel other = new SnCiraProjectMemberModel();
			other.setUseStatCd(tsh.d94.lib.core.data.constant.UseStatCd.Usable);
			given(projectMemberAccess.findByProjectIdAndUserId(PROJECT_ID, OTHER)).willReturn(Optional.of(other));
			CreateIssueRequest req = request();
			req.setAssigneeId(OTHER);

			issueService.createIssue(PROJECT_ID, req);

			ArgumentCaptor<SnCiraIssueModel> saved = ArgumentCaptor.forClass(SnCiraIssueModel.class);
			then(issueAccess).should().save(saved.capture());
			assertThat(saved.getValue().getAssigneeId()).isEqualTo(OTHER);
		}

		private CreateIssueRequest request() {
			CreateIssueRequest req = new CreateIssueRequest();
			req.setTitle("새 이슈");
			req.setStatusId(TODO);
			return req;
		}

		@Test
		@DisplayName("시퀀스를 1 올려 키 {projectKey}-{seq} 를 만들고, 보고자는 로그인 사용자")
		void incrementsSequenceAndBuildsKey() {
			IssueResponse res = issueService.createIssue(PROJECT_ID, request());

			assertThat(project.getIssueSequence()).isEqualTo(15);
			then(projectAccess).should().save(project);
			assertThat(res.getIssueKey()).isEqualTo("CIRA-15");
			ArgumentCaptor<SnCiraIssueModel> saved = ArgumentCaptor.forClass(SnCiraIssueModel.class);
			then(issueAccess).should().save(saved.capture());
			assertThat(saved.getValue().getReporterId()).isEqualTo(ME);
			assertThat(saved.getValue().getTenant()).isEqualTo(TestContexts.TENANT);
		}

		@Test
		@DisplayName("생성 이력(Issue/Created)과 IssueCreatedEvent 를 남긴다")
		void recordsLogAndPublishesEvent() {
			issueService.createIssue(PROJECT_ID, request());

			ArgumentCaptor<SnCiraIssueLogModel> log = ArgumentCaptor.forClass(SnCiraIssueLogModel.class);
			then(issueLogAccess).should().save(log.capture());
			assertThat(log.getValue().getFieldNm()).isEqualTo("Issue");
			assertThat(log.getValue().getNewVal()).isEqualTo("Created");
			then(eventPublisher).should().publishEvent(any(IssueCreatedEvent.class));
		}

		@Test
		@DisplayName("상태에 매핑된 보드 컬럼이 있으면 맨 뒤 rank 로 위치를 만든다")
		void placesOnMappedColumn() {
			SnCiraBoardModel board = new SnCiraBoardModel();
			org.springframework.test.util.ReflectionTestUtils.setField(board, "objId", "board-1");
			given(boardAccess.findByProjectId(PROJECT_ID)).willReturn(List.of(board));
			SnCiraBoardColumnModel col = new SnCiraBoardColumnModel();
			org.springframework.test.util.ReflectionTestUtils.setField(col, "objId", "col-todo");
			col.setBoardId("board-1");
			given(boardColumnAccess.findByStatusId(TODO)).willReturn(List.of(col));
			SnCiraIssuePositionModel last = new SnCiraIssuePositionModel();
			last.setRankStr(LexoRankUtil.initial());
			given(issuePositionAccess.findByColumnIdOrderByRankStr("col-todo")).willReturn(List.of(last));

			issueService.createIssue(PROJECT_ID, request());

			ArgumentCaptor<SnCiraIssuePositionModel> pos = ArgumentCaptor.forClass(SnCiraIssuePositionModel.class);
			then(issuePositionAccess).should().save(pos.capture());
			assertThat(pos.getValue().getColumnId()).isEqualTo("col-todo");
			assertThat(pos.getValue().getRankStr()).isGreaterThan(LexoRankUtil.initial());
		}

		@Test
		@DisplayName("상태가 없으면 보드 위치를 만들지 않는다")
		void noStatus_noPosition() {
			CreateIssueRequest req = request();
			req.setStatusId(null);

			issueService.createIssue(PROJECT_ID, req);

			then(issuePositionAccess).should(never()).save(any());
		}

		@Test
		@DisplayName("멤버가 아니면 거절하고 시퀀스·이슈를 저장하지 않는다")
		void nonMember_rejected() {
			notMember();

			assertThatThrownBy(() -> issueService.createIssue(PROJECT_ID, request()))
				.isInstanceOf(IllegalArgumentException.class);
			then(projectAccess).should(never()).save(any());
			then(issueAccess).should(never()).save(any());
		}

		@Test
		@DisplayName("[D1] 존재하지 않는 담당자(예: 이메일)를 지정하면 거절한다")
		void unknownAssignee_rejected() {
			CreateIssueRequest req = request();
			req.setAssigneeId("someone@test.d94");

			assertThatThrownBy(() -> issueService.createIssue(PROJECT_ID, req))
				.isInstanceOfAny(IllegalArgumentException.class, CiraException.class);
		}

	}

	@Nested
	@DisplayName("updateIssue")
	class Update {

		@Test
		@DisplayName("바뀐 제목·내용만 이력을 남기고 반영한다")
		void titleAndContent_loggedWhenChanged() {
			SnCiraIssueModel issue = existingIssue();
			UpdateIssueRequest req = new UpdateIssueRequest();
			req.setTitle("new title");
			req.setContent("old content"); // 같음 → 이력 없음

			issueService.updateIssue(ISSUE_ID, req);

			assertThat(issue.getTitle()).isEqualTo("new title");
			ArgumentCaptor<SnCiraIssueLogModel> log = ArgumentCaptor.forClass(SnCiraIssueLogModel.class);
			then(issueLogAccess).should().save(log.capture());
			assertThat(log.getValue().getFieldNm()).isEqualTo("TITLE");
			then(issueAccess).should().save(issue);
		}

		@Test
		@DisplayName("담당자가 바뀌면 새 담당자에게 알림 + IssueAssignedEvent")
		void assigneeChange_notifiesAndPublishes() {
			existingIssue();
			UpdateIssueRequest req = new UpdateIssueRequest();
			req.setAssigneeId(OTHER);

			issueService.updateIssue(ISSUE_ID, req);

			then(notificationService).should().send(eq(OTHER), eq("ISSUE_ASSIGNED"), any(), any(), eq("ISSUE"),
				eq(ISSUE_ID));
			then(eventPublisher).should().publishEvent(any(IssueAssignedEvent.class));
		}

		@Test
		@DisplayName("담당자가 같으면 알림·이벤트 없음")
		void sameAssignee_noNotification() {
			SnCiraIssueModel issue = existingIssue();
			issue.setAssigneeId(OTHER);
			UpdateIssueRequest req = new UpdateIssueRequest();
			req.setAssigneeId(OTHER);

			issueService.updateIssue(ISSUE_ID, req);

			then(notificationService).shouldHaveNoInteractions();
			then(eventPublisher).shouldHaveNoInteractions();
		}

		@Test
		@DisplayName("[D17] 우선순위·스토리포인트·마감일·스프린트도 반영된다")
		void otherFields_applied() {
			SnCiraIssueModel issue = existingIssue();
			UpdateIssueRequest req = new UpdateIssueRequest();
			req.setPriority("HIGH");
			req.setStoryPnt(BigDecimal.valueOf(3));
			req.setDueDt(LocalDate.of(2026, 12, 31));
			req.setSprintId("sprint-1");

			issueService.updateIssue(ISSUE_ID, req);

			assertThat(issue.getPriority()).isEqualTo("HIGH");
			assertThat(issue.getStoryPnt()).isEqualByComparingTo("3");
			assertThat(issue.getDueDt()).isEqualTo(LocalDate.of(2026, 12, 31));
			assertThat(issue.getSprintId()).isEqualTo("sprint-1");
		}

		@Test
		@DisplayName("멤버가 아니면 거절")
		void nonMember_rejected() {
			existingIssue();
			notMember();

			assertThatThrownBy(() -> issueService.updateIssue(ISSUE_ID, new UpdateIssueRequest()))
				.isInstanceOf(IllegalArgumentException.class);
			then(issueAccess).should(never()).save(any());
		}

	}

	@Nested
	@DisplayName("deleteIssue")
	class Delete {

		@Test
		@DisplayName("ADMIN 은 soft delete (deletedAt 설정)")
		void admin_softDeletes() {
			memberAs("ADMIN");
			SnCiraIssueModel issue = existingIssue();

			issueService.deleteIssue(ISSUE_ID);

			assertThat(issue.getDeletedAt()).isNotNull();
			then(issueAccess).should().save(issue);
		}

		@Test
		@DisplayName("ADMIN 이 아니면 거절")
		void member_rejected() {
			existingIssue();

			assertThatThrownBy(() -> issueService.deleteIssue(ISSUE_ID))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Admin");
			then(issueAccess).should(never()).save(any());
		}

	}

	@Nested
	@DisplayName("changeStatus")
	class ChangeStatus {

		@BeforeEach
		void statuses() {
			given(statusAccess.findByIdOptional(TODO)).willReturn(Optional.of(status(TODO, "To Do")));
			given(statusAccess.findByIdOptional(DONE)).willReturn(Optional.of(status(DONE, "Done")));
		}

		@Test
		@DisplayName("전이를 검증하고 상태·이력·이벤트를 남긴다")
		void validTransition() {
			SnCiraIssueModel issue = existingIssue();

			issueService.changeStatus(ISSUE_ID, changeTo(DONE));

			then(workflowService).should().validateTransition(PROJECT_ID, TODO, DONE);
			assertThat(issue.getStatusId()).isEqualTo(DONE);
			ArgumentCaptor<SnCiraIssueLogModel> log = ArgumentCaptor.forClass(SnCiraIssueLogModel.class);
			then(issueLogAccess).should().save(log.capture());
			assertThat(log.getValue().getOldVal()).isEqualTo("To Do");
			assertThat(log.getValue().getNewVal()).isEqualTo("Done");
			then(eventPublisher).should().publishEvent(any(IssueStatusChangedEvent.class));
		}

		@Test
		@DisplayName("알림: 담당자·보고자에게 보내되 변경한 본인과 중복은 제외")
		void notifications() {
			SnCiraIssueModel issue = existingIssue();
			issue.setAssigneeId(ME); // 본인 → 제외, 보고자 OTHER → 받음

			issueService.changeStatus(ISSUE_ID, changeTo(DONE));

			then(notificationService).should().send(eq(OTHER), eq("ISSUE_STATUS_CHANGED"), any(), any(), any(), any());
			then(notificationService).should(never()).send(eq(ME), any(), any(), any(), any(), any());
		}

		@Test
		@DisplayName("허용되지 않은 전이는 WorkflowService 예외 그대로, 저장 없음")
		void invalidTransition_propagates() {
			existingIssue();
			willThrow(new CiraException(ErrorCode.ISSUE_INVALID_TRANSITION))
				.given(workflowService).validateTransition(PROJECT_ID, TODO, DONE);

			assertThatThrownBy(() -> issueService.changeStatus(ISSUE_ID, changeTo(DONE)))
				.isInstanceOf(CiraException.class);
			then(issueAccess).should(never()).save(any());
		}

		@Test
		@DisplayName("없는 상태·삭제된 이슈·비멤버는 CiraException")
		void rejections() {
			SnCiraIssueModel issue = existingIssue();
			assertThatThrownBy(() -> issueService.changeStatus(ISSUE_ID, changeTo("nope")))
				.isInstanceOf(CiraException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ISSUE_STATUS_NOT_FOUND);

			notMember();
			assertThatThrownBy(() -> issueService.changeStatus(ISSUE_ID, changeTo(DONE)))
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_NOT_MEMBER);

			issue.setDeletedAt(LocalDateTime.now());
			assertThatThrownBy(() -> issueService.changeStatus(ISSUE_ID, changeTo(DONE)))
				.extracting("errorCode").isEqualTo(ErrorCode.ISSUE_NOT_FOUND);
		}

		@Test
		@DisplayName("보드 위치를 같은 보드의 새 상태 컬럼으로 옮긴다")
		void movesBoardPosition() {
			existingIssue();
			SnCiraIssuePositionModel pos = new SnCiraIssuePositionModel();
			pos.setColumnId("col-todo");
			given(issuePositionAccess.findByIssueId(ISSUE_ID)).willReturn(List.of(pos));
			SnCiraBoardColumnModel todoCol = new SnCiraBoardColumnModel();
			todoCol.setBoardId("board-1");
			given(boardColumnAccess.findByIdOptional("col-todo")).willReturn(Optional.of(todoCol));
			SnCiraBoardColumnModel doneCol = new SnCiraBoardColumnModel();
			org.springframework.test.util.ReflectionTestUtils.setField(doneCol, "objId", "col-done");
			given(boardColumnAccess.findByBoardIdAndStatusId("board-1", DONE)).willReturn(Optional.of(doneCol));

			issueService.changeStatus(ISSUE_ID, changeTo(DONE));

			assertThat(pos.getColumnId()).isEqualTo("col-done");
			then(issuePositionAccess).should().save(pos);
		}

	}

	@Nested
	@DisplayName("getIssue")
	class Get {

		@Test
		@DisplayName("삭제된 이슈는 EntityNotFoundException")
		void deleted_notFound() {
			existingIssue().setDeletedAt(LocalDateTime.now());

			assertThatThrownBy(() -> issueService.getIssue(ISSUE_ID)).isInstanceOf(EntityNotFoundException.class);
		}

		@Test
		@DisplayName("[D18] 프로젝트 멤버가 아니면 이슈를 볼 수 없다")
		void nonMember_cannotRead() {
			existingIssue();
			notMember();

			assertThatThrownBy(() -> issueService.getIssue(ISSUE_ID))
				.isInstanceOfAny(IllegalArgumentException.class, CiraException.class);
		}

	}

}
