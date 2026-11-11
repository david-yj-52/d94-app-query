package tsh.d94.app.server.apService.ayaan;

import lombok.Getter;

/** 조건부 전이 실패·lease 소유자 불일치·이슈당 활성 작업 중복 → 409. data 는 응답 본문의 data 로 내려간다(중복 시 기존 Task). */
@Getter
public class AyaanConflictException extends RuntimeException {

	private final transient Object data;

	public AyaanConflictException(String message, Object data) {
		super(message);
		this.data = data;
	}

}
