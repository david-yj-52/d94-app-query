package tsh.d94.app.server.interfaces.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import tsh.d94.app.server.apService.cira.cql.CqlParseException;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;

/** 예외 → HTTP 상태·{success:false, code, message} 응답 매핑 */
class GlobalExceptionHandlerTest {

	private MockMvc mvc;

	@RestController
	static class ThrowingController {

		record Body(@NotBlank String name) {
		}

		@GetMapping("/throw")
		void throwIt(@RequestParam String kind) {
			switch (kind) {
				case "cira" -> throw new CiraException(ErrorCode.ISSUE_INVALID_TRANSITION);
				case "cira-not-found" -> throw new CiraException(ErrorCode.ISSUE_NOT_FOUND);
				case "cira-not-member" -> throw new CiraException(ErrorCode.PROJECT_NOT_MEMBER);
				case "cql" -> throw new CqlParseException("bad", 3);
				case "not-found" -> throw new EntityNotFoundException("gone");
				case "illegal-arg" -> throw new IllegalArgumentException("nope");
				case "illegal-state" -> throw new IllegalStateException("PROJECT_MEMBER_ALREADY_EXISTS");
				case "security" -> throw new SecurityException("Access denied: ADMIN role required");
				default -> throw new RuntimeException("boom");
			}
		}

		@PostMapping("/valid")
		void valid(@Valid @RequestBody Body body) {
		}
	}

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	@DisplayName("CiraException → 400 + 도메인 코드")
	void ciraException() throws Exception {
		mvc.perform(get("/throw").param("kind", "cira"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value(ErrorCode.ISSUE_INVALID_TRANSITION.getCode()));
	}

	@Test
	@DisplayName("CqlParseException → 400 CQL_PARSE_ERROR")
	void cqlParse() throws Exception {
		mvc.perform(get("/throw").param("kind", "cql"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CQL_PARSE_ERROR"));
	}

	@Test
	@DisplayName("EntityNotFoundException → 404, IllegalArgumentException → 400, 그 밖 → 500 (메시지 숨김)")
	void standardMappings() throws Exception {
		mvc.perform(get("/throw").param("kind", "not-found")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mvc.perform(get("/throw").param("kind", "illegal-arg")).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		mvc.perform(get("/throw").param("kind", "boom")).andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
			.andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
	}

	@Test
	@DisplayName("@Valid 실패 → 400 VALIDATION_ERROR + 필드명")
	void validation() throws Exception {
		mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("name")));
	}

	@Test
	@DisplayName("[D19] 중복(IllegalStateException)은 409, 권한(SecurityException)은 403 이어야 한다 — 지금은 500")
	void conflictAndForbidden() throws Exception {
		mvc.perform(get("/throw").param("kind", "illegal-state")).andExpect(status().isConflict());
		mvc.perform(get("/throw").param("kind", "security")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("[D19] CiraException 중 NOT_FOUND 계열은 404, PROJECT_NOT_MEMBER 는 403 이어야 한다 — 지금은 모두 400")
	void ciraNotFoundAndForbidden() throws Exception {
		mvc.perform(get("/throw").param("kind", "cira-not-found")).andExpect(status().isNotFound());
		mvc.perform(get("/throw").param("kind", "cira-not-member")).andExpect(status().isForbidden());
	}

}
