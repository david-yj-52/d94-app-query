package tsh.d94.app.server.apService.study.exception;

import lombok.Getter;

@Getter
public class StudyException extends RuntimeException {

	private final StudyErrorCode errorCode;

	public StudyException(StudyErrorCode errorCode) {
		super(errorCode.getDefaultMessage());
		this.errorCode = errorCode;
	}

	public StudyException(StudyErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
