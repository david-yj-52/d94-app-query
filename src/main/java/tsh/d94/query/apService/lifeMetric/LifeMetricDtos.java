package tsh.d94.app.server.apService.lifeMetric;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/** life_metric 요청·응답. inputSchema 는 [{key,label,type(NUMBER|DATE),unit,mode(BASELINE|LOG)}] JSON 그대로. */
public final class LifeMetricDtos {

	private LifeMetricDtos() {
	}

	public record TemplateResponse(String id, String code, String name, JsonNode inputSchema, String defaultFormula,
		String outputUnit) {
	}

	/** templateId 를 주고 formula 를 비우면 템플릿의 스키마·수식·단위를 쓴다. code 는 생성 후 바꿀 수 없다. */
	public record MetricRequest(String templateId, String code, String name, JsonNode inputSchema, String formula,
		String outputUnit) {
	}

	/** value·error 는 지금 평가한 결과 (둘 중 하나만 채움) */
	public record MetricResponse(String id, String templateId, String code, String name, JsonNode inputSchema,
		String formula, String outputUnit, Map<String, Object> inputs, Double value, String error,
		LocalDateTime createdAt, LocalDateTime modifiedAt) {
	}

	/** 값: 숫자, 날짜(yyyy-MM-dd)·텍스트 문자열, null·빈 문자열이면 삭제 */
	public record InputsRequest(Map<String, Object> values) {
	}

	/** recordedAt 이 없으면 지금 */
	public record LogRequest(String inputKey, Double value, String note, LocalDateTime recordedAt) {
	}

	public record LogResponse(long id, String inputKey, double value, String note, LocalDateTime recordedAt) {
	}

	public record EvaluateResponse(Double value, String unit, String error) {
	}

	/** 저장 전 미리보기: 기준값은 inputs, @code 는 내 지표, 로그 집계는 0 */
	public record PreviewRequest(String formula, Map<String, Object> inputs) {
	}
}
