package tsh.d94.app.server.support;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

/**
 * 서비스가 읽는 요청 컨텍스트(로그인 사용자 email, tenant)를 테스트에서 채우고 비운다.
 * 운영에서는 lib-core {@code JwtAuthenticationFilter} 가 같은 일을 한다.
 */
public final class TestContexts {

	public static final String TENANT = "TSH";

	private TestContexts() {
	}

	/** 로그인 사용자와 tenant 를 함께 설정 */
	public static void login(String email) {
		login(email, TENANT);
	}

	public static void login(String email, String tenant) {
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
		TenantContext.set(tenant);
	}

	public static void clear() {
		SecurityContextHolder.clearContext();
		TenantContext.clear();
	}

}
