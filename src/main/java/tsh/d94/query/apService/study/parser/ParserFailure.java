package tsh.d94.app.server.apService.study.parser;

/** 파싱 실패. 메시지가 그대로 문서의 failReason 이 된다. */
public class ParserFailure extends RuntimeException {

	public ParserFailure(String message) {
		super(message);
	}
}
