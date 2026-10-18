package tsh.d94.app.server.apService.cira.dto.version;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReleaseNoteGroup {

	private String category;
	private List<IssueSummary> issues;
}
