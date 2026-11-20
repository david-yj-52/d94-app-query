package tsh.d94.app.server.interfaces.controller;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerAccessDeniedTest {

	@Test
	void access_denied_is_403_not_500() {
		var res = new GlobalExceptionHandler().handleAccessDenied(new AccessDeniedException("x"));

		assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(res.getBody().isSuccess()).isFalse();
	}
}
