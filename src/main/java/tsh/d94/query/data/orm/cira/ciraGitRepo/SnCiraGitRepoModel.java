package tsh.d94.app.server.data.orm.cira.ciraGitRepo;

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
	name = ApTableName.SN_CIRA_GIT_REPO,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraGitRepoModel.UK01, columnNames = {"PROJECT_ID", "REPO_URL"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraGitRepoModel extends BaseModel {

	public static final String UK01 = "uk_ciraGitRepo_01";

	@Column(name = "PROJECT_ID", length = 100, nullable = false)
	private String projectId;

	@Column(name = "REPO_NM", length = 255, nullable = false)
	private String repoNm;

	@Column(name = "PROVIDER", length = 20, nullable = false)
	private String provider;

	@Column(name = "REPO_URL", length = 500, nullable = false)
	private String repoUrl;

	@Column(name = "ACCESS_TOKEN_ENC", length = 1000)
	private String accessTokenEnc;

	@Column(name = "DEFAULT_BRANCH", length = 100, nullable = false)
	private String defaultBranch;

	@Column(name = "WEBHOOK_SECRET", length = 500)
	private String webhookSecret;

}
