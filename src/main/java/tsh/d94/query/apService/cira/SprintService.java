package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.CompleteSprintRequest;
import tsh.d94.app.server.apService.cira.dto.CreateSprintRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.SprintResponse;
import tsh.d94.app.server.apService.cira.dto.UpdateSprintRequest;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintAccess;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SprintService {

	private static final String STAT_PLANNED = "Planned";
	private static final String STAT_ACTIVE = "Active";
	private static final String STAT_COMPLETED = "Completed";

	private final SnCiraSprintAccess sprintAccess;
	private final SnCiraProjectAccess projectAccess;
	private final SnCiraProjectMemberAccess projectMemberAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraIssueStatusAccess issueStatusAccess;
	private final GsUserAccess userAccess;
	private final IssueService issueService;
	private final NotificationService notificationService;

	@Transactional
	public SprintResponse createSprint(String projectId, CreateSprintRequest request) {
		String email = currentEmail();
		GsUserModel user = findUser(email);

		validateMembership(user.getObjId(), projectId);
		projectAccess.findById(projectId); // 프로젝트 존재 확인

		SnCiraSprintModel sprint = SnCiraSprintModel.builder()
			.projectId(projectId)
			.sprintNm(request.getSprintNm())
			.goal(request.getGoal())
			.startDt(request.getStartDt())
			.endDt(request.getEndDt())
			.sprintStat(STAT_PLANNED)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("CREATE-SPRINT")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateSprint")
			.prevEvntNm("None")
			.build();

		sprintAccess.save(sprint);
		return mapToResponse(sprint);
	}

	public List<SprintResponse> getSprints(String projectId) {
		projectAccess.findById(projectId);
		return sprintAccess.findByProjectId(projectId).stream()
			.filter(s -> UseStatCd.Usable.equals(s.getUseStatCd()))
			.map(this::mapToResponse)
			.collect(Collectors.toList());
	}

	public Optional<SprintResponse> getActiveSprint(String projectId) {
		projectAccess.findById(projectId);
		return sprintAccess.findByProjectIdAndSprintStat(projectId, STAT_ACTIVE).stream()
			.filter(s -> UseStatCd.Usable.equals(s.getUseStatCd()))
			.findFirst()
			.map(this::mapToResponse);
	}

	public Page<IssueResponse> getBacklog(String projectId, Pageable pageable) {
		projectAccess.findById(projectId);
		return issueService.getBacklog(projectId, pageable);
	}

	public SprintResponse getSprint(String sprintId) {
		SnCiraSprintModel sprint = findActiveSprint(sprintId);
		return mapToResponse(sprint);
	}

	@Transactional
	public SprintResponse updateSprint(String sprintId, UpdateSprintRequest request) {
		String email = currentEmail();
		GsUserModel user = findUser(email);

		SnCiraSprintModel sprint = findActiveSprint(sprintId);
		validateMembership(user.getObjId(), sprint.getProjectId());

		if (request.getSprintNm() != null)
			sprint.setSprintNm(request.getSprintNm());
		if (request.getGoal() != null)
			sprint.setGoal(request.getGoal());
		if (request.getStartDt() != null)
			sprint.setStartDt(request.getStartDt());
		if (request.getEndDt() != null)
			sprint.setEndDt(request.getEndDt());

		sprint.setEvtNm("UpdateSprint");
		sprint.setPrevEvntNm("CreateSprint");
		sprintAccess.save(sprint);
		return mapToResponse(sprint);
	}

	@Transactional
	public void deleteSprint(String sprintId) {
		String email = currentEmail();
		GsUserModel user = findUser(email);

		SnCiraSprintModel sprint = findActiveSprint(sprintId);
		validateMembership(user.getObjId(), sprint.getProjectId());

		// 스프린트에 할당된 이슈를 백로그(sprintId=null)로 이동
		unassignAllIssues(sprintId);

		sprint.setUseStatCd(UseStatCd.Delete);
		sprint.setEvtNm("DeleteSprint");
		sprint.setPrevEvntNm(sprint.getEvtNm());
		sprintAccess.save(sprint);
	}

	@Transactional
	public SprintResponse startSprint(String sprintId) {
		String email = currentEmail();
		GsUserModel user = findUser(email);

		SnCiraSprintModel sprint = findActiveSprint(sprintId);
		validateMembership(user.getObjId(), sprint.getProjectId());

		if (!STAT_PLANNED.equals(sprint.getSprintStat())) {
			throw new CiraException(ErrorCode.SPRINT_INVALID_TRANSITION,
				"Planned 상태인 스프린트만 시작할 수 있습니다. 현재 상태: " + sprint.getSprintStat());
		}

		// 동일 프로젝트에 이미 Active 스프린트가 있으면 차단
		boolean hasActive = sprintAccess.findByProjectIdAndSprintStat(sprint.getProjectId(), STAT_ACTIVE)
			.stream().anyMatch(s -> UseStatCd.Usable.equals(s.getUseStatCd()));
		if (hasActive) {
			throw new CiraException(ErrorCode.SPRINT_ALREADY_ACTIVE);
		}

		sprint.setSprintStat(STAT_ACTIVE);
		sprint.setEvtNm("StartSprint");
		sprint.setPrevEvntNm("CreateSprint");
		sprintAccess.save(sprint);

		// 프로젝트 멤버 전체에게 스프린트 시작 알림
		notifyProjectMembers(sprint.getProjectId(),
			"SPRINT_STARTED", "스프린트가 시작되었습니다",
			"[" + sprint.getSprintNm() + "] 스프린트가 시작되었습니다.", "SPRINT", sprint.getObjId());

		return mapToResponse(sprint);
	}

	@Transactional
	public SprintResponse completeSprint(String sprintId, CompleteSprintRequest request) {
		String email = currentEmail();
		GsUserModel user = findUser(email);

		SnCiraSprintModel sprint = findActiveSprint(sprintId);
		validateMembership(user.getObjId(), sprint.getProjectId());

		if (!STAT_ACTIVE.equals(sprint.getSprintStat())) {
			throw new CiraException(ErrorCode.SPRINT_INVALID_TRANSITION,
				"Active 상태인 스프린트만 완료할 수 있습니다. 현재 상태: " + sprint.getSprintStat());
		}

		boolean isNextSprint = "NEXT_SPRINT".equals(request.getIncompleteIssueAction());
		if (isNextSprint && request.getNextSprintId() == null) {
			throw new CiraException(ErrorCode.SPRINT_NEXT_SPRINT_REQUIRED);
		}

		Set<String> doneStatusIds = issueStatusAccess.findByProjectId(sprint.getProjectId()).stream()
			.filter(s -> "DONE".equals(s.getCategory()))
			.map(s -> s.getObjId())
			.collect(Collectors.toSet());

		issueAccess.findBySprintId(sprintId).forEach(issue -> {
			if (doneStatusIds.contains(issue.getStatusId())) {
				return; // Done 이슈는 현재 스프린트 유지
			}
			String prevEvt = issue.getEvtNm() != null ? issue.getEvtNm() : "None";
			if (isNextSprint) {
				issue.setSprintId(request.getNextSprintId());
				issue.setEvtNm("MoveToNextSprint");
			} else {
				issue.setSprintId(null);
				issue.setEvtNm("UnassignFromSprint");
			}
			issue.setPrevEvntNm(prevEvt);
			issueAccess.save(issue);
		});

		sprint.setSprintStat(STAT_COMPLETED);
		sprint.setEvtNm("CompleteSprint");
		sprint.setPrevEvntNm("StartSprint");
		sprintAccess.save(sprint);

		// 프로젝트 멤버 전체에게 스프린트 종료 알림
		notifyProjectMembers(sprint.getProjectId(),
			"SPRINT_COMPLETED", "스프린트가 완료되었습니다",
			"[" + sprint.getSprintNm() + "] 스프린트가 완료되었습니다.", "SPRINT", sprint.getObjId());

		return mapToResponse(sprint);
	}

	@Transactional
	public void assignIssueToSprint(String sprintId, String issueId) {
		String email = currentEmail();
		GsUserModel user = findUser(email);

		SnCiraSprintModel sprint = findActiveSprint(sprintId);
		validateMembership(user.getObjId(), sprint.getProjectId());

		SnCiraIssueModel issue = issueAccess.findById(issueId);
		if (issue.getDeletedAt() != null) {
			throw new EntityNotFoundException("Issue not found: " + issueId);
		}
		if (!sprint.getProjectId().equals(issue.getProjectId())) {
			throw new IllegalArgumentException("이슈가 스프린트의 프로젝트에 속하지 않습니다.");
		}

		issue.setSprintId(sprintId);
		issue.setEvtNm("AssignToSprint");
		issue.setPrevEvntNm(issue.getEvtNm() != null ? issue.getEvtNm() : "None");
		issueAccess.save(issue);
	}

	@Transactional
	public void removeIssueFromSprint(String sprintId, String issueId) {
		String email = currentEmail();
		GsUserModel user = findUser(email);

		SnCiraSprintModel sprint = findActiveSprint(sprintId);
		validateMembership(user.getObjId(), sprint.getProjectId());

		SnCiraIssueModel issue = issueAccess.findById(issueId);
		if (issue.getDeletedAt() != null) {
			throw new EntityNotFoundException("Issue not found: " + issueId);
		}

		issue.setSprintId(null);
		issue.setEvtNm("RemoveFromSprint");
		issue.setPrevEvntNm(issue.getEvtNm() != null ? issue.getEvtNm() : "None");
		issueAccess.save(issue);
	}

	private void unassignAllIssues(String sprintId) {
		issueAccess.findBySprintId(sprintId).forEach(issue -> {
			String prevEvt = issue.getEvtNm() != null ? issue.getEvtNm() : "None";
			issue.setSprintId(null);
			issue.setPrevEvntNm(prevEvt);
			issue.setEvtNm("UnassignFromSprint");
			issueAccess.save(issue);
		});
	}

	private SnCiraSprintModel findActiveSprint(String sprintId) {
		SnCiraSprintModel sprint = sprintAccess.findByIdOptional(sprintId)
			.orElseThrow(() -> new CiraException(ErrorCode.SPRINT_NOT_FOUND, sprintId));
		if (UseStatCd.Delete.equals(sprint.getUseStatCd())) {
			throw new CiraException(ErrorCode.SPRINT_NOT_FOUND, sprintId);
		}
		return sprint;
	}

	private void validateMembership(String userId, String projectId) {
		projectMemberAccess.findAllByUserId(userId).stream()
			.filter(m -> m.getProjectId().equals(projectId))
			.findFirst()
			.orElseThrow(() -> new CiraException(ErrorCode.PROJECT_NOT_MEMBER));
	}

	private String currentEmail() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	private GsUserModel findUser(String email) {
		return userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
	}

	private SprintResponse mapToResponse(SnCiraSprintModel model) {
		return SprintResponse.builder()
			.id(model.getObjId())
			.projectId(model.getProjectId())
			.sprintNm(model.getSprintNm())
			.goal(model.getGoal())
			.startDt(model.getStartDt())
			.endDt(model.getEndDt())
			.sprintStat(model.getSprintStat())
			.createdAt(model.getCreatedAt())
			.modifiedAt(model.getModifiedAt())
			.build();
	}

	private void notifyProjectMembers(String projectId, String type, String title,
		String message, String resourceType, String resourceId) {
		projectMemberAccess.findAllByProjectId(projectId).forEach(member ->
			notificationService.send(member.getUserId(), type, title, message, resourceType, resourceId)
		);
	}
}
