package tsh.d94.app.server.apService.cira;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.automation.events.IssueAssignedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueCreatedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueStatusChangedEvent;
import tsh.d94.app.server.apService.cira.dto.ChangeStatusRequest;
import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.IssueFilterRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.IssueStatusResponse;
import tsh.d94.app.server.apService.cira.dto.UpdateIssueRequest;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardModel;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnModel;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeAccess;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeModel;
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
import tsh.d94.lib.core.apService.auth.dto.UserResponse;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IssueService {

	private final SnCiraIssueAccess issueAccess;
	private final SnCiraProjectAccess projectAccess;
	private final GsUserAccess userAccess;
	private final SnCiraCiraIssueTypeAccess issueTypeAccess;
	private final SnCiraIssueStatusAccess statusAccess;
	private final SnCiraIssueLogAccess issueLogAccess;
	private final SnCiraIssuePositionAccess issuePositionAccess;
	private final SnCiraProjectMemberAccess projectMemberAccess;
	private final WorkflowService workflowService;
	private final SnCiraBoardColumnAccess boardColumnAccess;
	private final SnCiraBoardAccess boardAccess;
	private final NotificationService notificationService;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public IssueResponse createIssue(String projectId, CreateIssueRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel reporter = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraProjectModel project = projectAccess.findById(projectId);

		// Check membership
		projectMemberAccess.findAllByUserId(reporter.getObjId()).stream()
			.filter(m -> m.getProjectId().equals(projectId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("User is not a member of the project"));

		// Increment sequence and generate key
		project.setIssueSequence(project.getIssueSequence() + 1);
		projectAccess.save(project);
		String issueKey = project.getProjectKey() + "-" + project.getIssueSequence();

		SnCiraIssueModel issue = SnCiraIssueModel.builder()
			.projectId(projectId)
			.sprintId(request.getSprintId())
			.issueKey(issueKey)
			.title(request.getTitle())
			.content(request.getContent())
			.issueTypeId(request.getIssueTypeId())
			.statusId(request.getStatusId())
			.priority(request.getPriority())
			.storyPnt(request.getStoryPnt())
			.assigneeId(request.getAssigneeId())
			.reporterId(reporter.getObjId())
			.dueDt(request.getDueDt())
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("CREATE-ISSUE")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateIssue")
			.prevEvntNm("None")
			.build();

		issueAccess.save(issue);

		// 자동화 이벤트 발행: 이슈 생성
		eventPublisher.publishEvent(new IssueCreatedEvent(this, issue.getObjId(), projectId, reporter.getObjId()));

		// Record Log
		recordLog(issue.getObjId(), "Issue", null, "Created", reporter.getObjId());

		// Initial Position: 이슈 상태와 매핑된 컬럼을 찾아 초기 위치 설정
		String columnId = resolveColumnId(projectId, issue.getStatusId());
		if (columnId != null) {
			List<SnCiraIssuePositionModel> existing = issuePositionAccess.findByColumnIdOrderByRankStr(columnId);
			String rankStr = existing.isEmpty()
				? LexoRankUtil.initial()
				: LexoRankUtil.after(existing.get(existing.size() - 1).getRankStr());

			SnCiraIssuePositionModel position = SnCiraIssuePositionModel.builder()
				.issueId(issue.getObjId())
				.columnId(columnId)
				.rankStr(rankStr)
				.srvId(ApplicationProperties.getApplicationServiceName())
				.tenant(ApplicationProperties.getApplicationTenant())
				.traceId("CREATE-ISSUE")
				.useStatCd(UseStatCd.Usable)
				.evtNm("SetInitialPosition")
				.prevEvntNm("None")
				.build();
			issuePositionAccess.save(position);
		}

		return mapToResponse(issue);
	}

	public IssueResponse getIssue(String issueId) {
		SnCiraIssueModel issue = issueAccess.findById(issueId);

		if (issue.getDeletedAt() != null) {
			throw new EntityNotFoundException("Issue not found: " + issueId);
		}

		return mapToResponse(issue);
	}

	@Transactional
	public IssueResponse updateIssue(String issueId, UpdateIssueRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraIssueModel issue = issueAccess.findById(issueId);

		// Check membership
		projectMemberAccess.findAllByUserId(user.getObjId()).stream()
			.filter(m -> m.getProjectId().equals(issue.getProjectId()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("User is not a member of the project"));

		// Record changes and update
		if (request.getTitle() != null && !request.getTitle().equals(issue.getTitle())) {
			recordLog(issueId, "TITLE", issue.getTitle(), request.getTitle(), user.getObjId());
			issue.setTitle(request.getTitle());
		}
		if (request.getContent() != null && !request.getContent().equals(issue.getContent())) {
			recordLog(issueId, "CONTENT", issue.getContent(), request.getContent(), user.getObjId());
			issue.setContent(request.getContent());
		}

		// assignee 변경 알림
		String prevAssigneeId = issue.getAssigneeId();
		if (request.getAssigneeId() != null && !request.getAssigneeId().equals(prevAssigneeId)) {
			recordLog(issueId, "ASSIGNEE_ID", prevAssigneeId, request.getAssigneeId(), user.getObjId());
			issue.setAssigneeId(request.getAssigneeId());
			notificationService.send(
				request.getAssigneeId(),
				"ISSUE_ASSIGNED",
				"이슈가 배정되었습니다",
				"[" + issue.getIssueKey() + "] " + issue.getTitle(),
				"ISSUE", issue.getObjId()
			);
			// 자동화 이벤트 발행: 이슈 담당자 변경
			eventPublisher.publishEvent(new IssueAssignedEvent(
				this, issueId, issue.getProjectId(), request.getAssigneeId(), user.getObjId()));
		}

		issueAccess.save(issue);
		return mapToResponse(issue);
	}

	@Transactional
	public void deleteIssue(String issueId) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraIssueModel issue = issueAccess.findById(issueId);

		// Check Admin membership
		SnCiraProjectMemberModel member = projectMemberAccess.findAllByUserId(user.getObjId()).stream()
			.filter(m -> m.getProjectId().equals(issue.getProjectId()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("User is not a member of the project"));

		if (!"ADMIN".equals(member.getRole())) {
			throw new IllegalArgumentException("Only Admin can delete issues");
		}

		issue.setDeletedAt(LocalDateTime.now());
		issueAccess.save(issue);
	}

	@Transactional
	public IssueResponse changeStatus(String issueId, ChangeStatusRequest request) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraIssueModel issue = issueAccess.findById(issueId);

		if (issue.getDeletedAt() != null) {
			throw new CiraException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다: " + issueId);
		}

		projectMemberAccess.findAllByUserId(user.getObjId()).stream()
			.filter(m -> m.getProjectId().equals(issue.getProjectId()))
			.findFirst()
			.orElseThrow(() -> new CiraException(ErrorCode.PROJECT_NOT_MEMBER));

		String fromStatusId = issue.getStatusId();
		String toStatusId = request.getStatusId();

		SnCiraIssueStatusModel fromStatus = statusAccess.findByIdOptional(fromStatusId).orElse(null);
		SnCiraIssueStatusModel toStatus = statusAccess.findByIdOptional(toStatusId)
			.orElseThrow(() -> new CiraException(ErrorCode.ISSUE_STATUS_NOT_FOUND, "존재하지 않는 상태 ID: " + toStatusId));

		workflowService.validateTransition(issue.getProjectId(), fromStatusId, toStatusId);

		issue.setStatusId(toStatusId);
		issueAccess.save(issue);

		recordLog(issueId, "status",
			fromStatus != null ? fromStatus.getStatusNm() : fromStatusId,
			toStatus.getStatusNm(),
			user.getObjId());

		updateIssuePosition(issueId, toStatusId);

		// 상태 변경 알림 (담당자/보고자)
		String statusMsg = "[" + issue.getIssueKey() + "] 상태가 " + toStatus.getStatusNm() + "(으)로 변경되었습니다";
		if (issue.getAssigneeId() != null && !issue.getAssigneeId().equals(user.getObjId())) {
			notificationService.send(issue.getAssigneeId(), "ISSUE_STATUS_CHANGED",
				"이슈 상태 변경", statusMsg, "ISSUE", issueId);
		}
		if (!issue.getReporterId().equals(user.getObjId())
			&& !issue.getReporterId().equals(issue.getAssigneeId())) {
			notificationService.send(issue.getReporterId(), "ISSUE_STATUS_CHANGED",
				"이슈 상태 변경", statusMsg, "ISSUE", issueId);
		}

		// 자동화 이벤트 발행: 이슈 상태 변경
		eventPublisher.publishEvent(new IssueStatusChangedEvent(
			this, issueId, issue.getProjectId(), fromStatusId, toStatusId, user.getObjId()));

		return mapToResponse(issue);
	}

	public List<IssueStatusResponse> getAvailableTransitions(String issueId) {
		SnCiraIssueModel issue = issueAccess.findById(issueId);

		if (issue.getDeletedAt() != null) {
			throw new CiraException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다: " + issueId);
		}

		return workflowService.getAvailableTransitions(issue.getProjectId(), issue.getStatusId());
	}

	private void updateIssuePosition(String issueId, String newStatusId) {
		List<SnCiraIssuePositionModel> positions = issuePositionAccess.findByIssueId(issueId);

		for (SnCiraIssuePositionModel position : positions) {
			SnCiraBoardColumnModel currentColumn = boardColumnAccess.findByIdOptional(position.getColumnId())
				.orElse(null);
			if (currentColumn == null) {
				continue;
			}
			boardColumnAccess.findByBoardIdAndStatusId(currentColumn.getBoardId(), newStatusId)
				.ifPresent(newColumn -> {
					position.setColumnId(newColumn.getObjId());
					issuePositionAccess.save(position);
				});
		}
	}

	public Page<IssueResponse> getBacklog(String projectId, Pageable pageable) {
		Specification<SnCiraIssueModel> spec = (root, query, cb) -> cb.and(
			cb.equal(root.get("projectId"), projectId),
			cb.isNull(root.get("deletedAt")),
			cb.isNull(root.get("sprintId"))
		);
		return issueAccess.findAll(spec, pageable).map(this::mapToResponse);
	}

	public Page<IssueResponse> listIssues(String projectId, IssueFilterRequest filter, Pageable pageable) {
		Specification<SnCiraIssueModel> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.equal(root.get("projectId"), projectId));
			predicates.add(cb.isNull(root.get("deletedAt")));

			if (filter.getStatus() != null && !filter.getStatus().isEmpty()) {
				predicates.add(root.get("statusId").in(filter.getStatus()));
			}
			if (filter.getPriority() != null) {
				predicates.add(cb.equal(root.get("priority"), filter.getPriority()));
			}
			if (filter.getAssigneeId() != null) {
				predicates.add(cb.equal(root.get("assigneeId"), filter.getAssigneeId()));
			}
			if (filter.getIssueType() != null) {
				predicates.add(cb.equal(root.get("issueTypeId"), filter.getIssueType()));
			}
			if (filter.getSprintId() != null) {
				predicates.add(cb.equal(root.get("sprintId"), filter.getSprintId()));
			}
			if (filter.getKeyword() != null && !filter.getKeyword().isEmpty()) {
				predicates.add(cb.like(cb.lower(root.get("title")), "%" + filter.getKeyword().toLowerCase() + "%"));
			}

			return cb.and(predicates.toArray(new Predicate[0]));
		};

		return issueAccess.findAll(spec, pageable).map(this::mapToResponse);
	}

	private String resolveColumnId(String projectId, String statusId) {
		if (statusId == null)
			return null;
		Set<String> boardIds = boardAccess.findByProjectId(projectId).stream()
			.map(SnCiraBoardModel::getObjId)
			.collect(Collectors.toSet());
		return boardColumnAccess.findByStatusId(statusId).stream()
			.filter(c -> boardIds.contains(c.getBoardId()))
			.map(SnCiraBoardColumnModel::getObjId)
			.findFirst()
			.orElse(null);
	}

	private void recordLog(String issueId, String field, String oldVal, String newVal, String changerId) {
		SnCiraIssueLogModel log = SnCiraIssueLogModel.builder()
			.issueId(issueId)
			.fieldNm(field)
			.oldVal(oldVal)
			.newVal(newVal)
			.changedBy(changerId)
			.changedAt(LocalDateTime.now())
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("ISSUE-LOG")
			.useStatCd(UseStatCd.Usable)
			.evtNm("RecordLog")
			.prevEvntNm("None")
			.build();
		issueLogAccess.save(log);
	}

	private IssueResponse mapToResponse(SnCiraIssueModel model) {
		GsUserModel reporter = userAccess.findByIdOptional(model.getReporterId()).orElse(null);
		GsUserModel assignee =
			model.getAssigneeId() != null ? userAccess.findByIdOptional(model.getAssigneeId()).orElse(null) : null;

		SnCiraCiraIssueTypeModel type = issueTypeAccess.findByIdOptional(model.getIssueTypeId()).orElse(null);
		SnCiraIssueStatusModel status = statusAccess.findByIdOptional(model.getStatusId()).orElse(null);

		return IssueResponse.builder()
			.id(model.getObjId())
			.issueKey(model.getIssueKey())
			.title(model.getTitle())
			.content(model.getContent())
			.issueTypeId(model.getIssueTypeId())
			.issueTypeNm(type != null ? type.getTypeNm() : null)
			.statusId(model.getStatusId())
			.statusNm(status != null ? status.getStatusNm() : null)
			.priority(model.getPriority())
			.storyPnt(model.getStoryPnt())
			.reporter(reporter != null ? UserResponse.builder()
				.email(reporter.getEmail())
				.name(reporter.getUserNm())
				.avatarUrl(reporter.getAvatarUrl())
				.build() : null)
			.assignee(assignee != null ? UserResponse.builder()
				.email(assignee.getEmail())
				.name(assignee.getUserNm())
				.avatarUrl(assignee.getAvatarUrl())
				.build() : null)
			.projectId(model.getProjectId())
			.sprintId(model.getSprintId())
			.dueDt(model.getDueDt())
			.createdAt(model.getCreatedAt())
			.modifiedAt(model.getModifiedAt())
			.build();
	}
}
