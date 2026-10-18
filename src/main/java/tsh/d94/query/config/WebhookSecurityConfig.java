package tsh.d94.app.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * GitHub Webhook 수신 경로(/api/v1/webhooks/**)를 인증 없이 허용하는 보안 설정.
 *
 * <p>@Order(1)로 lib-core의 기본 SecurityFilterChain보다 먼저 적용되며,
 * securityMatcher로 webhook 경로만 선택적으로 처리합니다.
 * 나머지 경로는 lib-core의 JWT 기반 FilterChain이 처리합니다.
 */
@Configuration
public class WebhookSecurityConfig {

	@Bean
	@Order(1)
	public SecurityFilterChain webhookSecurityFilterChain(HttpSecurity http) throws Exception {
		http
			.securityMatcher("/api/v1/webhooks/**")
			.csrf(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.anyRequest().permitAll()
			);
		return http.build();
	}
}
