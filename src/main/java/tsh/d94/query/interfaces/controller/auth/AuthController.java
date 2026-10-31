package tsh.d94.app.server.interfaces.controller.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.auth.CredentialCryptoService;
import tsh.d94.app.server.apService.auth.dto.CredentialPublicKeyResponse;
import tsh.d94.lib.core.apService.auth.AuthService;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;
import tsh.d94.lib.core.interfaces.rest.auth.BaseAuthController;

/**
 * login·register 본문은 암호화 봉투로만 받는다 → EncryptedCredentialBodyAdvice 가 lib-core 로 넘기기 전에 복호화한다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController extends BaseAuthController {

	private final CredentialCryptoService credentialCryptoService;

	public AuthController(AuthService authService, CredentialCryptoService credentialCryptoService) {
		super(authService);
		this.credentialCryptoService = credentialCryptoService;
	}

	/** 자격 증명 암호화용 공개키 (/api/v1/auth/** 는 SecurityConfig 에서 permitAll) */
	@GetMapping("/public-key")
	public ApiResponse<CredentialPublicKeyResponse> publicKey() {
		return ApiResponse.ok(credentialCryptoService.getPublicKey());
	}

	// 서비스 특화 로직이 필요한 경우 아래 hook 메서드를 오버라이드 하세요.
	/*
	@Override
	protected void preLogin(LoginRequest request) {
		// App level custom logic
	}
	*/
}
