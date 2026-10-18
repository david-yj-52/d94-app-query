package tsh.d94.app.server.apService.cira.automation;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자동화 규칙 조건 평가기
 *
 * conditionConfig JSON 형식:
 * {
 *   "operator": "AND",
 *   "conditions": [
 *     { "field": "priority", "op": "EQUALS", "value": "HIGH" }
 *   ]
 * }
 *
 * 지원 field: priority, assignee, status, issueType
 * 지원 op   : EQUALS, NOT_EQUALS, IS_EMPTY, IS_NOT_EMPTY
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionEvaluator {

	private final ObjectMapper objectMapper;

	public boolean evaluate(String conditionConfig, Map<String, Object> eventContext) {
		if (conditionConfig == null || conditionConfig.isBlank()) {
			return true; // 조건 없으면 항상 통과
		}
		try {
			Map<String, Object> config = objectMapper.readValue(conditionConfig, new TypeReference<>() {
			});
			String operator = (String)config.getOrDefault("operator", "AND");
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> conditions = (List<Map<String, Object>>)config.get("conditions");

			if (conditions == null || conditions.isEmpty()) {
				return true;
			}

			if ("OR".equalsIgnoreCase(operator)) {
				return conditions.stream().anyMatch(c -> evaluateCondition(c, eventContext));
			} else {
				return conditions.stream().allMatch(c -> evaluateCondition(c, eventContext));
			}
		} catch (Exception e) {
			log.warn("conditionConfig 파싱 실패, 조건 false 처리: {}", e.getMessage());
			return false;
		}
	}

	private boolean evaluateCondition(Map<String, Object> condition, Map<String, Object> ctx) {
		String field = (String)condition.get("field");
		String op = (String)condition.get("op");
		String value = condition.get("value") != null ? condition.get("value").toString() : null;

		Object contextValue = resolveField(field, ctx);
		String ctxStr = contextValue != null ? contextValue.toString() : null;

		return switch (op) {
			case "EQUALS" -> value != null && value.equalsIgnoreCase(ctxStr);
			case "NOT_EQUALS" -> value != null && !value.equalsIgnoreCase(ctxStr);
			case "IS_EMPTY" -> ctxStr == null || ctxStr.isBlank();
			case "IS_NOT_EMPTY" -> ctxStr != null && !ctxStr.isBlank();
			default -> {
				log.warn("지원하지 않는 조건 연산자: {}", op);
				yield false;
			}
		};
	}

	private Object resolveField(String field, Map<String, Object> ctx) {
		if (field == null)
			return null;
		return switch (field) {
			case "priority" -> ctx.get("priority");
			case "assignee" -> ctx.get("assigneeId");
			case "status" -> ctx.get("statusId");
			case "issueType" -> ctx.get("issueTypeId");
			default -> ctx.get(field);
		};
	}

}
