package tsh.d94.app.server.data.orm.cira.ciraGitPr;

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
	name = ApTableName.SN_CIRA_GIT_PR,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraGitPrModel.UK01, columnNames = {"REPO_ID", "PR_NO"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraGitPrModel extends BaseModel {

	public static final String UK01 = "uk_ciraGitPr_01";

	@Column(name = "REPO_ID", length = 100, nullable = false)
	private String repoId;

	@Column(name = "ISSUE_ID", length = 100)
	private String issueId;

	@Column(name = "PR_NO", nullable = false)
	private Integer prNo;

	@Column(name = "TITLE", length = 500, nullable = false)
	private String title;

	@Column(name = "DESCR")
	private String descr;

	@Column(name = "PR_STAT", length = 20, nullable = false)
	private String prStat;

	@Column(name = "SRC_BRANCH", length = 200)
	private String srcBranch;

	@Column(name = "TGT_BRANCH", length = 200)
	private String tgtBranch;

	@Column(name = "AUTHOR_NM", length = 100)
	private String authorNm;

	@Column(name = "AUTHOR_EMAIL", length = 255)
	private String authorEmail;

	@Column(name = "MERGED_AT")
	private java.time.LocalDateTime mergedAt;

	@Column(name = "CLOSED_AT")
	private java.time.LocalDateTime closedAt;

}
