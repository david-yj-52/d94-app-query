package tsh.d94.app.server.apService.apiToken;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ApiTokenScopeTest {

	@Test
	void ciraAllowsOnlyItsPaths() {
		ApiTokenScope cira = ApiTokenScope.CIRA;
		assertThat(cira.allows("/api/v1/projects")).isTrue();
		assertThat(cira.allows("/api/v1/projects/p1/issues")).isTrue();
		assertThat(cira.allows("/api/v1/users/search")).isTrue();
		// 접두사만 같은 다른 경로·토큰/설정/로그인/내부 API 는 막힌다
		assertThat(cira.allows("/api/v1/projectsX")).isFalse();
		assertThat(cira.allows("/api/v1/api-tokens")).isFalse();
		assertThat(cira.allows("/api/v1/settings")).isFalse();
		assertThat(cira.allows("/api/v1/auth/login")).isFalse();
		assertThat(cira.allows("/api/v1/users")).isFalse();
		assertThat(cira.allows("/api/v1/internal/ayaan/config")).isFalse();
		assertThat(cira.allows("/api/v1/study/documents")).isFalse();
	}

	@Test
	void ofIsCaseInsensitiveAndRejectsUnknown() {
		assertThat(ApiTokenScope.of("CIRA")).isEqualTo(ApiTokenScope.CIRA);
		assertThatThrownBy(() -> ApiTokenScope.of("nope")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rawTokenFormatAndHash() {
		String raw = ApiTokenService.newRawToken(ApiTokenScope.CIRA);
		assertThat(raw).matches("d94_cira_[A-Za-z0-9_-]{43}");
		assertThat(ApiTokenService.sha256(raw)).hasSize(64).isEqualTo(ApiTokenService.sha256(raw));
		assertThat(ApiTokenService.newRawToken(ApiTokenScope.CIRA)).isNotEqualTo(raw);
	}
}
