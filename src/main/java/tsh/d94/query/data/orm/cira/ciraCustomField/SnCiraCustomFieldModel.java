package tsh.d94.app.server.data.orm.cira.ciraCustomField;

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
	name = ApTableName.SN_CIRA_CUSTOM_FIELD,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraCustomFieldModel.UK01, columnNames = {"PROJECT_ID", "FIELD_NM"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraCustomFieldModel extends BaseModel {

	public static final String UK01 = "uk_ciraCustomField_01";

	@Column(name = "PROJECT_ID", length = 100)
	private String projectId;

	@Column(name = "FIELD_NM", length = 100, nullable = false)
	private String fieldNm;

	@Column(name = "FIELD_TYPE", length = 30, nullable = false)
	private String fieldType;

	@Column(name = "REQUIRED_YN", length = 1, nullable = false)
	private String requiredYn;

	@Column(name = "OPTIONS", columnDefinition = "jsonb")
	private String options;

	@Column(name = "SORT_ORD", nullable = false)
	private Short sortOrd;

}
