package tsh.d94.app.server.apService.cira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProjectResponse {
	private String id;
	private String key;
	private String name;
	private String description;
	private String projectType; // 타입 이름 (= projectTypeNm, 옛 클라이언트용)
	private String projectTypeId;
	private String projectTypeNm;
	private String ownerId;
	private Integer issueSequence;
}
