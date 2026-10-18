package tsh.d94.app.server.apService.cira.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IssueStatusResponse {

	private String id;
	private String statusNm;
	private String category;
	private String colorCd;
	private Short sortOrd;
}
