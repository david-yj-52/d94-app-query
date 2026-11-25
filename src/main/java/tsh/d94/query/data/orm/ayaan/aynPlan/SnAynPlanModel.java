package tsh.d94.app.server.data.orm.ayaan.aynPlan;

import java.time.LocalDateTime;

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
@Table(name = ApTableName.SN_AYN_PLAN)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynPlanModel extends BaseModel {

	@Column(name = "PROJECT_KEY", length = 50, nullable = false)
	private String projectKey;

	@Column(name = "MILESTONE_ID", length = 100, nullable = false)
	private String milestoneId;

	@Column(name = "MILESTONE_KEY", length = 60)
	private String milestoneKey;

	@Column(name = "STAGE", length = 20, nullable = false)
	private String stage;

	@Column(name = "CURRENT_ISSUE_KEY", length = 100)
	private String currentIssueKey;

	@Column(name = "LAST_ERROR", columnDefinition = "TEXT")
	private String lastError;

	@Column(name = "STARTED_AT")
	private LocalDateTime startedAt;

	@Column(name = "ENDED_AT")
	private LocalDateTime endedAt;

}
