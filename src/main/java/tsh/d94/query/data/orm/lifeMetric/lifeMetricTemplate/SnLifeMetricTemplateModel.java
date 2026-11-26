package tsh.d94.app.server.data.orm.lifeMetric.lifeMetricTemplate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/** 기본 계산 유형. V610 시드(TENANT='*', 전 tenant 공용). ponytail: 읽기 전용 — 관리 화면 생기면 쓰기 추가. */
@Entity
@Table(name = ApTableName.SN_LIFE_METRIC_TEMPLATE)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnLifeMetricTemplateModel extends BaseModel {

	@Column(name = "CODE", length = 50, nullable = false)
	private String code;

	@Column(name = "NAME", length = 200, nullable = false)
	private String name;

	@Column(name = "INPUT_SCHEMA", columnDefinition = "text", nullable = false)
	private String inputSchema;

	@Column(name = "DEFAULT_FORMULA", columnDefinition = "text", nullable = false)
	private String defaultFormula;

	@Column(name = "OUTPUT_UNIT", length = 50)
	private String outputUnit;
}
