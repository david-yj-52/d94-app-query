package tsh.d94.app.server.apService.cira.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IssueFilterRequest {
	private List<String> status;
	private String priority;
	private String assigneeId;
	private String issueType;
	private String sprintId;
	private String keyword;
}
