package tsh.d94.app.server.data.orm.cira.ciraCiraIssueType;

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
	name = ApTableName.SN_CIRA_CIRA_ISSUE_TYPE,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraCiraIssueTypeModel.UK01, columnNames = {"TYPE_NM"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraCiraIssueTypeModel extends BaseModel {

	public static final String UK01 = "uk_ciraCiraIssueType_01";

	@Column(name = "TYPE_NM", length = 50, nullable = false)
	private String typeNm;

	@Column(name = "ICON", length = 100)
	private String icon;

	@Column(name = "COLOR_CD", length = 7)
	private String colorCd;

	@Column(name = "DESCR", length = 255)
	private String descr;

}
