package tsh.d94.app.server.apService.cira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IssueTypeResponse {

	private String id;
	private String typeNm;
	private String icon;
	private String colorCd;
	private String descr;

}
