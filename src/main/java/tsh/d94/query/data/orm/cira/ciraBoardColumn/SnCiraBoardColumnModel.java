package tsh.d94.app.server.data.orm.cira.ciraBoardColumn;

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
	name = ApTableName.SN_CIRA_BOARD_COLUMN,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraBoardColumnModel.UK01, columnNames = {"BOARD_ID", "SORT_ORD"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraBoardColumnModel extends BaseModel {

	public static final String UK01 = "uk_ciraBoardColumn_01";

	@Column(name = "BOARD_ID", length = 100, nullable = false)
	private String boardId;

	@Column(name = "STATUS_ID", length = 100, nullable = false)
	private String statusId;

	@Column(name = "COLUMN_NM", length = 100, nullable = false)
	private String columnNm;

	@Column(name = "WIP_LIMIT")
	private Short wipLimit;

	@Column(name = "SORT_ORD", nullable = false)
	private Short sortOrd;

}
