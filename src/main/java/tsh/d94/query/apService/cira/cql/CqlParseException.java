package tsh.d94.app.server.apService.cira.cql;

import lombok.Getter;

/**
 * CQL 파싱 오류 시 throw되는 RuntimeException.
 * GlobalExceptionHandler에서 HTTP 400으로 처리된다.
 */
@Getter
public class CqlParseException extends RuntimeException {

	private final int position;

	public CqlParseException(String message, int position) {
		super(message);
		this.position = position;
	}

	public CqlParseException(String message) {
		this(message, -1);
	}
}
