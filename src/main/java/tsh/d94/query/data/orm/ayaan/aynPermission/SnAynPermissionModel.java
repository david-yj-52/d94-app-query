package tsh.d94.app.server.data.orm.ayaan.aynPermission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

@Entity
@Table(name = ApTableName.SN_AYN_PERMISSION)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynPermissionModel extends BaseModel {

	@Column(name = "TOOL_CD", length = 20, nullable = false)
	private String toolCd;

	@Column(name = "CATEGORY", length = 20, nullable = false)
	private String category;

	@Column(name = "MATCHER", length = 300, nullable = false)
	private String matcher;

	@Column(name = "DECISION", length = 10, nullable = false)
	private String decision;

	@Column(name = "PRIORITY", nullable = false)
	private Integer priority;

	@Column(name = "DESCRIPTION", length = 500)
	private String description;

}
