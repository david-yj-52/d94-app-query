package tsh.d94.app.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * ayaan 내부 API(/api/v1/internal/ayaan/**) 보안. JWT 대신 X-Service-Token 헤더를 {@code ayaan.service-token} 과 비교한다.
 * 토큰 설정이 비어 있으면 모든 요청을 401 로 막는다(fail closed).
 * 필터는 @Bean 으로 노출하지 않는다 — 서블릿 컨테이너가 전체 경로에 자동 등록하기 때문.
 */
@Configuration
public class AyaanInternalSecurityConfig {

	@Bean
	@Order(1)
	public SecurityFilterChain ayaanInternalSecurityFilterChain(HttpSecurity http,
		@Value("${ayaan.service-token:}") String serviceToken) throws Exception {
		http
			.securityMatcher("/api/v1/internal/ayaan/**")
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(new ServiceTokenFilter(serviceToken), UsernamePasswordAuthenticationFilter.class)
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		return http.build();
	}

	static final class ServiceTokenFilter extends OncePerRequestFilter {

		private final byte[] expected;

		ServiceTokenFilter(String token) {
			this.expected = token.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
			String given = req.getHeader("X-Service-Token");
			if (expected.length == 0 || given == null
				|| !MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8))) {
				res.setStatus(HttpStatus.UNAUTHORIZED.value());
				return;
			}
			chain.doFilter(req, res);
		}
	}
}
