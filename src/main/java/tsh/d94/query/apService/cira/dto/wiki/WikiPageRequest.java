package tsh.d94.app.server.apService.cira.dto.wiki;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WikiPageRequest {

	@NotBlank(message = "title은 필수입니다.")
	private String title;

	private String content;

	private String parentId;

	private Integer sortOrder;
}
