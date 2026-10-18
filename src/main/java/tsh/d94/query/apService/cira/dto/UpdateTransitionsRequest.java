package tsh.d94.app.server.apService.cira.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateTransitionsRequest {

	private List<TransitionItem> transitions;

	@Data
	@Builder
	@AllArgsConstructor
	@NoArgsConstructor
	public static class TransitionItem {
		private String fromStatusId;
		private String toStatusId;
	}
}
