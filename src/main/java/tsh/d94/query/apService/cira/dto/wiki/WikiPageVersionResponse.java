package tsh.d94.app.server.apService.cira.dto.wiki;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WikiPageVersionResponse {

	private String id;
	private Integer version;
	private String editedBy;
	private OffsetDateTime editedAt;
}
