package tsh.d94.app.server.apService.cira.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import tsh.d94.lib.core.apService.auth.dto.UserResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IssueResponse {
	private String id;
	private String issueKey;
	private String title;
	private String content;
	private String issueTypeId;
	private String issueTypeNm;
	private String statusId;
	private String statusNm;
	private String priority;
	private BigDecimal storyPnt;
	private UserResponse assignee;
	private UserResponse reporter;
	private String projectId;
	/** null = 공통 레이어 (COMMON Feature) */
	private String spaceId;
	private String featureScope;
	private String featureId;
	/** 단건 조회만: 연결된 Docs 메인 노트 (content 는 그 본문) */
	private String mainNoteId;
	private String sprintId;
	private LocalDate dueDt;
	private LocalDateTime startedAt;
	private LocalDateTime resolvedAt;
	/** {"cf:<id>": 값}. 값이 있는 필드만 */
	@Builder.Default
	private java.util.Map<String, Object> customFields = new java.util.HashMap<>();
	private LocalDateTime createdAt;
	private LocalDateTime modifiedAt;
}
