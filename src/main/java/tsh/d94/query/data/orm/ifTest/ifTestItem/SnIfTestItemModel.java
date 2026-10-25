package tsh.d94.app.server.data.orm.ifTest.ifTestItem;

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
	name = ApTableName.SN_IF_TEST_ITEM
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnIfTestItemModel extends BaseModel {

	@Column(name = "ITEM_NM", length = 100, nullable = false)
	private String itemNm;

	@Column(name = "CONTENT", columnDefinition = "TEXT")
	private String content;

	@Column(name = "STATUS", length = 50, nullable = false)
	private String status;

}
