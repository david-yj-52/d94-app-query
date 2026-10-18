package tsh.d94.app.server.apService.cira.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VelocityItemResponse {

	private String sprintId;
	private String sprintName;
	private BigDecimal committed;
	private BigDecimal completed;
	private BigDecimal velocity;

}
