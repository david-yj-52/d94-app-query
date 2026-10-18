package tsh.d94.app.server.apService.cira.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BurndownResponse {

	private String sprintId;
	private String sprintName;
	private LocalDate startDate;
	private LocalDate endDate;
	private BigDecimal totalPoints;
	private List<BurndownPoint> idealBurndown;
	private List<BurndownPoint> actualBurndown;

	@Getter
	@Builder
	public static class BurndownPoint {
		private LocalDate date;
		private BigDecimal remainingPoints;
	}

}
