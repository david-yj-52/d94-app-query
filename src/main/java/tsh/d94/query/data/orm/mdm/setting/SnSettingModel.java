package tsh.d94.app.server.data.orm.mdm.setting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/** 기준정보 설정 (테넌트별 키-값). 키 예: general.logo.text. ponytail: @Audited 미사용 — 변경 이력 요구 생기면 추가 */
@Entity
@Table(name = ApTableName.SN_SETTING,
	uniqueConstraints = @UniqueConstraint(name = "uk_sn_setting_01", columnNames = {"TENANT", "SETTING_KEY"}))
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnSettingModel extends BaseModel {

	@Column(name = "SETTING_KEY", length = 100, nullable = false)
	private String settingKey;

	@Column(name = "SETTING_VALUE", length = 500)
	private String settingValue;
}
