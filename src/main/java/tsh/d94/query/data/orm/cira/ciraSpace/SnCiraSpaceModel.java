package tsh.d94.app.server.data.orm.cira.ciraSpace;

import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** 프로젝트 하위 작업 공간 (V650). 상태·전이·보드·필드 세트·컬럼 설정·공간 산출물 규칙의 소유자. 물리 삭제 */
@Entity
@Table(name = ApTableName.SN_CIRA_SPACE)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnCiraSpaceModel extends BaseModel {

	@Column(name = "PROJECT_ID", length = 100, nullable = false)
	private String projectId;

	@Column(name = "SPACE_NM", length = 60, nullable = false)
	private String spaceNm;

	@Column(name = "SPACE_KEY", length = 20, nullable = false)
	private String spaceKey;

	@Column(name = "DESCR", length = 500)
	private String descr;

	@Column(name = "PRESET_TYPE_ID", length = 100)
	private String presetTypeId;

	@Column(name = "ADMIN_USER_ID", length = 100, nullable = false)
	private String adminUserId;

	@Column(name = "IS_DEFAULT_YN", length = 1, nullable = false)
	private String isDefaultYn;

	@Column(name = "SORT_ORD", nullable = false)
	private Short sortOrd;

	public boolean isDefault() {
		return "Y".equals(isDefaultYn);
	}
}
