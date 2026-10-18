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

import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberModel;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintAccess;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintModel;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

	private static final String USER_EMAIL = "user@test.com";
	private static final String USER_ID = "user-001";
	private static final String PROJECT_ID = "project-001";
	private static final String SPRINT_ID = "sprint-001";
	@Mock
	private SnCiraSprintAccess sprintAccess;
	@Mock
	private SnCiraProjectAccess projectAccess;
	@Mock
	private SnCiraProjectMemberAccess projectMemberAccess;
	@Mock
	private SnCiraIssueAccess issueAccess;
	@Mock
	private SnCiraIssueStatusAccess issueStatusAccess;
	@Mock
	private GsUserAccess userAccess;
	@Mock
	private IssueService issueService;
	@Mock
	private NotificationService notificationService;
	@InjectMocks
	private SprintService sprintService;

	@BeforeEach
	void setUpSecurityContext() {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(USER_EMAIL, null, List.of())
		);
	}

	// ─── startSprint ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("startSprint - Planned 스프린트를 Active로 전이 성공")
	void startSprint_planned_success() {
		SnCiraSprintModel sprint = sprintModel(SPRINT_ID, PROJECT_ID, "Planned");

		given(userAccess.findByEmail(USER_EMAIL)).willReturn(Optional.of(userModel(USER_ID)));
		given(sprintAccess.findByIdOptional(SPRINT_ID)).willReturn(Optional.of(sprint));
		given(projectMemberAccess.findAllByUserId(USER_ID)).willReturn(List.of(memberModel(USER_ID, PROJECT_ID)));
		given(sprintAccess.findByProjectIdAndSprintStat(PROJECT_ID, "Active")).willReturn(List.of());
		given(sprintAccess.save(any())).willReturn(sprint);
		given(projectMemberAccess.findAllByProjectId(PROJECT_ID)).willReturn(List.of());

		var result = sprintService.startSprint(SPRINT_ID);

		assertThat(sprint.getSprintStat()).isEqualTo("Active");
	}

	@Test
	@DisplayName("startSprint - Planned이 아닌 스프린트는 SPRINT_INVALID_TRANSITION 에러")
	void startSprint_notPlanned_throwsCiraException() {
		SnCiraSprintModel sprint = sprintModel(SPRINT_ID, PROJECT_ID, "Active");

		given(userAccess.findByEmail(USER_EMAIL)).willReturn(Optional.of(userModel(USER_ID)));
		given(sprintAccess.findByIdOptional(SPRINT_ID)).willReturn(Optional.of(sprint));
		given(projectMemberAccess.findAllByUserId(USER_ID)).willReturn(List.of(memberModel(USER_ID, PROJECT_ID)));

		assertThatThrownBy(() -> sprintService.startSprint(SPRINT_ID))
			.isInstanceOf(CiraException.class)
			.satisfies(ex -> assertThat(((CiraException)ex).getErrorCode())
				.isEqualTo(ErrorCode.SPRINT_INVALID_TRANSITION));
	}

	@Test
	@DisplayName("startSprint - 이미 Active 스프린트 존재 시 SPRINT_ALREADY_ACTIVE 에러")
	void startSprint_alreadyActive_throwsCiraException() {
		SnCiraSprintModel planned = sprintModel(SPRINT_ID, PROJECT_ID, "Planned");
		SnCiraSprintModel active = sprintModel("sprint-active", PROJECT_ID, "Active");
		active.setUseStatCd(UseStatCd.Usable);

		given(userAccess.findByEmail(USER_EMAIL)).willReturn(Optional.of(userModel(USER_ID)));
		given(sprintAccess.findByIdOptional(SPRINT_ID)).willReturn(Optional.of(planned));
		given(projectMemberAccess.findAllByUserId(USER_ID)).willReturn(List.of(memberModel(USER_ID, PROJECT_ID)));
		given(sprintAccess.findByProjectIdAndSprintStat(PROJECT_ID, "Active")).willReturn(List.of(active));

		assertThatThrownBy(() -> sprintService.startSprint(SPRINT_ID))
			.isInstanceOf(CiraException.class)
			.satisfies(ex -> assertThat(((CiraException)ex).getErrorCode())
				.isEqualTo(ErrorCode.SPRINT_ALREADY_ACTIVE));
	}

	@Test
	@DisplayName("getSprints - 삭제된 스프린트는 결과에서 제외")
	void getSprints_excludesDeletedSprints() {
		SnCiraSprintModel usable = sprintModel(SPRINT_ID, PROJECT_ID, "Planned");
		usable.setUseStatCd(UseStatCd.Usable);
		SnCiraSprintModel deleted = sprintModel("sprint-del", PROJECT_ID, "Planned");
		deleted.setUseStatCd(UseStatCd.Delete);

		given(projectAccess.findById(PROJECT_ID)).willReturn(projectModel(PROJECT_ID));
		given(sprintAccess.findByProjectId(PROJECT_ID)).willReturn(List.of(usable, deleted));

		var results = sprintService.getSprints(PROJECT_ID);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo(SPRINT_ID);
	}

	// ─── Helpers ───────────────────────────────────────────────────────────────

	private SnCiraSprintModel sprintModel(String id, String projectId, String stat) {
		return SnCiraSprintModel.builder()
			.objId(id)
			.projectId(projectId)
			.sprintNm("Sprint " + id)
			.sprintStat(stat)
			.useStatCd(UseStatCd.Usable)
			.build();
	}

	private SnCiraProjectMemberModel memberModel(String userId, String projectId) {
		return SnCiraProjectMemberModel.builder()
			.userId(userId)
			.projectId(projectId)
			.role("MEMBER")
			.useStatCd(UseStatCd.Usable)
			.build();
	}

	private GsUserModel userModel(String userId) {
		return GsUserModel.builder()
			.objId(userId)
			.email(USER_EMAIL)
			.build();
	}

	private SnCiraProjectModel projectModel(String projectId) {
		return SnCiraProjectModel.builder()
			.objId(projectId)
			.projectNm("Test Project")
			.projectKey("PROJ")
			.issueSequence(0)
			.build();
	}
}
