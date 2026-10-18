package tsh.d94.app.server.data.orm.cira.ciraIssuePosition;

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
	name = ApTableName.SN_CIRA_ISSUE_POSITION,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraIssuePositionModel.UK01, columnNames = {"ISSUE_ID", "COLUMN_ID"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraIssuePositionModel extends BaseModel {

	public static final String UK01 = "uk_ciraIssuePosition_01";

	@Column(name = "ISSUE_ID", length = 100, nullable = false)
	private String issueId;

	@Column(name = "COLUMN_ID", length = 100, nullable = false)
	private String columnId;

	@Column(name = "RANK_STR", length = 100, nullable = false)
	private String rankStr;

}
