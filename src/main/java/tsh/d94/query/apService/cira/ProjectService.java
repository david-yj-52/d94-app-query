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
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionModel;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberModel;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
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
	private final CiraFieldService fieldService;
	private final SpaceService spaceService;
	private final SpaceGuard spaceGuard;

	@Transactional
	public ProjectResponse createProject(CreateProjectRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel owner = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		if (projectAccess.findByProjectKey(request.getKey()).isPresent()) {
			throw new IllegalArgumentException("Project key already exists: " + request.getKey());
		}

		var type = fieldService.resolveProjectType(request.getProjectTypeId(), request.getProjectType());
		SnCiraProjectModel project = SnCiraProjectModel.builder()
			.projectKey(request.getKey())
			.projectNm(request.getName())
			.descr(request.getDescription())
			.projectType(type.typeNm())
			.projectTypeId(type.id())
			.ownerId(owner.getObjId())
			.issueSequence(0)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(TenantContext.get())
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
			.tenant(TenantContext.get())
			.traceId("INIT-PROJ")
			.useStatCd(UseStatCd.Usable)
			.evtNm("AddOwner")
			.prevEvntNm("None")
			.build();

		projectMemberAccess.save(member);

		// 타입 강제 대신: 고른 타입은 기본 작업 공간의 스타터 프리셋 (V650)
		spaceService.createDefault(project.getObjId(), request.getDefaultSpaceName(), type.id(), owner.getObjId());

		return mapToResponse(project);
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
		if (request.getProjectTypeId() != null || request.getProjectType() != null) {
			var type = fieldService.resolveProjectType(request.getProjectTypeId(), request.getProjectType());
			project.setProjectTypeId(type.id());
			project.setProjectType(type.typeNm());
		}
		project.setEvtNm("UpdateProject");
		projectAccess.save(project);
		return mapToResponse(project);
	}

	@Transactional
	public void deleteProject(String projectId) {
		validateAdminAccess(projectId);
		SnCiraProjectModel project = projectAccess.findById(projectId);
		if (project.getDeletedAt() != null) {
			throw new EntityNotFoundException("Project not found: " + projectId);
		}
		project.setDeletedAt(java.time.LocalDateTime.now());
		project.setEvtNm("DeleteProject");
		projectAccess.save(project);
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
			.tenant(TenantContext.get())
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

	// ── 워크플로 (V650: 공간 소유. spaceId null = 기본 공간, 수정은 프로젝트 ADMIN 또는 공간 관리자) ──

	public List<WorkflowStatusResponse> getWorkflowStatuses(String projectId) {
		return getWorkflowStatuses(projectId, null);
	}

	public List<WorkflowStatusResponse> getWorkflowStatuses(String projectId, String spaceId) {
		return issueStatusAccess.findBySpaceId(spaceGuard.resolve(projectId, spaceId).getObjId())
			.stream()
			.filter(s -> UseStatCd.Usable.equals(s.getUseStatCd()))
			.map(this::mapToStatusResponse)
			.collect(Collectors.toList());
	}

	@Transactional
	public WorkflowStatusResponse addWorkflowStatus(String projectId, AddStatusRequest request) {
		return addWorkflowStatus(projectId, null, request);
	}

	@Transactional
	public WorkflowStatusResponse addWorkflowStatus(String projectId, String spaceId, AddStatusRequest request) {
		String sid = spaceGuard.requireManager(spaceGuard.resolve(projectId, spaceId)).getObjId();

		issueStatusAccess.findBySpaceIdAndStatusNm(sid, request.getStatusNm())
			.filter(s -> UseStatCd.Usable.equals(s.getUseStatCd()))
			.ifPresent(s -> {
				throw new IllegalStateException("Status already exists: " + request.getStatusNm());
			});

		SnCiraIssueStatusModel status = SnCiraIssueStatusModel.builder()
			.projectId(projectId)
			.spaceId(sid)
			.statusNm(request.getStatusNm())
			.category(request.getCategory())
			.colorCd(request.getColorCd())
			.sortOrd(request.getSortOrd() != null ? request.getSortOrd() : (short)99)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(TenantContext.get())
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
		SnCiraIssueStatusModel status = issueStatusAccess.findById(statusId);
		if (!projectId.equals(status.getProjectId())) {
			throw new EntityNotFoundException("Status not found in project: " + statusId);
		}
		spaceGuard.requireManager(spaceGuard.resolve(projectId, status.getSpaceId()));

		// 해당 상태가 관련된 전이 규칙도 함께 삭제
		issueTransitionAccess.findBySpaceId(status.getSpaceId())
			.stream()
			.filter(t -> UseStatCd.Usable.equals(t.getUseStatCd()))
			.filter(t -> statusId.equals(t.getFromStatusId()) || statusId.equals(t.getToStatusId()))
			.forEach(t -> issueTransitionAccess.delete(t.getObjId()));

		issueStatusAccess.delete(statusId);
	}

	public List<WorkflowTransitionResponse> getWorkflowTransitions(String projectId) {
		return getWorkflowTransitions(projectId, null);
	}

	public List<WorkflowTransitionResponse> getWorkflowTransitions(String projectId, String spaceId) {
		return issueTransitionAccess.findBySpaceId(spaceGuard.resolve(projectId, spaceId).getObjId())
			.stream()
			.filter(t -> UseStatCd.Usable.equals(t.getUseStatCd()))
			.map(this::mapToTransitionResponse)
			.collect(Collectors.toList());
	}

	@Transactional
	public List<WorkflowTransitionResponse> updateWorkflowTransitions(String projectId,
		UpdateTransitionsRequest request) {
		return updateWorkflowTransitions(projectId, null, request);
	}

	/** 공간 전이 규칙 전체 교체. 그 공간의 상태끼리만 */
	@Transactional
	public List<WorkflowTransitionResponse> updateWorkflowTransitions(String projectId, String spaceId,
		UpdateTransitionsRequest request) {
		String sid = spaceGuard.requireManager(spaceGuard.resolve(projectId, spaceId)).getObjId();
		java.util.Set<String> own = issueStatusAccess.findBySpaceId(sid).stream()
			.filter(s -> UseStatCd.Usable.equals(s.getUseStatCd()))
			.map(SnCiraIssueStatusModel::getObjId)
			.collect(Collectors.toSet());
		request.getTransitions().forEach(item -> {
			if (!own.contains(item.getFromStatusId()) || !own.contains(item.getToStatusId())) {
				throw new IllegalArgumentException("이 작업 공간의 상태가 아닙니다");
			}
		});

		issueTransitionAccess.findBySpaceId(sid)
			.stream()
			.filter(t -> UseStatCd.Usable.equals(t.getUseStatCd()))
			.forEach(t -> issueTransitionAccess.delete(t.getObjId()));

		return request.getTransitions()
			.stream()
			.map(item -> {
				SnCiraIssueTransitionModel transition = SnCiraIssueTransitionModel.builder()
					.projectId(projectId)
					.spaceId(sid)
					.fromStatusId(item.getFromStatusId())
					.toStatusId(item.getToStatusId())
					.allowYn("Y")
					.srvId(ApplicationProperties.getApplicationServiceName())
					.tenant(TenantContext.get())
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
			.projectTypeId(model.getProjectTypeId())
			.projectTypeNm(model.getProjectType())
			.ownerId(model.getOwnerId())
			.issueSequence(model.getIssueSequence())
			.build();
	}
}
