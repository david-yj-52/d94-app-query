package tsh.d94.app.server.apService.cira.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateIssueRequest {
	private String title;
	private String content;
	private String issueTypeId;
	private String statusId;
	private String priority;
	private BigDecimal storyPnt;
	private String assigneeId;
	private String sprintId;
	private LocalDate dueDt;
	private List<String> parentIssueIds; // For sub-tasks or links
}
