package tsh.d94.app.server.apService.cira.dto.wiki;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WikiPageResponse {

	private String id;
	private String title;
	private String content;
	private String contentHtml;
	private String parentId;
	private String authorId;
	private Integer sortOrder;
	private Integer version;
	private List<WikiPageResponse> children;
}
