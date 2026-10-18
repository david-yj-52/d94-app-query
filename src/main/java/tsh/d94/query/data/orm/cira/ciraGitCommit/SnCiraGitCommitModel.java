package tsh.d94.app.server.data.orm.cira.ciraGitCommit;

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
	name = ApTableName.SN_CIRA_GIT_COMMIT,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraGitCommitModel.UK01, columnNames = {"REPO_ID", "COMMIT_HASH"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraGitCommitModel extends BaseModel {

	public static final String UK01 = "uk_ciraGitCommit_01";

	@Column(name = "REPO_ID", length = 100, nullable = false)
	private String repoId;

	@Column(name = "ISSUE_ID", length = 100)
	private String issueId;

	@Column(name = "COMMIT_HASH", length = 40, nullable = false)
	private String commitHash;

	@Column(name = "MSG")
	private String msg;

	@Column(name = "AUTHOR_NM", length = 100)
	private String authorNm;

	@Column(name = "AUTHOR_EMAIL", length = 255)
	private String authorEmail;

	@Column(name = "COMMIT_DT", nullable = false)
	private java.time.LocalDateTime commitDt;

}
