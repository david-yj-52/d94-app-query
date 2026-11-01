package tsh.d94.app.server.apService.cira.automation;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

class ConditionEvaluatorTest {

	private final ConditionEvaluator evaluator = new ConditionEvaluator(new ObjectMapper());

	private static Map<String, Object> ctx() {
		Map<String, Object> ctx = new HashMap<>();
		ctx.put("priority", "HIGH");
		ctx.put("assigneeId", "user-1");
		ctx.put("statusId", "status-todo");
		ctx.put("issueTypeId", null);
		return ctx;
	}

	private static String one(String field, String op, String value) {
		String v = value == null ? "" : ",\"value\":\"" + value + "\"";
		return "{\"conditions\":[{\"field\":\"" + field + "\",\"op\":\"" + op + "\"" + v + "}]}";
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"  ", "{}", "{\"conditions\":[]}"})
	@DisplayName("조건이 없으면 항상 통과")
	void noConditions_pass(String config) {
		assertThat(evaluator.evaluate(config, ctx())).isTrue();
	}

	@Test
	@DisplayName("EQUALS 는 대소문자 무시, 필드 별칭(assignee→assigneeId, status→statusId)")
	void equals_withAliases() {
		assertThat(evaluator.evaluate(one("priority", "EQUALS", "high"), ctx())).isTrue();
		assertThat(evaluator.evaluate(one("assignee", "EQUALS", "user-1"), ctx())).isTrue();
		assertThat(evaluator.evaluate(one("status", "EQUALS", "status-done"), ctx())).isFalse();
	}

	@Test
	@DisplayName("NOT_EQUALS, IS_EMPTY, IS_NOT_EMPTY")
	void otherOperators() {
		assertThat(evaluator.evaluate(one("priority", "NOT_EQUALS", "LOW"), ctx())).isTrue();
		assertThat(evaluator.evaluate(one("issueType", "IS_EMPTY", null), ctx())).isTrue();
		assertThat(evaluator.evaluate(one("assignee", "IS_NOT_EMPTY", null), ctx())).isTrue();
		assertThat(evaluator.evaluate(one("assignee", "IS_EMPTY", null), ctx())).isFalse();
	}

	@Test
	@DisplayName("AND 는 모두, OR 는 하나만 참이면 통과 (operator 생략 시 AND)")
	void andOr() {
		String conds = "[{\"field\":\"priority\",\"op\":\"EQUALS\",\"value\":\"HIGH\"},"
			+ "{\"field\":\"status\",\"op\":\"EQUALS\",\"value\":\"nope\"}]";
		assertThat(evaluator.evaluate("{\"conditions\":" + conds + "}", ctx())).isFalse();
		assertThat(evaluator.evaluate("{\"operator\":\"AND\",\"conditions\":" + conds + "}", ctx())).isFalse();
		assertThat(evaluator.evaluate("{\"operator\":\"or\",\"conditions\":" + conds + "}", ctx())).isTrue();
	}

	@Test
	@DisplayName("값 없는 EQUALS·모르는 연산자·깨진 JSON 은 실패(false) 처리")
	void invalidInputs_fail() {
		assertThat(evaluator.evaluate(one("priority", "EQUALS", null), ctx())).isFalse();
		assertThat(evaluator.evaluate(one("priority", "CONTAINS", "H"), ctx())).isFalse();
		assertThat(evaluator.evaluate("{not json", ctx())).isFalse();
		assertThat(evaluator.evaluate("{\"conditions\":[{\"field\":\"priority\"}]}", ctx())).isFalse();
	}

	@Test
	@DisplayName("컨텍스트가 비어 있으면(이슈를 못 읽음, D2) 담당자 조건은 거짓")
	void emptyContext_assigneeConditionFalse() {
		assertThat(evaluator.evaluate(one("assignee", "IS_NOT_EMPTY", null), new HashMap<>())).isFalse();
	}

}
