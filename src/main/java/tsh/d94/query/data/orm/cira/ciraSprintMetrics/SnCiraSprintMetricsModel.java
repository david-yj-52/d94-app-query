package tsh.d94.app.server.data.orm.cira.ciraSprintMetrics;

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
	name = ApTableName.SN_CIRA_SPRINT_METRICS,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraSprintMetricsModel.UK01, columnNames = {"SPRINT_ID"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraSprintMetricsModel extends BaseModel {

	public static final String UK01 = "uk_ciraSprintMetrics_01";

	@Column(name = "SPRINT_ID", length = 100, nullable = false)
	private String sprintId;

	@Column(name = "VELOCITY")
	private java.math.BigDecimal velocity;

	@Column(name = "TEAM_CAPACITY")
	private java.math.BigDecimal teamCapacity;

	@Column(name = "PLAN_STORY_PNT")
	private java.math.BigDecimal planStoryPnt;

	@Column(name = "COMPL_STORY_PNT")
	private java.math.BigDecimal complStoryPnt;

}
