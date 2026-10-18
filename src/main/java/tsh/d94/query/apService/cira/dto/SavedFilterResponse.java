package tsh.d94.app.server.apService.cira.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SavedFilterResponse {

	private String id;
	private String userId;
	private String projectId;
	private String filterName;
	private SearchIssueRequest filterParams;
	private LocalDateTime createdAt;

}
