package tsh.d94.app.server.apService.cira.dto.milestone;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MilestoneProgressResponse {

	private String milestoneId;
	private int total;
	private int completed;
	private double percentage;
}
