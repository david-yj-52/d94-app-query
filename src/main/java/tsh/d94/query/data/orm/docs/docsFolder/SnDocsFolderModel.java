package tsh.d94.app.server.data.orm.docs.docsFolder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/** ponytail: @Audited 미사용, 물리 삭제 (study 와 같음). 컬럼 길이는 V570 과 짝. */
@Entity
@Table(name = ApTableName.SN_DOCS_FOLDER)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnDocsFolderModel extends BaseModel {

	/** 만든 사람(이메일). 접근 범위는 vaultId */
	@Column(name = "OWNER_ID", length = 200, nullable = false)
	private String ownerId;

	@Column(name = "VAULT_ID", nullable = false)
	private String vaultId;

	/** null = 최상위 */
	@Column(name = "PARENT_ID")
	private String parentId;

	@Column(name = "NAME", length = 200, nullable = false)
	private String name;

	@Column(name = "SORT_ORDER", nullable = false)
	private Integer sortOrder;
}
