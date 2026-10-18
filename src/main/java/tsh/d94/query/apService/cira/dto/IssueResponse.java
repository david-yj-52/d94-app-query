package tsh.d94.app.server.apService.cira.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import tsh.d94.lib.core.apService.auth.dto.UserResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IssueResponse {
	private String id;
	private String issueKey;
	private String title;
	private String content;
	private String issueTypeId;
	private String issueTypeNm;
	private String statusId;
	private String statusNm;
	private String priority;
	private BigDecimal storyPnt;
	private UserResponse assignee;
	private UserResponse reporter;
	private String projectId;
	private String sprintId;
	private LocalDate dueDt;
	private LocalDateTime createdAt;
	private LocalDateTime modifiedAt;
}
