package tsh.d94.app.server.apService.cira.dto.wiki;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WikiPageSummary {

	private String id;
	private String title;
	private String parentId;
	private Integer sortOrder;
	private Integer version;
}
