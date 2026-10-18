package tsh.d94.app.server.apService.cira.dto.version;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VersionRequest {

	private String versionNm;
	private String descr;
	private String status;
	private LocalDate planRelDt;

}
