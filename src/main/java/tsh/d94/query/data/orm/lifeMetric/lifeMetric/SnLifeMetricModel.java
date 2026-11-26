package tsh.d94.app.server.data.orm.lifeMetric.lifeMetric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/**
 * 사용자 지표. CODE 는 소유자 안에서 유일하고 다른 지표 수식에서 @code 로 참조한다.
 * ponytail: @Audited 미사용, 물리 삭제. 컬럼 길이는 V610 과 짝.
 */
@Entity
@Table(name = ApTableName.SN_LIFE_METRIC)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnLifeMetricModel extends BaseModel {

	@Column(name = "OWNER_ID", length = 200, nullable = false)
	private String ownerId;

	@Column(name = "TEMPLATE_ID")
	private String templateId;

	@Column(name = "CODE", length = 50, nullable = false)
	private String code;

	@Column(name = "NAME", length = 200, nullable = false)
	private String name;

	@Column(name = "INPUT_SCHEMA", columnDefinition = "text", nullable = false)
	private String inputSchema;

	@Column(name = "FORMULA", columnDefinition = "text", nullable = false)
	private String formula;

	@Column(name = "OUTPUT_UNIT", length = 50)
	private String outputUnit;
}
