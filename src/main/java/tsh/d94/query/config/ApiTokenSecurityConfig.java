package tsh.d94.app.server.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import tsh.d94.app.server.apService.apiToken.ApiTokenDtos.Authenticated;
import tsh.d94.app.server.apService.apiToken.ApiTokenDtos.AuthResult;
import tsh.d94.app.server.apService.apiToken.ApiTokenDtos.Denied;
import tsh.d94.app.server.apService.apiToken.ApiTokenScope;
import tsh.d94.app.server.apService.apiToken.ApiTokenService;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/**
 * {@code Authorization: Bearer d94_…} 요청만 받는 체인. JWT 체인(lib-core)보다 먼저, ayaan 내부 체인(@Order(1)) 다음.
 * 허용 경로·읽기/쓰기는 {@link ApiTokenService#authenticate} 가 판단한다.
 * 필터는 @Bean 으로 노출하지 않는다 — 서블릿 컨테이너가 전체 경로에 자동 등록하기 때문.
 */
@Configuration
public class ApiTokenSecurityConfig {

	private static final String BEARER = "Bearer ";

	@Bean
	@Order(2)
	public SecurityFilterChain apiTokenSecurityFilterChain(HttpSecurity http, ApiTokenService apiTokenService,
		ObjectMapper objectMapper) throws Exception {
		http
			.securityMatcher(ApiTokenSecurityConfig::hasApiToken)
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilterBefore(new ApiTokenFilter(apiTokenService, objectMapper), UsernamePasswordAuthenticationFilter.class)
			.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
		return http.build();
	}

	static boolean hasApiToken(HttpServletRequest req) {
		String header = req.getHeader("Authorization");
		return header != null && header.startsWith(BEARER + ApiTokenScope.TOKEN_PREFIX);
	}

	@Slf4j
	static final class ApiTokenFilter extends OncePerRequestFilter {

		private final ApiTokenService service;
		private final ObjectMapper objectMapper;

		ApiTokenFilter(ApiTokenService service, ObjectMapper objectMapper) {
			this.service = service;
			this.objectMapper = objectMapper;
		}

		@Override
		protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
			String raw = req.getHeader("Authorization").substring(BEARER.length()).trim();
			String path = req.getRequestURI().substring(req.getContextPath().length());
			AuthResult result = service.authenticate(raw, req.getMethod(), path);

			if (result instanceof Denied denied) {
				// 원문은 남기지 않는다 — 형식 prefix(d94_{service}_) 까지만
				log.warn("API token denied [{}] {} {} token={}", denied.code(), req.getMethod(), path, displayPrefix(raw));
				res.setStatus(denied.status());
				res.setContentType(MediaType.APPLICATION_JSON_VALUE);
				res.setCharacterEncoding(StandardCharsets.UTF_8.name());
				objectMapper.writeValue(res.getWriter(), ApiResponse.error(denied.code(), denied.message()));
				return;
			}

			Authenticated ok = (Authenticated)result;
			UsernamePasswordAuthenticationToken auth =
				new UsernamePasswordAuthenticationToken(ok.user(), null, ok.user().getAuthorities());
			SecurityContextHolder.getContext().setAuthentication(auth);
			TenantContext.set(ok.tenant());
			try {
				chain.doFilter(req, res);
			} finally {
				TenantContext.clear();
				SecurityContextHolder.clearContext();
			}
		}

		private static String displayPrefix(String raw) {
			int end = raw.indexOf('_', ApiTokenScope.TOKEN_PREFIX.length());
			return end < 0 ? "d94_?" : raw.substring(0, end + 1) + "…";
		}
	}
}
