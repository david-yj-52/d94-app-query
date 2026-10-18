package tsh.d94.app.server.data.orm.cira.ciraIssue;

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
	name = ApTableName.SN_CIRA_ISSUE,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraIssueModel.UK01, columnNames = {"ISSUE_KEY"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraIssueModel extends BaseModel {

	public static final String UK01 = "uk_ciraIssue_01";

	@Column(name = "PROJECT_ID", length = 100, nullable = false)
	private String projectId;

	@Column(name = "SPRINT_ID", length = 100)
	private String sprintId;

	@Column(name = "ISSUE_KEY", length = 30, nullable = false)
	private String issueKey;

	@Column(name = "TITLE", length = 500, nullable = false)
	private String title;

	@Column(name = "CONTENT")
	private String content;

	@Column(name = "ISSUE_TYPE_ID", length = 100, nullable = false)
	private String issueTypeId;

	@Column(name = "STATUS_ID", length = 100, nullable = false)
	private String statusId;

	@Column(name = "PRIORITY", length = 20, nullable = false)
	private String priority;

	@Column(name = "STORY_PNT")
	private java.math.BigDecimal storyPnt;

	@Column(name = "ASSIGNEE_ID", length = 100)
	private String assigneeId;

	@Column(name = "REPORTER_ID", length = 100, nullable = false)
	private String reporterId;

	@Column(name = "DUE_DT")
	private java.time.LocalDate dueDt;

	@Column(name = "STARTED_AT")
	private java.time.LocalDateTime startedAt;

	@Column(name = "RESOLVED_AT")
	private java.time.LocalDateTime resolvedAt;

	@Column(name = "DELETED_AT")
	private java.time.LocalDateTime deletedAt;

}
