package tsh.d94.app.server.data.orm.cira.ciraAutoExecution;

import java.time.OffsetDateTime;

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
@Table(name = ApTableName.SN_CIRA_AUTO_EXECUTION)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraAutoExecutionModel extends BaseModel {

	@Column(name = "RULE_ID", length = 100, nullable = false)
	private String ruleId;

	@Column(name = "ISSUE_ID", length = 100)
	private String issueId;

	@Column(name = "EXEC_STAT", length = 50, nullable = false)
	private String execStat;

	@Column(name = "EXECUTED_AT", nullable = false, columnDefinition = "TIMESTAMPTZ")
	private OffsetDateTime executedAt;

	@Column(name = "ERR_MSG", columnDefinition = "TEXT")
	private String errMsg;

}
