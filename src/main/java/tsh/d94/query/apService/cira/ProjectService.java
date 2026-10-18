package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.AddMemberRequest;
import tsh.d94.app.server.apService.cira.dto.AddStatusRequest;
import tsh.d94.app.server.apService.cira.dto.CreateProjectRequest;
import tsh.d94.app.server.apService.cira.dto.ProjectMemberResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.dto.UpdateMemberRoleRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateProjectRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateTransitionsRequest;
import tsh.d94.app.server.apService.cira.dto.WorkflowStatusResponse;
import tsh.d94.app.server.apService.cira.dto.WorkflowTransitionResponse;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardModel;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionModel;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProjectService {

	private final SnCiraProjectAccess projectAccess;
	private final SnCiraProjectMemberAccess projectMemberAccess;
	private final GsUserAccess userAccess;
	private final SnCiraIssueStatusAccess issueStatusAccess;
	private final SnCiraIssueTransitionAccess issueTransitionAccess;
	private final SnCiraBoardAccess boardAccess;
	private final SnCiraBoardColumnAccess boardColumnAccess;

	@Transactional
	public ProjectResponse createProject(CreateProjectRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel owner = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		if (projectAccess.findByProjectKey(request.getKey()).isPresent()) {
			throw new IllegalArgumentException("Project key already exists: " + request.getKey());
		}

		SnCiraProjectModel project = SnCiraProjectModel.builder()
			.projectKey(request.getKey())
			.projectNm(request.getName())
			.descr(request.getDescription())
			.projectType(request.getProjectType())
			.ownerId(owner.getObjId())
			.issueSequence(0)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("INIT-PROJ")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateProject")
			.prevEvntNm("None")
			.build();

		projectAccess.save(project);

		// Add owner as Admin member
		SnCiraProjectMemberModel member = SnCiraProjectMemberModel.builder()
			.projectId(project.getObjId())
			.userId(owner.getObjId())
			.role("ADMIN")
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("INIT-PROJ")
			.useStatCd(UseStatCd.Usable)
			.evtNm("AddOwner")
			.prevEvntNm("None")
			.build();

		projectMemberAccess.save(member);

		initDefaultWorkflow(project.getObjId(), request.getProjectType());

		return mapToResponse(project);
	}

	private void initDefaultWorkflow(String projectId, String projectType) {
		SnCiraIssueStatusModel todo = createStatus(projectId, "To Do", "TODO", "#DFE1E6", (short)1);
		SnCiraIssueStatusModel inProgress = createStatus(projectId, "In Progress", "IN_PROGRESS", "#0052CC", (short)2);
		SnCiraIssueStatusModel inReview = createStatus(projectId, "In Review", "IN_PROGRESS", "#FF991F", (short)3);
		SnCiraIssueStatusModel done = createStatus(projectId, "Done", "DONE", "#00875A", (short)4);

		createTransition(projectId, todo.getObjId(), inProgress.getObjId());
		createTransition(projectId, todo.getObjId(), done.getObjId());
		createTransition(projectId, inProgress.getObjId(), inReview.getObjId());
		createTransition(projectId, inProgress.getObjId(), todo.getObjId());
		createTransition(projectId, inReview.getObjId(), done.getObjId());
		createTransition(projectId, inReview.getObjId(), inProgress.getObjId());

		SnCiraBoardModel board = SnCiraBoardModel.builder()
			.projectId(projectId)
			.boardNm("Default Board")
			.boardType(projectType != null ? projectType : "KANBAN")
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("INIT-PROJ")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateDefaultBoard")
			.prevEvntNm("None")
			.build();
		boardAccess.save(board);

		createBoardColumn(board.getObjId(), todo.getObjId(), "To Do", (short)1);
		createBoardColumn(board.getObjId(), inProgress.getObjId(), "In Progress", (short)2);
		createBoardColumn(board.getObjId(), inReview.getObjId(), "In Review", (short)3);
		createBoardColumn(board.getObjId(), done.getObjId(), "Done", (short)4);
	}

	private SnCiraIssueStatusModel createStatus(String projectId, String statusNm, String category, String colorCd,
		short sortOrd) {
		SnCiraIssueStatusModel status = SnCiraIssueStatusModel.builder()
			.projectId(projectId)
			.statusNm(statusNm)
			.category(category)
			.colorCd(colorCd)
			.sortOrd(sortOrd)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("INIT-PROJ")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateStatus")
			.prevEvntNm("None")
			.build();
		issueStatusAccess.save(status);
		return status;
	}

	private void createTransition(String projectId, String fromStatusId, String toStatusId) {
		SnCiraIssueTransitionModel transition = SnCiraIssueTransitionModel.builder()
			.projectId(projectId)
			.fromStatusId(fromStatusId)
			.toStatusId(toStatusId)
			.allowYn("Y")
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("INIT-PROJ")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateTransition")
			.prevEvntNm("None")
			.build();
		issueTransitionAccess.save(transition);
	}

	private void createBoardColumn(String boardId, String statusId, String columnNm, short sortOrd) {
		SnCiraBoardColumnModel column = SnCiraBoardColumnModel.builder()
			.boardId(boardId)
			.statusId(statusId)
			.columnNm(columnNm)
			.sortOrd(sortOrd)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("INIT-PROJ")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateBoardColumn")
			.prevEvntNm("None")
			.build();
		boardColumnAccess.save(column);
	}

	public List<ProjectResponse> getMyProjects() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		// Find projects where user is a member
		List<SnCiraProjectMemberModel> memberships = projectMemberAccess.findAllByUserId(user.getObjId());
		List<String> projectIds = memberships.stream()
			.map(SnCiraProjectMemberModel::getProjectId)
			.collect(Collectors.toList());

		return projectAccess.findAllById(projectIds).stream()
			.filter(p -> p.getDeletedAt() == null)
			.map(this::mapToResponse)
			.collect(Collectors.toList());
	}

	public ProjectResponse getProject(String projectId) {
		SnCiraProjectModel project = projectAccess.findById(projectId);

		if (project.getDeletedAt() != null) {
			throw new EntityNotFoundException("Project not found: " + projectId);
		}

		return mapToResponse(project);
	}

	@Transactional
	public ProjectResponse updateProject(String projectId, UpdateProjectRequest request) {
		validateAdminAccess(projectId);
		SnCiraProjectModel project = projectAccess.findById(projectId);
		if (project.getDeletedAt() != null) {
			throw new EntityNotFoundException("Project not found: " + projectId);
		}
		if (request.getName() != null) {
			project.setProjectNm(request.getName());
		}
		if (request.getDescription() != null) {
			project.setDescr(request.getDescription());
		}
		if (request.getProjectType() != null) {
			project.setProjectType(request.getProjectType());
		}
		project.setEvtNm("UpdateProject");
		projectAccess.save(project);
		return mapToResponse(project);
	}

	public List<ProjectMemberResponse> getMembers(String projectId) {
		List<SnCiraProjectMemberModel> members = projectMemberAccess.findAllByProjectId(projectId)
			.stream()
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.collect(Collectors.toList());

		List<String> userIds = members.stream()
			.map(SnCiraProjectMemberModel::getUserId)
			.collect(Collectors.toList());

		Map<String, GsUserModel> userMap = userAccess.findAllById(userIds)
			.stream()
			.collect(Collectors.toMap(GsUserModel::getObjId, u -> u));

		return members.stream()
			.map(m -> {
				GsUserModel user = userMap.get(m.getUserId());
				return ProjectMemberResponse.builder()
					.userId(m.getUserId())
					.email(user != null ? user.getEmail() : null)
					.userNm(user != null ? user.getUserNm() : null)
					.avatarUrl(user != null ? user.getAvatarUrl() : null)
					.role(m.getRole())
					.build();
			})
			.collect(Collectors.toList());
	}

	@Transactional
	public ProjectMemberResponse addMember(String projectId, AddMemberRequest request) {
		validateAdminAccess(projectId);

		GsUserModel targetUser = userAccess.findByEmail(request.getEmail())
			.orElseThrow(() -> new EntityNotFoundException("USER_NOT_FOUND: " + request.getEmail()));

		projectMemberAccess.findByProjectIdAndUserId(projectId, targetUser.getObjId())
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.ifPresent(m -> {
				throw new IllegalStateException("PROJECT_MEMBER_ALREADY_EXISTS");
			});

		String role = (request.getRole() != null) ? request.getRole() : "DEVELOPER";
		SnCiraProjectMemberModel member = SnCiraProjectMemberModel.builder()
			.projectId(projectId)
			.userId(targetUser.getObjId())
			.role(role)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("PROJ-MEMBER-ADD")
			.useStatCd(UseStatCd.Usable)
			.evtNm("AddMember")
			.prevEvntNm("None")
			.build();
		projectMemberAccess.save(member);

		return ProjectMemberResponse.builder()
			.userId(targetUser.getObjId())
			.email(targetUser.getEmail())
			.userNm(targetUser.getUserNm())
			.avatarUrl(targetUser.getAvatarUrl())
			.role(role)
			.build();
	}

	@Transactional
	public ProjectMemberResponse updateMemberRole(String projectId, String userId, UpdateMemberRoleRequest request) {
		validateAdminAccess(projectId);

		SnCiraProjectMemberModel member = projectMemberAccess.findByProjectIdAndUserId(projectId, userId)
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.orElseThrow(() -> new EntityNotFoundException("Member not found: " + userId));

		member.setRole(request.getRole());
		member.setEvtNm("UpdateMemberRole");
		projectMemberAccess.save(member);

		GsUserModel user = userAccess.findByIdOptional(userId)
			.orElse(null);

		return ProjectMemberResponse.builder()
			.userId(userId)
			.email(user != null ? user.getEmail() : null)
			.userNm(user != null ? user.getUserNm() : null)
			.avatarUrl(user != null ? user.getAvatarUrl() : null)
			.role(member.getRole())
			.build();
	}

	@Transactional
	public void removeMember(String projectId, String userId) {
		validateAdminAccess(projectId);

		SnCiraProjectMemberModel member = projectMemberAccess.findByProjectIdAndUserId(projectId, userId)
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.orElseThrow(() -> new EntityNotFoundException("Member not found: " + userId));

		// 마지막 ADMIN 보호
		long adminCount = projectMemberAccess.findAllByProjectId(projectId)
			.stream()
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.filter(m -> "ADMIN".equals(m.getRole()))
			.count();

		if ("ADMIN".equals(member.getRole()) && adminCount <= 1) {
			throw new IllegalStateException("Cannot remove the last ADMIN of the project");
		}

		projectMemberAccess.delete(member.getObjId());
	}

	public List<WorkflowStatusResponse> getWorkflowStatuses(String projectId) {
		return issueStatusAccess.findByProjectId(projectId)
			.stream()
			.filter(s -> UseStatCd.Usable.equals(s.getUseStatCd()))
			.map(this::mapToStatusResponse)
			.collect(Collectors.toList());
	}

	@Transactional
	public WorkflowStatusResponse addWorkflowStatus(String projectId, AddStatusRequest request) {
		validateAdminAccess(projectId);

		issueStatusAccess.findByProjectIdAndStatusNm(projectId, request.getStatusNm())
			.filter(s -> UseStatCd.Usable.equals(s.getUseStatCd()))
			.ifPresent(s -> {
				throw new IllegalStateException("Status already exists: " + request.getStatusNm());
			});

		SnCiraIssueStatusModel status = SnCiraIssueStatusModel.builder()
			.projectId(projectId)
			.statusNm(request.getStatusNm())
			.category(request.getCategory())
			.colorCd(request.getColorCd())
			.sortOrd(request.getSortOrd() != null ? request.getSortOrd() : (short)99)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("PROJ-STATUS-ADD")
			.useStatCd(UseStatCd.Usable)
			.evtNm("AddStatus")
			.prevEvntNm("None")
			.build();
		issueStatusAccess.save(status);

		return mapToStatusResponse(status);
	}

	@Transactional
	public void deleteWorkflowStatus(String projectId, String statusId) {
		validateAdminAccess(projectId);

		SnCiraIssueStatusModel status = issueStatusAccess.findById(statusId);
		if (!projectId.equals(status.getProjectId())) {
			throw new EntityNotFoundException("Status not found in project: " + statusId);
		}

		// 해당 상태가 관련된 전이 규칙도 함께 삭제
		issueTransitionAccess.findByProjectId(projectId)
			.stream()
			.filter(t -> UseStatCd.Usable.equals(t.getUseStatCd()))
			.filter(t -> statusId.equals(t.getFromStatusId()) || statusId.equals(t.getToStatusId()))
			.forEach(t -> issueTransitionAccess.delete(t.getObjId()));

		issueStatusAccess.delete(statusId);
	}

	public List<WorkflowTransitionResponse> getWorkflowTransitions(String projectId) {
		return issueTransitionAccess.findByProjectId(projectId)
			.stream()
			.filter(t -> UseStatCd.Usable.equals(t.getUseStatCd()))
			.map(this::mapToTransitionResponse)
			.collect(Collectors.toList());
	}

	@Transactional
	public List<WorkflowTransitionResponse> updateWorkflowTransitions(String projectId,
		UpdateTransitionsRequest request) {
		validateAdminAccess(projectId);

		// 기존 전이 규칙 전체 삭제 (soft delete)
		issueTransitionAccess.findByProjectId(projectId)
			.stream()
			.filter(t -> UseStatCd.Usable.equals(t.getUseStatCd()))
			.forEach(t -> issueTransitionAccess.delete(t.getObjId()));

		// 새 전이 규칙 생성
		return request.getTransitions()
			.stream()
			.map(item -> {
				SnCiraIssueTransitionModel transition = SnCiraIssueTransitionModel.builder()
					.projectId(projectId)
					.fromStatusId(item.getFromStatusId())
					.toStatusId(item.getToStatusId())
					.allowYn("Y")
					.srvId(ApplicationProperties.getApplicationServiceName())
					.tenant(ApplicationProperties.getApplicationTenant())
					.traceId("PROJ-TRANS-UPDATE")
					.useStatCd(UseStatCd.Usable)
					.evtNm("UpdateTransition")
					.prevEvntNm("None")
					.build();
				issueTransitionAccess.save(transition);
				return mapToTransitionResponse(transition);
			})
			.collect(Collectors.toList());
	}

	private void validateAdminAccess(String projectId) {
		GsUserModel user = getCurrentUser();
		SnCiraProjectMemberModel member = projectMemberAccess
			.findByProjectIdAndUserId(projectId, user.getObjId())
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.orElseThrow(() -> new SecurityException("Access denied: not a project member"));
		if (!"ADMIN".equals(member.getRole())) {
			throw new SecurityException("Access denied: ADMIN role required");
		}
	}

	private GsUserModel getCurrentUser() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
	}

	private WorkflowStatusResponse mapToStatusResponse(SnCiraIssueStatusModel model) {
		return WorkflowStatusResponse.builder()
			.id(model.getObjId())
			.statusNm(model.getStatusNm())
			.category(model.getCategory())
			.colorCd(model.getColorCd())
			.sortOrd(model.getSortOrd())
			.build();
	}

	private WorkflowTransitionResponse mapToTransitionResponse(SnCiraIssueTransitionModel model) {
		return WorkflowTransitionResponse.builder()
			.id(model.getObjId())
			.fromStatusId(model.getFromStatusId())
			.toStatusId(model.getToStatusId())
			.allowYn(model.getAllowYn())
			.build();
	}

	private ProjectResponse mapToResponse(SnCiraProjectModel model) {
		return ProjectResponse.builder()
			.id(model.getObjId())
			.key(model.getProjectKey())
			.name(model.getProjectNm())
			.description(model.getDescr())
			.projectType(model.getProjectType())
			.ownerId(model.getOwnerId())
			.issueSequence(model.getIssueSequence())
			.build();
	}
}
