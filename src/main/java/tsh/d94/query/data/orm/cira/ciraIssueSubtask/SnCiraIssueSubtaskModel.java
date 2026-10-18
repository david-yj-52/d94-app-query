package tsh.d94.app.server.data.orm.cira.ciraIssueSubtask;

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
	name = ApTableName.SN_CIRA_ISSUE_SUBTASK,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraIssueSubtaskModel.UK01, columnNames = {"PARENT_ISSUE_ID", "CHILD_ISSUE_ID"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraIssueSubtaskModel extends BaseModel {

	public static final String UK01 = "uk_ciraIssueSubtask_01";

	@Column(name = "PARENT_ISSUE_ID", length = 100, nullable = false)
	private String parentIssueId;

	@Column(name = "CHILD_ISSUE_ID", length = 100, nullable = false)
	private String childIssueId;

	@Column(name = "SORT_ORD", nullable = false)
	private Short sortOrd;

}
