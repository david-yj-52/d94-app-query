package tsh.d94.app.server.data.orm.cira.ciraIssueVersion;

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
	name = ApTableName.SN_CIRA_ISSUE_VERSION,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraIssueVersionModel.UK01, columnNames = {"ISSUE_ID", "VERSION_ID", "REL_TYPE"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraIssueVersionModel extends BaseModel {

	public static final String UK01 = "uk_ciraIssueVersion_01";

	@Column(name = "ISSUE_ID", length = 100, nullable = false)
	private String issueId;

	@Column(name = "VERSION_ID", length = 100, nullable = false)
	private String versionId;

	@Column(name = "REL_TYPE", length = 30, nullable = false)
	private String relType;

}
