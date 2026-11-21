package tsh.d94.app.server.apService.apiToken;

import java.time.LocalDateTime;

import org.springframework.security.core.userdetails.UserDetails;

public final class ApiTokenDtos {

	private ApiTokenDtos() {
	}

	/** access: READ | WRITE (기본 READ). expiresInDays: 30 | 90 | 365 (기본 90) */
	public record IssueRequest(String service, String name, String access, Integer expiresInDays) {
	}

	/** 해시·원문 없음 */
	public record TokenView(String id, String name, String service, String access, String prefix,
		LocalDateTime expiresAt, LocalDateTime lastUsedAt, LocalDateTime createdAt) {
	}

	/** token 원문은 이 응답에서만 한 번 내려간다 */
	public record IssueResponse(String token, TokenView info) {
	}

	public record ServiceStatus(String service, boolean enabled) {
	}

	public record ServiceToggle(Boolean enabled) {
	}

	public sealed interface AuthResult {
	}

	public record Authenticated(UserDetails user, String tenant, String tokenPrefix) implements AuthResult {
	}

	public record Denied(int status, String code, String message) implements AuthResult {
	}
}
