package tsh.d94.app.server.apService.cira.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserDashboardResponse {

	private List<IssueResponse> assignedIssues;
	private List<ActivityItem> recentActivity;
	private List<IssueResponse> upcomingDeadlines;
	private SprintProgressItem sprintProgress;

	@Getter
	@Builder
	public static class ActivityItem {
		private String issueId;
		private String issueKey;
		private String fieldName;
		private String oldValue;
		private String newValue;
		private String changedAt;
	}

	@Getter
	@Builder
	public static class SprintProgressItem {
		private String sprintId;
		private String sprintName;
		private int totalIssues;
		private int doneIssues;
		private double progressPercent;
	}

}
