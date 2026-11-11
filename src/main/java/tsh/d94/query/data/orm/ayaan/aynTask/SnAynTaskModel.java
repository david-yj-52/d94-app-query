package tsh.d94.app.server.data.orm.ayaan.aynTask;

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
@Table(name = ApTableName.SN_AYN_TASK)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynTaskModel extends BaseModel {

	@Column(name = "ISSUE_KEY", length = 100)
	private String issueKey;

	@Column(name = "SOURCE", length = 20, nullable = false)
	private String source;

	@Column(name = "PROJECT_KEY", length = 50, nullable = false)
	private String projectKey;

	@Column(name = "WORKSPACE_PATH", length = 500, nullable = false)
	private String workspacePath;

	@Column(name = "PROMPT", columnDefinition = "TEXT", nullable = false)
	private String prompt;

	@Column(name = "TOOL_CD", length = 20, nullable = false)
	private String toolCd;

	@Column(name = "EXHAUST_POLICY", length = 20)
	private String exhaustPolicy;

	@Column(name = "FALLBACK_TOOLS", length = 200)
	private String fallbackTools;

	@Column(name = "STATUS", length = 20, nullable = false)
	private String status;

	@Column(name = "CLI_SESSION_ID", length = 200)
	private String cliSessionId;

	@Column(name = "RESUME_AT")
	private LocalDateTime resumeAt;

	@Column(name = "PAUSE_REASON", length = 500)
	private String pauseReason;

	@Column(name = "RESUME_COUNT", nullable = false)
	private Integer resumeCount;

	@Column(name = "LEASE_OWNER", length = 100)
	private String leaseOwner;

	@Column(name = "LEASE_UNTIL")
	private LocalDateTime leaseUntil;

	@Column(name = "RESULT_SUMMARY", columnDefinition = "TEXT")
	private String resultSummary;

	@Column(name = "FAIL_REASON", columnDefinition = "TEXT")
	private String failReason;

	@Column(name = "STARTED_AT")
	private LocalDateTime startedAt;

	@Column(name = "FINISHED_AT")
	private LocalDateTime finishedAt;

}
