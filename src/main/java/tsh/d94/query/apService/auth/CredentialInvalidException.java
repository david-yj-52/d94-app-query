package tsh.d94.app.server.apService.auth;

/** 자격 증명 봉투를 풀 수 없음 (형식·키·용도 불일치, 만료, 재사용). 사유는 로그에만 남기고 응답에는 노출하지 않는다 */
public class CredentialInvalidException extends RuntimeException {

	public CredentialInvalidException(String reason) {
		super(reason);
	}

	public CredentialInvalidException(String reason, Throwable cause) {
		super(reason, cause);
	}
}
