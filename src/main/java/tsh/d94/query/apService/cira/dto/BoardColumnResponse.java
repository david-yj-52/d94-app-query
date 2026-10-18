package tsh.d94.app.server.apService.cira.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BoardColumnResponse {
	private String id;
	private String boardId;
	private String statusId;
	private String statusNm;
	private String statusCategory;
	private String colorCd;
	private String columnNm;
	private Short wipLimit;
	private short sortOrd;
	private List<BoardIssueResponse> issues;
}
