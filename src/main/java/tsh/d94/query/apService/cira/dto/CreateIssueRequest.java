package tsh.d94.app.server.apService.cira.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateIssueRequest {
	@NotBlank(message = "title은 필수입니다.")
	private String title;
	private String content;
	private String issueTypeId;
	/** 작업 공간. null = 기본 공간 (COMMON Feature 는 무시하고 공통 레이어) */
	private String spaceId;
	/** Feature 유형만: COMMON(공통 레이어) | UNIT(공간 Feature, 기본값) */
	private String featureScope;
	/** 연결할 Feature (Task → COMMON/UNIT Feature, UNIT Feature → COMMON Feature) */
	private String featureId;
	private String statusId;
	private String priority;
	private BigDecimal storyPnt;
	private String assigneeId;
	private String sprintId;
	private LocalDate dueDt;
	/** {"cf:<id>": 값}. null·"" 은 값 삭제 (설계 5.4) */
	private java.util.Map<String, Object> customFields;
	private List<String> parentIssueIds; // For sub-tasks or links
}
