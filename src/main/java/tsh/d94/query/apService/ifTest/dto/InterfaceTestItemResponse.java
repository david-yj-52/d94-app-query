package tsh.d94.app.server.apService.ifTest.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterfaceTestItemResponse {

	private String id;
	private String name;
	private String content;
	private String status;
	private String lastEvent;
	private LocalDateTime createdAt;
	private LocalDateTime modifiedAt;
}
