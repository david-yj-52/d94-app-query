package tsh.d94.app.server.data.orm.ayaan.aynWorkspaceRepo;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.app.server.data.orm.ayaan.AynRefBase;

@Entity
@Table(name = ApTableName.SN_AYN_WORKSPACE_REPO)
@IdClass(SnAynWorkspaceRepoModel.Pk.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynWorkspaceRepoModel extends AynRefBase {

	@Id
	@Column(name = "PROJECT_KEY", length = 50)
	private String projectKey;

	@Id
	@Column(name = "REPO_NM", length = 100)
	private String repoNm;

	@Column(name = "GIT_URL", length = 500, nullable = false)
	private String gitUrl;

	@Column(name = "BASE_BRANCH", length = 100, nullable = false)
	private String baseBranch;

	@Column(name = "DEPLOY_BRANCH", length = 100)
	private String deployBranch;

	@Column(name = "SORT_ORD", nullable = false)
	private Short sortOrd;

	@Getter
	@Setter
	@NoArgsConstructor
	@EqualsAndHashCode
	public static class Pk implements Serializable {
		private String projectKey;
		private String repoNm;
	}

}
