package tsh.d94.app.server.interfaces.controller.auth;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.lib.core.apService.auth.AuthService;
import tsh.d94.lib.core.interfaces.rest.auth.BaseAuthController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController extends BaseAuthController {

	public AuthController(AuthService authService) {
		super(authService);
	}

	// 서비스 특화 로직이 필요한 경우 아래 hook 메서드를 오버라이드 하세요.
	/*
	@Override
	protected void preLogin(LoginRequest request) {
		// App level custom logic
	}
	*/
}
