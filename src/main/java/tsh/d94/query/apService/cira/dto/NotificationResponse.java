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
	private boolean read;
	private LocalDateTime createdAt;

}
