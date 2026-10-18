package tsh.d94.app.server.apService.cira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CompleteSprintRequest {
	private String incompleteIssueAction; // "BACKLOG" | "NEXT_SPRINT"
	private String nextSprintId;
}
