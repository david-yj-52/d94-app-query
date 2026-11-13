package tsh.d94.app.server.data.orm.ayaan.aynRequest;

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
@Table(name = ApTableName.SN_AYN_REQUEST)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynRequestModel extends BaseModel {

	@Column(name = "TASK_ID", length = 100, nullable = false)
	private String taskId;

	@Column(name = "RUN_ID", length = 100)
	private String runId;

	@Column(name = "KIND", length = 20, nullable = false)
	private String kind;

	@Column(name = "STATUS", length = 20, nullable = false)
	private String status;

	@Column(name = "QUESTION", columnDefinition = "TEXT")
	private String question;

	@Column(name = "OPTIONS", columnDefinition = "TEXT")
	private String options;

	@Column(name = "RECOMMEND")
	private Integer recommend;

	@Column(name = "CONTEXT", columnDefinition = "TEXT")
	private String context;

	@Column(name = "TOOL_NAME", length = 100)
	private String toolName;

	@Column(name = "INPUT_HASH", length = 64)
	private String inputHash;

	@Column(name = "ANSWER", columnDefinition = "TEXT")
	private String answer;

	@Column(name = "DECISION", length = 10)
	private String decision;

	@Column(name = "ANSWERED_BY", length = 100)
	private String answeredBy;

	@Column(name = "ANSWERED_AT")
	private LocalDateTime answeredAt;

	@Column(name = "CONSUMED_AT")
	private LocalDateTime consumedAt;

}
