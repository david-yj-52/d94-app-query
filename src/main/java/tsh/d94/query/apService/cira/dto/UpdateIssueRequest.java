package tsh.d94.app.server.apService.cira.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateIssueRequest {
	private String title;
	/** Task 설명 = 연결된 Docs 메인 노트 본문. 주면 메인 노트 본문(frontmatter·진행 경과 제외)을 바꾼다 (V630) */
	private String content;
	/** 연결할 Feature. "" = 연결 해제 */
	private String featureId;
	private String statusId;
	private String priority;
	private BigDecimal storyPnt;
	private String assigneeId;
	private String sprintId;
	private LocalDate dueDt;
	/** {"cf:<id>": 값}. null·"" 은 값 삭제 (설계 5.4) */
	private java.util.Map<String, Object> customFields;
}
