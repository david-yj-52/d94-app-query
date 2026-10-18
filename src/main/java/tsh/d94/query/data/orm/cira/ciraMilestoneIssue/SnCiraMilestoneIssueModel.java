package tsh.d94.app.server.data.orm.cira.ciraMilestoneIssue;

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
	name = ApTableName.SN_CIRA_MILESTONE_ISSUE,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraMilestoneIssueModel.UK01, columnNames = {"MILESTONE_ID", "ISSUE_ID"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraMilestoneIssueModel extends BaseModel {

	public static final String UK01 = "uk_ciraMilestoneIssue_01";

	@Column(name = "MILESTONE_ID", length = 100, nullable = false)
	private String milestoneId;

	@Column(name = "ISSUE_ID", length = 100, nullable = false)
	private String issueId;

}
