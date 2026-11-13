package tsh.d94.app.server.data.orm.ayaan.aynTool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.app.server.data.orm.ayaan.AynRefBase;

@Entity
@Table(name = ApTableName.SN_AYN_TOOL)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynToolModel extends AynRefBase {

	@Id
	@Column(name = "TOOL_CD", length = 20)
	private String toolCd;

	@Column(name = "ENABLED", nullable = false)
	private Boolean enabled;

	@Column(name = "AUTH_MODE", length = 20, nullable = false)
	private String authMode;

	@Column(name = "PLAN_NAME", length = 100)
	private String planName;

	@Column(name = "API_KEY_REF", length = 100)
	private String apiKeyRef;

	@Column(name = "MODEL", length = 100)
	private String model;

	@Column(name = "MAX_CONCURRENCY", nullable = false)
	private Integer maxConcurrency;

	@Column(name = "FALLBACK_RESET_RULE", length = 100)
	private String fallbackResetRule;

	@Column(name = "BUDGET_TOKENS")
	private Long budgetTokens;

	@Column(name = "BUDGET_PERIOD", length = 10)
	private String budgetPeriod;

	@Column(name = "PLAN_SKILL_CD", length = 50)
	private String planSkillCd;

	@Column(name = "DEV_SKILL_CD", length = 50)
	private String devSkillCd;

}
