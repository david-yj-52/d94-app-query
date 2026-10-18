package tsh.d94.app.server.apService.cira.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MoveIssueRequest {
	@NotBlank
	private String targetColumnId;
	/** 이 이슈 뒤에 삽입 (null = 컬럼 맨 앞) */
	private String afterIssueId;
	/** 이 이슈 앞에 삽입 (null = 컬럼 맨 뒤) */
	private String beforeIssueId;
}
