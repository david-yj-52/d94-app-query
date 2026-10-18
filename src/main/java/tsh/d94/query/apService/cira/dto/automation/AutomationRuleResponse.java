package tsh.d94.app.server.apService.cira.dto.automation;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AutomationRuleResponse {

	private String ruleId;
	private String projectId;
	private String ruleNm;
	private String triggerType;
	private String triggerConfig;
	private String condConfig;
	private String actionType;
	private String actionConfig;
	private Boolean isActive;
	private Integer sortOrd;

}
