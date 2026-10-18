package tsh.d94.app.server.apService.cira.dto.milestone;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MilestoneRequest {

	private String name;
	private String description;
	private LocalDate dueDate;
	private String status;
}
