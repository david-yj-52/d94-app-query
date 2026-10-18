package tsh.d94.app.server.data.orm.cira.ciraIssueLink;

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
	name = ApTableName.SN_CIRA_ISSUE_LINK,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraIssueLinkModel.UK01, columnNames = {"SRC_ISSUE_ID", "TGT_ISSUE_ID", "LINK_TYPE"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraIssueLinkModel extends BaseModel {

	public static final String UK01 = "uk_ciraIssueLink_01";

	@Column(name = "SRC_ISSUE_ID", length = 100, nullable = false)
	private String srcIssueId;

	@Column(name = "TGT_ISSUE_ID", length = 100, nullable = false)
	private String tgtIssueId;

	@Column(name = "LINK_TYPE", length = 50, nullable = false)
	private String linkType;

}
