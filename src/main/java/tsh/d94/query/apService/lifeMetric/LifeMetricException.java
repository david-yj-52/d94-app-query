package tsh.d94.app.server.apService.lifeMetric;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** life_metric 오류. ponytail: 코드 enum 을 같은 파일에 둔다. */
@Getter
public class LifeMetricException extends RuntimeException {

	@Getter
	@RequiredArgsConstructor
	public enum Code {
		NOT_FOUND("LIFE_METRIC_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않습니다."),
		INVALID_REQUEST("LIFE_METRIC_INVALID_REQUEST", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
		DUPLICATE_CODE("LIFE_METRIC_DUPLICATE_CODE", HttpStatus.CONFLICT, "같은 코드의 지표가 이미 있습니다."),
		/** 수식 문법 오류·모르는 함수 — 저장 거절 */
		INVALID_FORMULA("LIFE_METRIC_INVALID_FORMULA", HttpStatus.BAD_REQUEST, "수식이 올바르지 않습니다."),
		CIRCULAR_REFERENCE("LIFE_METRIC_CIRCULAR_REFERENCE", HttpStatus.BAD_REQUEST, "지표 참조가 순환합니다."),
		/** 0 나누기·값 없음 등 계산 시점 오류 — 저장은 허용, 평가 결과에 error 로 싣는다 */
		EVALUATION_FAILED("LIFE_METRIC_EVALUATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "계산할 수 없습니다.");

		private final String code;
		private final HttpStatus status;
		private final String defaultMessage;
	}

	private final Code code;

	public LifeMetricException(Code code) {
		this(code, code.getDefaultMessage());
	}

	public LifeMetricException(Code code, String message) {
		super(message);
		this.code = code;
	}
}
