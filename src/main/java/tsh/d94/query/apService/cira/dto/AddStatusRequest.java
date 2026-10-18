package tsh.d94.app.server.apService.cira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AddStatusRequest {
	private String statusNm;
	private String category; // TODO | IN_PROGRESS | DONE
	private String colorCd;
	private Short sortOrd;
}
