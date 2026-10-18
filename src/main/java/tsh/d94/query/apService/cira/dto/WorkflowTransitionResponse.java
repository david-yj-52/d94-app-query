package tsh.d94.app.server.apService.cira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowTransitionResponse {
	private String id;
	private String fromStatusId;
	private String toStatusId;
	private String allowYn;
}
