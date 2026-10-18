package tsh.d94.app.server.apService.cira.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProjectStatsResponse {

	private String projectId;
	private long totalIssues;
	private long openIssues;
	private long inProgressIssues;
	private long closedIssues;
	private Map<String, Long> issuesByType;
	private Map<String, Long> issuesByPriority;
	private Map<String, Long> issuesByAssignee;

}
