package tsh.d94.app.server.apService.cira.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

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
	/** 기본 작업 공간의 스타터 프리셋(SN_CIRA_PROJECT_TYPE.OBJ_ID). 프로젝트에는 남지 않는다 (V660). 옛 이름 projectTypeId */
	@JsonAlias("projectTypeId")
	private String presetTypeId;
	/** 프리셋 이름 (옛 클라이언트, presetTypeId 가 있으면 무시). 옛 이름 projectType */
	@JsonAlias("projectType")
	private String presetTypeNm;
	private String defaultSpaceName; // 기본 작업 공간 이름 (없으면 General). spaces 가 있으면 무시
	/** 처음 만들 작업 공간들 (첫 번째 = 기본 공간). presetTypeId null = 빈 공간. 관리자는 만든 사람. 없으면 위 두 값으로 1개 */
	private List<SpaceDto.SaveSpace> spaces;
}
