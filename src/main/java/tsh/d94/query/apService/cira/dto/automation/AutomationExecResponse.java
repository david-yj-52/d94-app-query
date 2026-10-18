package tsh.d94.app.server.apService.cira.dto.automation;

import java.time.OffsetDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AutomationExecResponse {

	private String execId;
	private String ruleId;
	private String issueId;
	private String execStat;
	private OffsetDateTime executedAt;
	private String errMsg;

}
