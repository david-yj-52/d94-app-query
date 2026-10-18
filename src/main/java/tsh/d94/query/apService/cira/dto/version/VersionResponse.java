package tsh.d94.app.server.apService.cira.dto.version;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VersionResponse {

	private String id;
	private String projectId;
	private String versionName;
	private String description;
	private String status;
	private LocalDate plannedReleaseDate;
	private OffsetDateTime releasedAt;
}
