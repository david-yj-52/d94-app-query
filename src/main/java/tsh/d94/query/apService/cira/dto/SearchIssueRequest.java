package tsh.d94.app.server.apService.cira.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class SearchIssueRequest {

	private String q;
	private List<String> projectId;
	private List<String> status;
	private String priority;
	private String assigneeId;
	private String reporterId;
	private String issueType;
	private String sprintId;
	private LocalDateTime createdAfter;
	private LocalDateTime createdBefore;
	private LocalDate dueDateFrom;
	private LocalDate dueDateTo;

}
