package tsh.d94.app.server.apService.cira.exception;

import lombok.Getter;

@Getter
public class CiraException extends RuntimeException {

	private final ErrorCode errorCode;

	public CiraException(ErrorCode errorCode) {
		super(errorCode.getDefaultMessage());
		this.errorCode = errorCode;
	}

	public CiraException(ErrorCode errorCode, String detail) {
		super(detail);
		this.errorCode = errorCode;
	}
}
