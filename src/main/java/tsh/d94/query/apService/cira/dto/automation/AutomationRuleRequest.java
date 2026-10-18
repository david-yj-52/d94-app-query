package tsh.d94.app.server.apService.cira.dto.automation;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AutomationRuleRequest {

	private String ruleNm;
	private String triggerType;
	private String triggerConfig;
	private String condConfig;
	private String actionType;
	private String actionConfig;
	private Boolean isActive;
	private Integer sortOrd;

}
