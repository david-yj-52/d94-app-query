package tsh.d94.app.server.data.orm.ayaan.aynTaskRun;

import java.math.BigDecimal;
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
@Table(name = ApTableName.SN_AYN_TASK_RUN)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynTaskRunModel extends BaseModel {

	@Column(name = "TASK_ID", length = 100, nullable = false)
	private String taskId;

	@Column(name = "RUN_SEQ", nullable = false)
	private Integer runSeq;

	@Column(name = "TOOL_CD", length = 20, nullable = false)
	private String toolCd;

	@Column(name = "CLI_SESSION_ID", length = 200)
	private String cliSessionId;

	@Column(name = "OUTCOME", length = 20)
	private String outcome;

	@Column(name = "INPUT_TOKENS")
	private Long inputTokens;

	@Column(name = "OUTPUT_TOKENS")
	private Long outputTokens;

	@Column(name = "CACHE_READ_TOKENS")
	private Long cacheReadTokens;

	@Column(name = "CACHE_WRITE_TOKENS")
	private Long cacheWriteTokens;

	@Column(name = "COST_USD", precision = 12, scale = 6)
	private BigDecimal costUsd;

	@Column(name = "EXIT_CODE")
	private Integer exitCode;

	@Column(name = "LOG_PATH", length = 500)
	private String logPath;

	@Column(name = "STARTED_AT")
	private LocalDateTime startedAt;

	@Column(name = "ENDED_AT")
	private LocalDateTime endedAt;

}
