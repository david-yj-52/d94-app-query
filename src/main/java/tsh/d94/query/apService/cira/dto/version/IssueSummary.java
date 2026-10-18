package tsh.d94.app.server.apService.cira.dto.version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IssueSummary {

	private String id;
	private String issueKey;
	private String title;
	private String issueTypeNm;
}
