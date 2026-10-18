package tsh.d94.app.server.data.orm.cira.ciraIssueCfValue;

import org.hibernate.envers.Audited;

import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
	name = ApTableName.SN_CIRA_ISSUE_CF_VALUE,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraIssueCfValueModel.UK01, columnNames = {"ISSUE_ID", "CUSTOM_FIELD_ID"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraIssueCfValueModel extends BaseModel {

	public static final String UK01 = "uk_ciraIssueCfValue_01";

	@Column(name = "ISSUE_ID", length = 100, nullable = false)
	private String issueId;

	@Column(name = "CUSTOM_FIELD_ID", length = 100, nullable = false)
	private String customFieldId;

	@Column(name = "VAL_TEXT")
	private String valText;

	@Column(name = "VAL_NUMBER")
	private java.math.BigDecimal valNumber;

	@Column(name = "VAL_DT")
	private java.time.LocalDate valDt;

	@Column(name = "VAL_JSON", columnDefinition = "jsonb")
	private String valJson;

}
