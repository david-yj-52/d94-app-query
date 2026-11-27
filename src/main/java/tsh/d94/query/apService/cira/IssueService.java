package tsh.d94.app.server.apService.cira;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import tsh.d94.app.server.apService.cira.automation.events.IssueContentChangedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueCreatedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueStatusChangedEvent;
import tsh.d94.app.server.apService.cira.automation.events.IssueTitleChangedEvent;
import tsh.d94.app.server.apService.cira.dto.ChangeStatusRequest;
import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.IssueFilterRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.IssueTypeResponse;
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
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
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

	/** 이슈 유형을 지정하지 않았을 때의 기본값 (Flyway V320 시드, V590 에서 Task → Issue) */
	private static final String DEFAULT_ISSUE_TYPE = "Issue";

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
	private final CiraFieldService fieldService;
	private final SpaceGuard spaceGuard;

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

		String issueTypeId = resolveIssueTypeId(request.getIssueTypeId());
		// 공간·Feature 범위 (V630): COMMON Feature 만 공통 레이어(SPACE_ID null), 나머지는 공간 소유
		String featureScope = FeatureRules.scope(isFeature(issueTypeId), request.getFeatureScope());
		String spaceId = FeatureRules.COMMON.equals(featureScope) ? null
			: spaceGuard.resolve(projectId, request.getSpaceId()).getObjId();
		String featureId = blankToNull(request.getFeatureId());
		validateFeatureLink(projectId, featureScope, spaceId, featureId);
		String statusId = resolveStatusId(projectId, spaceId, request.getStatusId());
		validateAssignee(projectId, request.getAssigneeId());

		// Increment sequence and generate key
		project.setIssueSequence(project.getIssueSequence() + 1);
		projectAccess.save(project);
		String issueKey = project.getProjectKey() + "-" + project.getIssueSequence();

		SnCiraIssueModel issue = SnCiraIssueModel.builder()
			.projectId(projectId)
			.spaceId(spaceId)
			.featureScope(featureScope)
			.featureId(featureId)
			.sprintId(request.getSprintId())
			.issueKey(issueKey)
			.title(request.getTitle())
			.content(request.getContent())
			.issueTypeId(issueTypeId)
			.statusId(statusId)
			.priority(request.getPriority() != null ? request.getPriority() : "MEDIUM") // DB NOT NULL, 웹은 선택 항목
			.storyPnt(request.getStoryPnt())
			.assigneeId(request.getAssigneeId())
			.reporterId(reporter.getObjId())
			.dueDt(request.getDueDt())
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(TenantContext.get())
			.traceId("CREATE-ISSUE")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateIssue")
			.prevEvntNm("None")
			.build();

		issueAccess.save(issue);
		fieldService.applyValues(projectId, spaceId, issue.getObjId(), request.getCustomFields(), true);

		// 자동화 이벤트 발행: 이슈 생성
		eventPublisher.publishEvent(new IssueCreatedEvent(this, issue.getObjId(), projectId, reporter.getObjId()));

		// Record Log
		recordLog(issue.getObjId(), "Issue", null, "Created", reporter.getObjId());

		// Initial Position: 이슈 상태와 매핑된 컬럼을 찾아 초기 위치 설정
		String columnId = spaceId == null ? null : resolveColumnId(spaceId, issue.getStatusId()); // 공통 Feature 는 보드에 없다
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
				.tenant(TenantContext.get())
				.traceId("CREATE-ISSUE")
				.useStatCd(UseStatCd.Usable)
				.evtNm("SetInitialPosition")
				.prevEvntNm("None")
				.build();
			issuePositionAccess.save(position);
		}

		IssueResponse created = withValues(mapToResponse(issue));
		created.setContent(request.getContent()); // 저장은 Docs 메인 노트에만 (CiraDocsService 가 CONTENT 를 비운다)
		return created;
	}

	public IssueResponse getIssue(String issueId) {
		SnCiraIssueModel issue = issueAccess.findById(issueId);

		if (issue.getDeletedAt() != null) {
			throw new EntityNotFoundException("Issue not found: " + issueId);
		}

		return withValues(mapToResponse(issue));
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
			eventPublisher.publishEvent(new IssueTitleChangedEvent(this, issueId)); // Docs 메인 노트 제목 동기화
		}
		if (request.getContent() != null) {
			// 설명의 원본은 Docs 메인 노트 (V630). 이력은 노트 버전이 남긴다
			eventPublisher.publishEvent(new IssueContentChangedEvent(this, issueId, request.getContent()));
		}
		if (request.getFeatureId() != null) {
			String featureId = blankToNull(request.getFeatureId());
			if (!java.util.Objects.equals(featureId, issue.getFeatureId())) {
				validateFeatureLink(issue.getProjectId(), issue.getFeatureScope(), issue.getSpaceId(), featureId);
				if (featureId != null && featureId.equals(issueId)) {
					throw new IllegalArgumentException("자기 자신에 연결할 수 없습니다");
				}
				recordLog(issueId, "FEATURE_ID", issue.getFeatureId(), featureId, user.getObjId());
				issue.setFeatureId(featureId);
			}
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

		if (request.getCustomFields() != null) {
			fieldService.applyValues(issue.getProjectId(), issue.getSpaceId(), issueId, request.getCustomFields(), false);
		}

		issueAccess.save(issue);
		return withValues(mapToResponse(issue));
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
		return withValues(issueAccess.findAll(spec, pageable).map(this::mapToResponse));
	}

	public Page<IssueResponse> listIssues(String projectId, IssueFilterRequest filter, Pageable pageable) {
		IssueSort.validate(pageable.getSort());
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
			if (filter.getSpaceId() != null) {
				predicates.add(cb.equal(root.get("spaceId"), filter.getSpaceId()));
			}
			if (filter.getFeatureId() != null) {
				predicates.add(cb.equal(root.get("featureId"), filter.getFeatureId()));
			}
			if (filter.getKeyword() != null && !filter.getKeyword().isEmpty()) {
				predicates.add(cb.like(cb.lower(root.get("title")), "%" + filter.getKeyword().toLowerCase() + "%"));
			}

			if (pageable.getSort().isSorted() && !Long.class.equals(query.getResultType())) {
				query.orderBy(IssueSort.orders(root, query, cb, pageable.getSort()));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};

		Pageable unsorted = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		return withValues(issueAccess.findAll(spec, unsorted).map(this::mapToResponse));
	}

	/** 페이지의 사용자 정의 값을 한 번에 붙인다 (N+1 금지) */
	private Page<IssueResponse> withValues(Page<IssueResponse> page) {
		Map<String, Map<String, Object>> values = fieldService.values(page.getContent().stream().map(IssueResponse::getId).toList());
		page.getContent().forEach(r -> r.setCustomFields(values.getOrDefault(r.getId(), new java.util.HashMap<>())));
		return page;
	}

	private IssueResponse withValues(IssueResponse r) {
		r.setCustomFields(fieldService.values(java.util.Collections.singletonList(r.getId())).getOrDefault(r.getId(), new java.util.HashMap<>()));
		return r;
	}

	/** 공통 Feature 와 그 아래 단위 Feature, 단독 단위 Feature. taskCount = 직접 연결된 Task·Feature 수 */
	public record FeatureNode(IssueResponse feature, long taskCount, List<FeatureNode> children) {
	}

	/**
	 * 2단계 Feature 트리. COMMON 이 항상 먼저(최상위), 그 아래 연결된 UNIT, 그다음 공통에 안 묶인 UNIT.
	 * spaceId 를 주면 UNIT 은 그 공간 것만 (COMMON 은 공간과 무관하게 항상 포함).
	 */
	public List<FeatureNode> getFeatureTree(String projectId, String spaceId) {
		spaceGuard.requireMember(projectId);
		List<SnCiraIssueModel> all = issueAccess.findFeatures(projectId).stream()
			.filter(f -> FeatureRules.COMMON.equals(f.getFeatureScope()) || spaceId == null || spaceId.equals(f.getSpaceId()))
			.sorted(FeatureRules.commonFirst(SnCiraIssueModel::getFeatureScope,
				java.util.Comparator.comparing(SnCiraIssueModel::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))))
			.toList();
		Set<String> commonIds = all.stream().filter(f -> FeatureRules.COMMON.equals(f.getFeatureScope()))
			.map(SnCiraIssueModel::getObjId).collect(Collectors.toSet());
		List<FeatureNode> out = new ArrayList<>();
		for (SnCiraIssueModel f : all) {
			if (FeatureRules.COMMON.equals(f.getFeatureScope())) {
				List<FeatureNode> units = all.stream()
					.filter(u -> f.getObjId().equals(u.getFeatureId()))
					.map(u -> new FeatureNode(mapToResponse(u), issueAccess.countLiveByFeatureId(u.getObjId()), List.of()))
					.toList();
				out.add(new FeatureNode(mapToResponse(f), issueAccess.countLiveByFeatureId(f.getObjId()), units));
			} else if (f.getFeatureId() == null || !commonIds.contains(f.getFeatureId())) {
				out.add(new FeatureNode(mapToResponse(f), issueAccess.countLiveByFeatureId(f.getObjId()), List.of()));
			}
		}
		return out;
	}

	/** 선택 가능한 이슈 유형 (전역, 이름순) */
	public List<IssueTypeResponse> getIssueTypes() {
		return issueTypeAccess.findAllUsable().stream()
			.map(t -> IssueTypeResponse.builder()
				.id(t.getObjId())
				.typeNm(t.getTypeNm())
				.icon(t.getIcon())
				.colorCd(t.getColorCd())
				.descr(t.getDescr())
				.build())
			.collect(Collectors.toList());
	}

	/** 상태 미지정 → 공간 워크플로의 첫 상태(sortOrd 최소). 지정했으면 이 공간의 상태인지 확인. 공통 레이어(null)는 기본 공간 워크플로 */
	private String resolveStatusId(String projectId, String spaceId, String statusId) {
		String sid = spaceGuard.resolve(projectId, spaceId).getObjId();
		List<SnCiraIssueStatusModel> statuses = statusAccess.findBySpaceId(sid).stream()
			.filter(s -> UseStatCd.Usable.equals(s.getUseStatCd()))
			.collect(Collectors.toList());
		if (statusId == null) {
			return statuses.stream()
				.min(java.util.Comparator.comparing(SnCiraIssueStatusModel::getSortOrd))
				.map(SnCiraIssueStatusModel::getObjId)
				.orElseThrow(() -> new IllegalArgumentException("작업 공간에 이슈 상태가 없습니다: " + sid));
		}
		if (statuses.stream().noneMatch(s -> statusId.equals(s.getObjId()))) {
			throw new IllegalArgumentException("이 작업 공간의 이슈 상태가 아닙니다: " + statusId);
		}
		return statusId;
	}

	private boolean isFeature(String issueTypeId) {
		return issueTypeAccess.findByIdOptional(issueTypeId)
			.map(t -> FeatureRules.FEATURE_TYPE.equals(t.getTypeNm())).orElse(false);
	}

	/** featureId 가 가리키는 Feature 를 찾아 FeatureRules 로 검사 */
	private void validateFeatureLink(String projectId, String scope, String spaceId, String featureId) {
		if (featureId == null) {
			return;
		}
		SnCiraIssueModel target = issueAccess.findByIdOptional(featureId)
			.filter(f -> projectId.equals(f.getProjectId()) && f.getDeletedAt() == null)
			.orElseThrow(() -> new IllegalArgumentException("이 프로젝트의 Feature 가 아닙니다: " + featureId));
		FeatureRules.validateLink(scope, spaceId, target.getFeatureScope(), target.getSpaceId());
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}

	/** 유형 미지정 → 기본 유형 Issue. 지정했으면 존재하는지 확인 */
	private String resolveIssueTypeId(String issueTypeId) {
		if (issueTypeId == null) {
			return issueTypeAccess.findByTypeNm(DEFAULT_ISSUE_TYPE)
				.map(SnCiraCiraIssueTypeModel::getObjId)
				.orElseThrow(() -> new IllegalArgumentException("기본 이슈 유형이 없습니다: " + DEFAULT_ISSUE_TYPE));
		}
		issueTypeAccess.findByIdOptional(issueTypeId)
			.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이슈 유형입니다: " + issueTypeId));
		return issueTypeId;
	}

	/** 담당자는 이 프로젝트의 (제거되지 않은) 멤버의 사용자 objId 여야 한다. 이메일 등은 거절 */
	private void validateAssignee(String projectId, String assigneeId) {
		if (assigneeId == null) {
			return;
		}
		projectMemberAccess.findByProjectIdAndUserId(projectId, assigneeId)
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.orElseThrow(() -> new IllegalArgumentException("담당자가 프로젝트 멤버가 아닙니다: " + assigneeId));
	}

	private String resolveColumnId(String spaceId, String statusId) {
		if (statusId == null)
			return null;
		Set<String> boardIds = boardAccess.findBySpaceId(spaceId).stream()
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
			.tenant(TenantContext.get())
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
			.spaceId(model.getSpaceId())
			.featureScope(model.getFeatureScope())
			.featureId(model.getFeatureId())
			.sprintId(model.getSprintId())
			.dueDt(model.getDueDt())
			.startedAt(model.getStartedAt())
			.resolvedAt(model.getResolvedAt())
			.createdAt(model.getCreatedAt())
			.modifiedAt(model.getModifiedAt())
			.build();
	}
}
