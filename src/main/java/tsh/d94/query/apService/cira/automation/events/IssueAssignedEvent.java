package tsh.d94.app.server.apService.cira.automation.events;

import org.springframework.context.ApplicationEvent;

public class IssueAssignedEvent extends ApplicationEvent {

	private final String issueId;
	private final String projectId;
	private final String assigneeId;
	private final String assignedBy;

	public IssueAssignedEvent(Object source, String issueId, String projectId,
		String assigneeId, String assignedBy) {
		super(source);
		this.issueId = issueId;
		this.projectId = projectId;
		this.assigneeId = assigneeId;
		this.assignedBy = assignedBy;
	}

	public String getIssueId() {
		return issueId;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getAssigneeId() {
		return assigneeId;
	}

	public String getAssignedBy() {
		return assignedBy;
	}

}
