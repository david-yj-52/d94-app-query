package tsh.d94.app.server.apService.cira;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.BurndownResponse;
import tsh.d94.app.server.apService.cira.dto.CfdResponse;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectStatsResponse;
import tsh.d94.app.server.apService.cira.dto.UserDashboardResponse;
import tsh.d94.app.server.apService.cira.dto.VelocityItemResponse;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueLog.SnCiraIssueLogAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueLog.SnCiraIssueLogModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintAccess;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintModel;
import tsh.d94.app.server.data.orm.cira.ciraSprintMetrics.SnCiraSprintMetricsAccess;
import tsh.d94.app.server.data.orm.cira.ciraSprintMetrics.SnCiraSprintMetricsModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

	private final SnCiraSprintAccess sprintAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraIssueLogAccess issueLogAccess;
	private final SnCiraIssueStatusAccess statusAccess;
	private final SnCiraSprintMetricsAccess sprintMetricsAccess;
	private final GsUserAccess userAccess;
	private final IssueService issueService;

	@Cacheable(value = "burndown", key = "#sprintId")
	@Transactional(readOnly = true)
	public BurndownResponse getBurndown(String sprintId) {
		SnCiraSprintModel sprint = sprintAccess.findById(sprintId);
		List<SnCiraIssueModel> issues = issueAccess.findBySprintId(sprintId);

		String projectId = sprint.getProjectId();
		Set<String> doneStatusIds = statusAccess.findDoneStatusIdsByProject(projectId);

		BigDecimal totalPoints = issues.stream()
			.map(i -> i.getStoryPnt() != null ? i.getStoryPnt() : BigDecimal.ZERO)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		List<String> issueIds = issues.stream().map(SnCiraIssueModel::getObjId).collect(Collectors.toList());
		List<SnCiraIssueLogModel> statusLogs = issueIds.isEmpty()
			? List.of()
			: issueLogAccess.findStatusLogsByIssueIds(issueIds);

		Map<String, LocalDate> issueCompletedDate = new HashMap<>();
		for (SnCiraIssueLogModel log : statusLogs) {
			if (doneStatusIds.contains(log.getNewVal())) {
				LocalDate completedDate = log.getChangedAt().toLocalDate();
				issueCompletedDate.merge(log.getIssueId(), completedDate,
					(existing, newDate) -> existing.isBefore(newDate) ? existing : newDate);
			}
		}

		LocalDate start = sprint.getStartDt() != null ? sprint.getStartDt() : LocalDate.now();
		LocalDate end = sprint.getEndDt() != null ? sprint.getEndDt() : LocalDate.now().plusDays(14);
		LocalDate today = LocalDate.now();

		List<BurndownResponse.BurndownPoint> ideal = buildIdealBurndown(start, end, totalPoints);
		List<BurndownResponse.BurndownPoint> actual = buildActualBurndown(start, end.isBefore(today) ? end : today,
			issues, issueCompletedDate, totalPoints);

		return BurndownResponse.builder()
			.sprintId(sprintId)
			.sprintName(sprint.getSprintNm())
			.startDate(start)
			.endDate(end)
			.totalPoints(totalPoints)
			.idealBurndown(ideal)
			.actualBurndown(actual)
			.build();
	}

	@Cacheable(value = "velocity", key = "#projectId + '-' + #lastN")
	@Transactional(readOnly = true)
	public List<VelocityItemResponse> getVelocity(String projectId, int lastN) {
		List<SnCiraSprintModel> sprints = sprintAccess.findByProjectIdAndSprintStat(projectId, "Completed");
		sprints.sort(Comparator.comparing(s -> s.getEndDt() != null ? s.getEndDt() : LocalDate.MIN));
		int fromIndex = Math.max(0, sprints.size() - lastN);
		List<SnCiraSprintModel> recentSprints = sprints.subList(fromIndex, sprints.size());

		List<String> sprintIds = recentSprints.stream().map(SnCiraSprintModel::getObjId).collect(Collectors.toList());
		Map<String, SnCiraSprintMetricsModel> metricsMap = sprintMetricsAccess.findBySprintIdIn(sprintIds)
			.stream().collect(Collectors.toMap(SnCiraSprintMetricsModel::getSprintId, m -> m));

		return recentSprints.stream().map(sprint -> {
			SnCiraSprintMetricsModel metrics = metricsMap.get(sprint.getObjId());
			if (metrics == null) {
				metrics = computeSprintMetrics(sprint);
			}
			return VelocityItemResponse.builder()
				.sprintId(sprint.getObjId())
				.sprintName(sprint.getSprintNm())
				.committed(metrics.getPlanStoryPnt())
				.completed(metrics.getComplStoryPnt())
				.velocity(metrics.getVelocity())
				.build();
		}).collect(Collectors.toList());
	}

	@Cacheable(value = "cfd", key = "#projectId + '-' + #startDate + '-' + #endDate")
	@Transactional(readOnly = true)
	public CfdResponse getCfd(String projectId, LocalDate startDate, LocalDate endDate) {
		List<SnCiraIssueStatusModel> statuses = statusAccess.findAllByProject(projectId);
		List<SnCiraIssueModel> issues = issueAccess.findByProjectId(projectId);
		List<String> issueIds = issues.stream().map(SnCiraIssueModel::getObjId).collect(Collectors.toList());

		List<SnCiraIssueLogModel> statusLogs = issueIds.isEmpty()
			? List.of()
			: issueLogAccess.findStatusLogsByIssueIds(issueIds);

		List<LocalDate> dates = startDate.datesUntil(endDate.plusDays(1)).collect(Collectors.toList());
		List<String> statusNames = statuses.stream()
			.map(SnCiraIssueStatusModel::getStatusNm)
			.collect(Collectors.toList());
		Map<String, String> statusIdToName = statuses.stream()
			.collect(Collectors.toMap(SnCiraIssueStatusModel::getObjId, SnCiraIssueStatusModel::getStatusNm));

		Map<String, Map<LocalDate, String>> issueStatusAtDate = buildIssueStatusTimeline(issues, statusLogs,
			statusIdToName);

		Map<String, List<Integer>> data = new LinkedHashMap<>();
		for (String statusName : statusNames) {
			data.put(statusName, new ArrayList<>());
		}

		for (LocalDate date : dates) {
			Map<String, Integer> countByStatus = new HashMap<>();
			statusNames.forEach(s -> countByStatus.put(s, 0));

			for (SnCiraIssueModel issue : issues) {
				if (issue.getCreatedAt() != null && issue.getCreatedAt().toLocalDate().isAfter(date)) {
					continue;
				}
				String statusName = resolveStatusAtDate(issueStatusAtDate.get(issue.getObjId()), date,
					statusIdToName.get(issue.getStatusId()));
				if (statusName != null) {
					countByStatus.merge(statusName, 1, Integer::sum);
				}
			}

			for (String statusName : statusNames) {
				data.get(statusName).add(countByStatus.getOrDefault(statusName, 0));
			}
		}

		return CfdResponse.builder()
			.dates(dates)
			.statuses(statusNames)
			.data(data)
			.build();
	}

	@Cacheable(value = "userDashboard", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
	@Transactional(readOnly = true)
	public UserDashboardResponse getUserDashboard() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		List<SnCiraIssueModel> assigned = issueAccess.findByAssigneeId(user.getObjId());
		List<IssueResponse> assignedResponses = assigned.stream()
			.map(i -> issueService.getIssue(i.getObjId()))
			.collect(Collectors.toList());

		LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
		List<SnCiraIssueLogModel> recentLogs = issueLogAccess.findRecentActivityByUser(user.getObjId(), sevenDaysAgo);

		Map<String, SnCiraIssueModel> issueCache = assigned.stream()
			.collect(Collectors.toMap(SnCiraIssueModel::getObjId, i -> i, (a, b) -> a));

		List<UserDashboardResponse.ActivityItem> activities = recentLogs.stream()
			.map(log -> {
				SnCiraIssueModel issue = issueCache.computeIfAbsent(log.getIssueId(),
					id -> issueAccess.findByIdOptional(id).orElse(null));
				return UserDashboardResponse.ActivityItem.builder()
					.issueId(log.getIssueId())
					.issueKey(issue != null ? issue.getIssueKey() : null)
					.fieldName(log.getFieldNm())
					.oldValue(log.getOldVal())
					.newValue(log.getNewVal())
					.changedAt(log.getChangedAt().format(DateTimeFormatter.ISO_DATE_TIME))
					.build();
			})
			.collect(Collectors.toList());

		LocalDate upcomingDeadline = LocalDate.now().plusDays(7);
		List<IssueResponse> upcoming = issueAccess.findUpcomingDeadlines(user.getObjId(), upcomingDeadline)
			.stream().map(i -> issueService.getIssue(i.getObjId())).collect(Collectors.toList());

		UserDashboardResponse.SprintProgressItem sprintProgress = resolveActiveSprintProgress(user.getObjId(),
			assigned);

		return UserDashboardResponse.builder()
			.assignedIssues(assignedResponses)
			.recentActivity(activities)
			.upcomingDeadlines(upcoming)
			.sprintProgress(sprintProgress)
			.build();
	}

	@Cacheable(value = "projectStats", key = "#projectId")
	@Transactional(readOnly = true)
	public ProjectStatsResponse getProjectStats(String projectId) {
		List<SnCiraIssueModel> issues = issueAccess.findByProjectId(projectId);
		List<SnCiraIssueStatusModel> statuses = statusAccess.findAllByProject(projectId);

		Set<String> doneStatusIds = statuses.stream()
			.filter(s -> "DONE".equals(s.getCategory()))
			.map(SnCiraIssueStatusModel::getObjId)
			.collect(Collectors.toSet());

		Set<String> inProgressStatusIds = statuses.stream()
			.filter(s -> "IN_PROGRESS".equals(s.getCategory()))
			.map(SnCiraIssueStatusModel::getObjId)
			.collect(Collectors.toSet());

		long total = issues.size();
		long closed = issues.stream().filter(i -> doneStatusIds.contains(i.getStatusId())).count();
		long inProgress = issues.stream().filter(i -> inProgressStatusIds.contains(i.getStatusId())).count();
		long open = total - closed - inProgress;

		Map<String, Long> byType = issues.stream()
			.collect(Collectors.groupingBy(
				i -> i.getIssueTypeId() != null ? i.getIssueTypeId() : "UNKNOWN",
				Collectors.counting()
			));

		Map<String, Long> byPriority = issues.stream()
			.collect(Collectors.groupingBy(
				i -> i.getPriority() != null ? i.getPriority() : "UNKNOWN",
				Collectors.counting()
			));

		Map<String, Long> byAssignee = issues.stream()
			.filter(i -> i.getAssigneeId() != null)
			.collect(Collectors.groupingBy(SnCiraIssueModel::getAssigneeId, Collectors.counting()));

		return ProjectStatsResponse.builder()
			.projectId(projectId)
			.totalIssues(total)
			.openIssues(open)
			.inProgressIssues(inProgress)
			.closedIssues(closed)
			.issuesByType(byType)
			.issuesByPriority(byPriority)
			.issuesByAssignee(byAssignee)
			.build();
	}

	private List<BurndownResponse.BurndownPoint> buildIdealBurndown(
		LocalDate start, LocalDate end, BigDecimal totalPoints
	) {
		List<LocalDate> dates = start.datesUntil(end.plusDays(1)).collect(Collectors.toList());
		int totalDays = dates.size() - 1;
		if (totalDays <= 0) {
			return List.of(BurndownResponse.BurndownPoint.builder().date(start).remainingPoints(totalPoints).build());
		}
		List<BurndownResponse.BurndownPoint> points = new ArrayList<>();
		for (int i = 0; i < dates.size(); i++) {
			BigDecimal remaining = totalPoints.multiply(
				BigDecimal.valueOf(totalDays - i)).divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
			points.add(BurndownResponse.BurndownPoint.builder().date(dates.get(i)).remainingPoints(remaining).build());
		}
		return points;
	}

	private List<BurndownResponse.BurndownPoint> buildActualBurndown(
		LocalDate start, LocalDate end,
		List<SnCiraIssueModel> issues,
		Map<String, LocalDate> issueCompletedDate,
		BigDecimal totalPoints
	) {
		List<LocalDate> dates = start.datesUntil(end.plusDays(1)).collect(Collectors.toList());
		List<BurndownResponse.BurndownPoint> points = new ArrayList<>();
		BigDecimal remaining = totalPoints;

		for (LocalDate date : dates) {
			for (SnCiraIssueModel issue : issues) {
				LocalDate completed = issueCompletedDate.get(issue.getObjId());
				if (completed != null && completed.equals(date) && issue.getStoryPnt() != null) {
					remaining = remaining.subtract(issue.getStoryPnt());
				}
			}
			points.add(BurndownResponse.BurndownPoint.builder()
				.date(date)
				.remainingPoints(remaining.max(BigDecimal.ZERO))
				.build());
		}
		return points;
	}

	private SnCiraSprintMetricsModel computeSprintMetrics(SnCiraSprintModel sprint) {
		List<SnCiraIssueModel> issues = issueAccess.findBySprintId(sprint.getObjId());
		String projectId = sprint.getProjectId();
		Set<String> doneStatusIds = statusAccess.findDoneStatusIdsByProject(projectId);

		BigDecimal planned = issues.stream()
			.map(i -> i.getStoryPnt() != null ? i.getStoryPnt() : BigDecimal.ZERO)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		BigDecimal completed = issues.stream()
			.filter(i -> doneStatusIds.contains(i.getStatusId()))
			.map(i -> i.getStoryPnt() != null ? i.getStoryPnt() : BigDecimal.ZERO)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		SnCiraSprintMetricsModel metrics = SnCiraSprintMetricsModel.builder()
			.sprintId(sprint.getObjId())
			.planStoryPnt(planned)
			.complStoryPnt(completed)
			.velocity(completed)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("COMPUTE-METRICS")
			.useStatCd(UseStatCd.Usable)
			.evtNm("ComputeSprintMetrics")
			.prevEvntNm("None")
			.build();

		try {
			return sprintMetricsAccess.save(metrics);
		} catch (Exception e) {
			log.warn("Failed to persist sprint metrics for sprint {}: {}", sprint.getObjId(), e.getMessage());
			return metrics;
		}
	}

	private Map<String, Map<LocalDate, String>> buildIssueStatusTimeline(
		List<SnCiraIssueModel> issues,
		List<SnCiraIssueLogModel> statusLogs,
		Map<String, String> statusIdToName
	) {
		Map<String, List<SnCiraIssueLogModel>> logsByIssue = statusLogs.stream()
			.collect(Collectors.groupingBy(SnCiraIssueLogModel::getIssueId));

		Map<String, Map<LocalDate, String>> result = new HashMap<>();
		for (SnCiraIssueModel issue : issues) {
			List<SnCiraIssueLogModel> logs = logsByIssue.getOrDefault(issue.getObjId(), List.of());
			Map<LocalDate, String> timeline = new LinkedHashMap<>();
			for (SnCiraIssueLogModel log : logs) {
				String statusName = statusIdToName.get(log.getNewVal());
				if (statusName != null) {
					timeline.put(log.getChangedAt().toLocalDate(), statusName);
				}
			}
			result.put(issue.getObjId(), timeline);
		}
		return result;
	}

	private String resolveStatusAtDate(Map<LocalDate, String> timeline, LocalDate date, String currentStatus) {
		if (timeline == null || timeline.isEmpty()) {
			return currentStatus;
		}
		String latest = null;
		for (Map.Entry<LocalDate, String> entry : timeline.entrySet()) {
			if (!entry.getKey().isAfter(date)) {
				latest = entry.getValue();
			}
		}
		return latest != null ? latest : currentStatus;
	}

	private UserDashboardResponse.SprintProgressItem resolveActiveSprintProgress(
		String userId, List<SnCiraIssueModel> assignedIssues
	) {
		if (assignedIssues.isEmpty())
			return null;

		String sprintId = assignedIssues.stream()
			.filter(i -> i.getSprintId() != null)
			.map(SnCiraIssueModel::getSprintId)
			.findFirst().orElse(null);

		if (sprintId == null)
			return null;

		SnCiraSprintModel sprint = sprintAccess.findByIdOptional(sprintId).orElse(null);
		if (sprint == null)
			return null;

		List<SnCiraIssueModel> sprintIssues = issueAccess.findBySprintId(sprintId);
		Set<String> doneIds = statusAccess.findDoneStatusIdsByProject(sprint.getProjectId());

		long done = sprintIssues.stream().filter(i -> doneIds.contains(i.getStatusId())).count();
		int total = sprintIssues.size();
		double pct = total > 0 ? (done * 100.0 / total) : 0.0;

		return UserDashboardResponse.SprintProgressItem.builder()
			.sprintId(sprintId)
			.sprintName(sprint.getSprintNm())
			.totalIssues(total)
			.doneIssues((int)done)
			.progressPercent(pct)
			.build();
	}

}
