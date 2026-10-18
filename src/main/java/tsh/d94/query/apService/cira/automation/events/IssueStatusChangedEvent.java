package tsh.d94.app.server.apService.cira.automation.events;

import org.springframework.context.ApplicationEvent;

public class IssueStatusChangedEvent extends ApplicationEvent {

	private final String issueId;
	private final String projectId;
	private final String fromStatus;
	private final String toStatus;
	private final String changedBy;

	public IssueStatusChangedEvent(Object source, String issueId, String projectId,
		String fromStatus, String toStatus, String changedBy) {
		super(source);
		this.issueId = issueId;
		this.projectId = projectId;
		this.fromStatus = fromStatus;
		this.toStatus = toStatus;
		this.changedBy = changedBy;
	}

	public String getIssueId() {
		return issueId;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getFromStatus() {
		return fromStatus;
	}

	public String getToStatus() {
		return toStatus;
	}

	public String getChangedBy() {
		return changedBy;
	}

}
