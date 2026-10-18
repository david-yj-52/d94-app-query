package tsh.d94.app.server.apService.cira;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import tsh.d94.app.server.apService.cira.dto.AutocompleteResponse;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.SavedFilterRequest;
import tsh.d94.app.server.apService.cira.dto.SavedFilterResponse;
import tsh.d94.app.server.apService.cira.dto.SearchIssueRequest;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraSavedFilter.SnCiraSavedFilterAccess;
import tsh.d94.app.server.data.orm.cira.ciraSavedFilter.SnCiraSavedFilterModel;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintAccess;
import tsh.d94.lib.core.apService.auth.dto.UserResponse;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

	private final SnCiraIssueAccess issueAccess;
	private final SnCiraSavedFilterAccess savedFilterAccess;
	private final GsUserAccess userAccess;
	private final SnCiraCiraIssueTypeAccess issueTypeAccess;
	private final SnCiraIssueStatusAccess statusAccess;
	private final SnCiraSprintAccess sprintAccess;
	private final ObjectMapper objectMapper;

	@PersistenceContext
	private EntityManager em;

	public Page<IssueResponse> searchIssues(SearchIssueRequest request, Pageable pageable) {
		if (request.getQ() != null && !request.getQ().isBlank()) {
			return searchWithFts(request, pageable);
		}
		return searchWithSpec(request, pageable);
	}

	@Transactional
	public SavedFilterResponse saveFilter(SavedFilterRequest request) {
		String userId = currentUserId();
		SnCiraSavedFilterModel filter = SnCiraSavedFilterModel.builder()
			.userId(userId)
			.projectId(request.getProjectId())
			.filterNm(request.getFilterName())
			.jqlQuery(serializeParams(request.getFilterParams()))
			.sharedYn("N")
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("SAVED-FILTER")
			.useStatCd(UseStatCd.Usable)
			.evtNm("SaveFilter")
			.prevEvntNm("None")
			.build();
		savedFilterAccess.save(filter);
		return mapFilterToResponse(filter);
	}

	public List<SavedFilterResponse> getSavedFilters() {
		String userId = currentUserId();
		return savedFilterAccess.findByUserId(userId).stream()
			.filter(f -> UseStatCd.Usable.equals(f.getUseStatCd()))
			.map(this::mapFilterToResponse)
			.collect(Collectors.toList());
	}

	@Transactional
	public void deleteFilter(String filterId) {
		String userId = currentUserId();
		SnCiraSavedFilterModel filter = savedFilterAccess.findById(filterId);
		if (!userId.equals(filter.getUserId())) {
			throw new IllegalArgumentException("삭제 권한이 없습니다.");
		}
		filter.setUseStatCd(UseStatCd.Delete);
		filter.setEvtNm("DeleteFilter");
		filter.setPrevEvntNm("SaveFilter");
		savedFilterAccess.save(filter);
	}

	public List<AutocompleteResponse> autocomplete(String type, String keyword) {
		String kw = keyword == null ? "" : keyword.toLowerCase();
		String tenant = ApplicationProperties.getApplicationTenant();
		return switch (type.toUpperCase()) {
			case "ASSIGNEE", "USER" -> userAccess.searchByKeyword(kw).stream()
				.map(u -> AutocompleteResponse.builder()
					.id(u.getObjId()).label(u.getUserNm()).subLabel(u.getEmail()).build())
				.collect(Collectors.toList());
			case "STATUS" -> statusAccess.findAll(tenant).stream()
				.filter(s -> matchesKeyword(s.getStatusNm(), kw))
				.limit(10)
				.map(s -> AutocompleteResponse.builder()
					.id(s.getObjId()).label(s.getStatusNm()).build())
				.collect(Collectors.toList());
			case "SPRINT" -> sprintAccess.findAll(tenant).stream()
				.filter(s -> matchesKeyword(s.getSprintNm(), kw))
				.limit(10)
				.map(s -> AutocompleteResponse.builder()
					.id(s.getObjId()).label(s.getSprintNm()).build())
				.collect(Collectors.toList());
			case "ISSUE_TYPE" -> issueTypeAccess.findAll(tenant).stream()
				.filter(t -> matchesKeyword(t.getTypeNm(), kw))
				.limit(10)
				.map(t -> AutocompleteResponse.builder()
					.id(t.getObjId()).label(t.getTypeNm()).build())
				.collect(Collectors.toList());
			default -> List.of();
		};
	}

	@SuppressWarnings("unchecked")
	private Page<IssueResponse> searchWithFts(SearchIssueRequest request, Pageable pageable) {
		// Named parameters for safety
		StringBuilder whereSql = new StringBuilder(
			" WHERE i.DELETED_AT IS NULL AND i.SEARCH_VECTOR @@ plainto_tsquery('simple', :q)");

		if (request.getProjectId() != null && !request.getProjectId().isEmpty()) {
			whereSql.append(" AND i.PROJECT_ID IN :projectIds");
		}
		if (request.getStatus() != null && !request.getStatus().isEmpty()) {
			whereSql.append(" AND i.STATUS_ID IN :statusIds");
		}
		if (request.getPriority() != null) {
			whereSql.append(" AND i.PRIORITY = :priority");
		}
		if (request.getAssigneeId() != null) {
			whereSql.append(" AND i.ASSIGNEE_ID = :assigneeId");
		}
		if (request.getReporterId() != null) {
			whereSql.append(" AND i.REPORTER_ID = :reporterId");
		}
		if (request.getIssueType() != null) {
			whereSql.append(" AND i.ISSUE_TYPE_ID = :issueTypeId");
		}
		if (request.getSprintId() != null) {
			whereSql.append(" AND i.SPRINT_ID = :sprintId");
		}
		if (request.getCreatedAfter() != null) {
			whereSql.append(" AND i.CREATED_AT >= :createdAfter");
		}
		if (request.getCreatedBefore() != null) {
			whereSql.append(" AND i.CREATED_AT <= :createdBefore");
		}
		if (request.getDueDateFrom() != null) {
			whereSql.append(" AND i.DUE_DT >= :dueDateFrom");
		}
		if (request.getDueDateTo() != null) {
			whereSql.append(" AND i.DUE_DT <= :dueDateTo");
		}

		String selectSql = "SELECT i.* FROM SN_CIRA_ISSUE i" + whereSql
			+ " ORDER BY ts_rank(i.SEARCH_VECTOR, plainto_tsquery('simple', :q)) DESC";
		String countSql = "SELECT COUNT(*) FROM SN_CIRA_ISSUE i" + whereSql;

		jakarta.persistence.Query dataQuery = em.createNativeQuery(selectSql, SnCiraIssueModel.class);
		jakarta.persistence.Query countQuery = em.createNativeQuery(countSql);

		bindFtsParams(dataQuery, request);
		bindFtsParams(countQuery, request);

		dataQuery.setFirstResult((int)pageable.getOffset());
		dataQuery.setMaxResults(pageable.getPageSize());

		List<SnCiraIssueModel> results = dataQuery.getResultList();
		long total = ((Number)countQuery.getSingleResult()).longValue();

		return new PageImpl<>(
			results.stream().map(this::mapIssueToResponse).collect(Collectors.toList()),
			pageable, total);
	}

	private void bindFtsParams(jakarta.persistence.Query query, SearchIssueRequest request) {
		query.setParameter("q", request.getQ());
		if (request.getProjectId() != null && !request.getProjectId().isEmpty()) {
			query.setParameter("projectIds", request.getProjectId());
		}
		if (request.getStatus() != null && !request.getStatus().isEmpty()) {
			query.setParameter("statusIds", request.getStatus());
		}
		if (request.getPriority() != null)
			query.setParameter("priority", request.getPriority());
		if (request.getAssigneeId() != null)
			query.setParameter("assigneeId", request.getAssigneeId());
		if (request.getReporterId() != null)
			query.setParameter("reporterId", request.getReporterId());
		if (request.getIssueType() != null)
			query.setParameter("issueTypeId", request.getIssueType());
		if (request.getSprintId() != null)
			query.setParameter("sprintId", request.getSprintId());
		if (request.getCreatedAfter() != null)
			query.setParameter("createdAfter", request.getCreatedAfter());
		if (request.getCreatedBefore() != null)
			query.setParameter("createdBefore", request.getCreatedBefore());
		if (request.getDueDateFrom() != null)
			query.setParameter("dueDateFrom", request.getDueDateFrom());
		if (request.getDueDateTo() != null)
			query.setParameter("dueDateTo", request.getDueDateTo());
	}

	private Page<IssueResponse> searchWithSpec(SearchIssueRequest request, Pageable pageable) {
		Specification<SnCiraIssueModel> spec = (root, query, cb) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(cb.isNull(root.get("deletedAt")));

			if (request.getProjectId() != null && !request.getProjectId().isEmpty()) {
				predicates.add(root.get("projectId").in(request.getProjectId()));
			}
			if (request.getStatus() != null && !request.getStatus().isEmpty()) {
				predicates.add(root.get("statusId").in(request.getStatus()));
			}
			if (request.getPriority() != null) {
				predicates.add(cb.equal(root.get("priority"), request.getPriority()));
			}
			if (request.getAssigneeId() != null) {
				predicates.add(cb.equal(root.get("assigneeId"), request.getAssigneeId()));
			}
			if (request.getReporterId() != null) {
				predicates.add(cb.equal(root.get("reporterId"), request.getReporterId()));
			}
			if (request.getIssueType() != null) {
				predicates.add(cb.equal(root.get("issueTypeId"), request.getIssueType()));
			}
			if (request.getSprintId() != null) {
				predicates.add(cb.equal(root.get("sprintId"), request.getSprintId()));
			}
			if (request.getCreatedAfter() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), request.getCreatedAfter()));
			}
			if (request.getCreatedBefore() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), request.getCreatedBefore()));
			}
			if (request.getDueDateFrom() != null) {
				predicates.add(cb.greaterThanOrEqualTo(root.get("dueDt"), request.getDueDateFrom()));
			}
			if (request.getDueDateTo() != null) {
				predicates.add(cb.lessThanOrEqualTo(root.get("dueDt"), request.getDueDateTo()));
			}
			return cb.and(predicates.toArray(new Predicate[0]));
		};
		return issueAccess.findAll(spec, pageable).map(this::mapIssueToResponse);
	}

	private boolean matchesKeyword(String value, String keyword) {
		return value != null && value.toLowerCase().contains(keyword);
	}

	private IssueResponse mapIssueToResponse(SnCiraIssueModel model) {
		var reporter = userAccess.findByIdOptional(model.getReporterId()).orElse(null);
		var assignee = model.getAssigneeId() != null
			? userAccess.findByIdOptional(model.getAssigneeId()).orElse(null) : null;
		var type = issueTypeAccess.findByIdOptional(model.getIssueTypeId()).orElse(null);
		var status = statusAccess.findByIdOptional(model.getStatusId()).orElse(null);

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
				.email(reporter.getEmail()).name(reporter.getUserNm())
				.avatarUrl(reporter.getAvatarUrl()).build() : null)
			.assignee(assignee != null ? UserResponse.builder()
				.email(assignee.getEmail()).name(assignee.getUserNm())
				.avatarUrl(assignee.getAvatarUrl()).build() : null)
			.projectId(model.getProjectId())
			.sprintId(model.getSprintId())
			.dueDt(model.getDueDt())
			.createdAt(model.getCreatedAt())
			.modifiedAt(model.getModifiedAt())
			.build();
	}

	private SavedFilterResponse mapFilterToResponse(SnCiraSavedFilterModel model) {
		return SavedFilterResponse.builder()
			.id(model.getObjId())
			.userId(model.getUserId())
			.projectId(model.getProjectId())
			.filterName(model.getFilterNm())
			.filterParams(deserializeParams(model.getJqlQuery()))
			.createdAt(model.getCreatedAt())
			.build();
	}

	private String currentUserId() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
		return user.getObjId();
	}

	private String serializeParams(SearchIssueRequest params) {
		if (params == null)
			return null;
		try {
			return objectMapper.writeValueAsString(params);
		} catch (JsonProcessingException e) {
			log.warn("Failed to serialize filter params", e);
			return null;
		}
	}

	private SearchIssueRequest deserializeParams(String json) {
		if (json == null || json.isBlank())
			return null;
		try {
			return objectMapper.readValue(json, SearchIssueRequest.class);
		} catch (JsonProcessingException e) {
			log.warn("Failed to deserialize filter params", e);
			return null;
		}
	}

}
