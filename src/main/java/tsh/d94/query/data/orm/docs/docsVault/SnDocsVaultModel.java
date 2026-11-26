package tsh.d94.app.server.data.orm.docs.docsVault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/** Docs vault. PRIVATE = 사용자 1인(ownerId=이메일), TEAM = CIRA 프로젝트 1개(projectId). 컬럼 길이는 V620 과 짝. */
@Entity
@Table(name = ApTableName.SN_DOCS_VAULT)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnDocsVaultModel extends BaseModel {

	public static final String PRIVATE = "PRIVATE";
	public static final String TEAM = "TEAM";

	@Column(name = "VAULT_TYPE", length = 10, nullable = false)
	private String vaultType;

	@Column(name = "NAME", length = 200, nullable = false)
	private String name;

	@Column(name = "OWNER_ID", length = 200)
	private String ownerId;

	@Column(name = "PROJECT_ID")
	private String projectId;
}
