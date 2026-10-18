package tsh.d94.app.server.data.orm.cira.ciraAutoRule;

import org.hibernate.envers.Audited;

import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = ApTableName.SN_CIRA_AUTO_RULE)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraAutoRuleModel extends BaseModel {

	@Column(name = "PROJECT_ID", length = 100, nullable = false)
	private String projectId;

	@Column(name = "RULE_NM", length = 200, nullable = false)
	private String ruleNm;

	@Column(name = "TRIGGER_TYPE", length = 100, nullable = false)
	private String triggerType;

	@Column(name = "TRIGGER_CONFIG", columnDefinition = "jsonb")
	private String triggerConfig;

	@Column(name = "COND_CONFIG", columnDefinition = "jsonb")
	private String condConfig;

	@Column(name = "ACTION_TYPE", length = 100, nullable = false)
	private String actionType;

	@Column(name = "ACTION_CONFIG", columnDefinition = "jsonb")
	private String actionConfig;

	@Column(name = "IS_ACTIVE", nullable = false)
	private Boolean isActive = true;

	@Column(name = "SORT_ORD", nullable = false)
	private Integer sortOrd = 0;

}
