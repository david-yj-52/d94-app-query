package tsh.d94.app.server.interfaces.controller.apiToken;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.apiToken.ApiTokenDtos.*;
import tsh.d94.app.server.apService.apiToken.ApiTokenService;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/**
 * 내 API 토큰 관리. JWT 로그인으로만 호출된다 — API 토큰 체인은 이 경로를 어느 서비스 범위에도 넣지 않아 403.
 * 서비스 on/off 변경은 ADMIN 만.
 */
@RestController
@RequestMapping("/api/v1/api-tokens")
@RequiredArgsConstructor
public class ApiTokenController {

	private final ApiTokenService apiTokenService;

	@GetMapping
	public ApiResponse<List<TokenView>> list() {
		return ApiResponse.ok(apiTokenService.list(TenantContext.get(), owner()));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<IssueResponse>> issue(@RequestBody IssueRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.created(apiTokenService.issue(TenantContext.get(), owner(), request)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> revoke(@PathVariable String id) {
		apiTokenService.revoke(TenantContext.get(), owner(), id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/services")
	public ApiResponse<List<ServiceStatus>> services() {
		return ApiResponse.ok(apiTokenService.services(TenantContext.get()));
	}

	@PutMapping("/services/{service}")
	@PreAuthorize("hasRole('ADMIN')")
	public ApiResponse<List<ServiceStatus>> toggle(@PathVariable String service, @RequestBody ServiceToggle body) {
		return ApiResponse.ok(apiTokenService.setServiceEnabled(TenantContext.get(), service, body.enabled()));
	}

	private static String owner() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}
}
