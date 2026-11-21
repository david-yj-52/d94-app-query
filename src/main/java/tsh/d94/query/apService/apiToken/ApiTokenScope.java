package tsh.d94.app.server.apService.apiToken;

import java.util.Arrays;
import java.util.List;

/**
 * 서비스별 API 토큰이 호출할 수 있는 경로(context-path 제외). 목록에 없는 경로는 403.
 * 토큰 발급·로그인·설정·내부 API 는 어느 서비스에도 넣지 않는다 — 토큰으로 토큰을 만들거나 권한을 바꾸지 못하게.
 * ponytail: 서비스 추가 = 상수 하나. AYAAN 은 MCP 에 열 사용자용 경로가 정해지면 추가.
 */
public enum ApiTokenScope {

	CIRA("cira", List.of(
		"/api/v1/projects", "/api/v1/issues", "/api/v1/boards", "/api/v1/sprints", "/api/v1/milestones",
		"/api/v1/versions", "/api/v1/comments", "/api/v1/attachments", "/api/v1/wiki", "/api/v1/search",
		"/api/v1/saved-filters", "/api/v1/automation-rules", "/api/v1/issue-types", "/api/v1/git-repositories",
		"/api/v1/notifications", "/api/v1/users/search", "/api/v1/users/me/dashboard"));

	public static final String TOKEN_PREFIX = "d94_";

	private final String code;
	private final List<String> paths;

	ApiTokenScope(String code, List<String> paths) {
		this.code = code;
		this.paths = paths;
	}

	public String code() {
		return code;
	}

	/** path 는 정규화된 경로여야 한다(Spring Security StrictHttpFirewall 이 `..`·`;` 를 먼저 거절) */
	public boolean allows(String path) {
		return paths.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"));
	}

	public static ApiTokenScope of(String code) {
		return Arrays.stream(values())
			.filter(s -> s.code.equalsIgnoreCase(code))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("지원하지 않는 서비스입니다: " + code));
	}
}
