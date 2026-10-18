package tsh.d94.app.server.apService.cira.automation.events;

import org.springframework.context.ApplicationEvent;

public class IssueCreatedEvent extends ApplicationEvent {

	private final String issueId;
	private final String projectId;
	private final String createdBy;

	public IssueCreatedEvent(Object source, String issueId, String projectId, String createdBy) {
		super(source);
		this.issueId = issueId;
		this.projectId = projectId;
		this.createdBy = createdBy;
	}

	public String getIssueId() {
		return issueId;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getCreatedBy() {
		return createdBy;
	}

}
