package tsh.d94.app.server.apService.cira.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponse {

	private String id;
	private String userId;
	private String type;
	private String title;
	private String message;
	private String resourceType;
	private String resourceId;
	/** resourceType 이 ISSUE 일 때 이슈 키 (예: CIRA-15). 이슈가 없으면 null */
	private String issueKey;
	private boolean read;
	private LocalDateTime createdAt;

}
