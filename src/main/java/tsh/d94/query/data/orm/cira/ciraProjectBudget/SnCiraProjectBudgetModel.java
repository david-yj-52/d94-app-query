package tsh.d94.app.server.data.orm.cira.ciraProjectBudget;

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
	name = ApTableName.SN_CIRA_PROJECT_BUDGET
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraProjectBudgetModel extends BaseModel {

	public static final String UK01 = "uk_ciraProjectBudget_01";

	@Column(name = "PROJECT_ID", length = 100, nullable = false)
	private String projectId;

	@Column(name = "TOTAL_BUDGET", nullable = false)
	private java.math.BigDecimal totalBudget;

	@Column(name = "BUDGET_CATEGORY", length = 50, nullable = false)
	private String budgetCategory;

	@Column(name = "CURRENCY", length = 3, nullable = false)
	private String currency;

	@Column(name = "FISCAL_YEAR")
	private Short fiscalYear;

}
