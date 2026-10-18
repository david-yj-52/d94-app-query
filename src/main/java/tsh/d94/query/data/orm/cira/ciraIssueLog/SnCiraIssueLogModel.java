package tsh.d94.app.server.data.orm.cira.ciraIssueLog;

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
@Table(
	name = ApTableName.SN_CIRA_ISSUE_LOG
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraIssueLogModel extends BaseModel {

	public static final String UK01 = "uk_ciraIssueLog_01";

	@Column(name = "ISSUE_ID", length = 100, nullable = false)
	private String issueId;

	@Column(name = "FIELD_NM", length = 100, nullable = false)
	private String fieldNm;

	@Column(name = "OLD_VAL")
	private String oldVal;

	@Column(name = "NEW_VAL")
	private String newVal;

	@Column(name = "CHANGED_BY", length = 100, nullable = false)
	private String changedBy;

	@Column(name = "CHANGED_AT", nullable = false)
	private java.time.LocalDateTime changedAt;

}
