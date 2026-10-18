package tsh.d94.app.server.apService.cira.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SavedFilterRequest {

	@NotBlank
	private String filterName;

	private String projectId;

	private SearchIssueRequest filterParams;

}
