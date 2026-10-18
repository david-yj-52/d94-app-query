package tsh.d94.app.server.data.orm.cira.ciraIssueStatus;

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
	name = ApTableName.SN_CIRA_ISSUE_STATUS,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraIssueStatusModel.UK01, columnNames = {"PROJECT_ID", "STATUS_NM"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraIssueStatusModel extends BaseModel {

	public static final String UK01 = "uk_ciraIssueStatus_01";

	@Column(name = "PROJECT_ID", length = 100)
	private String projectId;

	@Column(name = "STATUS_NM", length = 50, nullable = false)
	private String statusNm;

	@Column(name = "CATEGORY", length = 20, nullable = false)
	private String category;

	@Column(name = "COLOR_CD", length = 7)
	private String colorCd;

	@Column(name = "SORT_ORD", nullable = false)
	private Short sortOrd;

}
