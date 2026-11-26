package tsh.d94.app.server.apService.docs;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** docs 오류. ponytail: 코드 enum 을 같은 파일에 둔다. */
@Getter
public class DocsException extends RuntimeException {

	@Getter
	@RequiredArgsConstructor
	public enum Code {
		NOT_FOUND("DOCS_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않습니다."),
		FORBIDDEN("DOCS_FORBIDDEN", HttpStatus.FORBIDDEN, "권한이 없습니다."),
		INVALID_REQUEST("DOCS_INVALID_REQUEST", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
		DUPLICATE_TITLE("DOCS_DUPLICATE_TITLE", HttpStatus.CONFLICT, "같은 제목의 노트가 이미 있습니다."),
		DUPLICATE_FOLDER("DOCS_DUPLICATE_FOLDER", HttpStatus.CONFLICT, "같은 이름의 폴더가 이미 있습니다."),
		FOLDER_NOT_EMPTY("DOCS_FOLDER_NOT_EMPTY", HttpStatus.CONFLICT, "비어 있지 않은 폴더는 삭제할 수 없습니다."),
		INVALID_MOVE("DOCS_INVALID_MOVE", HttpStatus.BAD_REQUEST, "폴더를 자기 자신이나 하위 폴더로 옮길 수 없습니다."),
		VERSION_CONFLICT("DOCS_VERSION_CONFLICT", HttpStatus.CONFLICT, "다른 곳에서 먼저 수정되었습니다. 새로고침 후 다시 저장하세요.");

		private final String code;
		private final HttpStatus status;
		private final String defaultMessage;
	}

	private final Code code;

	public DocsException(Code code) {
		this(code, code.getDefaultMessage());
	}

	public DocsException(Code code, String message) {
		super(message);
		this.code = code;
	}
}
