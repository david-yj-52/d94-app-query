package tsh.d94.app.server.apService.cira.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SprintResponse {
	private String id;
	private String projectId;
	private String sprintNm;
	private String goal;
	private LocalDate startDt;
	private LocalDate endDt;
	private String sprintStat;
	private LocalDateTime createdAt;
	private LocalDateTime modifiedAt;
}
