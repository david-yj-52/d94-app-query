package tsh.d94.app.server.apService.cira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateProjectRequest {
	private String key;
	private String name;
	private String description;
	private String projectTypeId; // SN_CIRA_PROJECT_TYPE.OBJ_ID (기준정보)
	private String projectType; // 옛 클라이언트용 타입 이름. projectTypeId 가 있으면 무시
}
