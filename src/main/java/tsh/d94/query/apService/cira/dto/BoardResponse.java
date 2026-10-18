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
public class BoardResponse {
	private String id;
	private String projectId;
	private String boardNm;
	private String boardType;
	private int columnCount;
	private LocalDateTime createdAt;
	private LocalDateTime modifiedAt;
}
