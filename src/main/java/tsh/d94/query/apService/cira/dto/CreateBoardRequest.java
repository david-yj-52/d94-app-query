package tsh.d94.app.server.apService.cira.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateBoardRequest {
	@NotBlank
	private String boardNm;
	private String spaceId; // null = 기본 공간
	private String boardType = "KANBAN";
}
