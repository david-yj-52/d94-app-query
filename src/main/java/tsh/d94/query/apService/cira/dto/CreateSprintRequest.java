package tsh.d94.app.server.apService.cira.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateSprintRequest {
	private String sprintNm;
	private String goal;
	private LocalDate startDt;
	private LocalDate endDt;
}
