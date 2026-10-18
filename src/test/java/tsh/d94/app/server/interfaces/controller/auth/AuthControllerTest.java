package tsh.d94.app.server.interfaces.controller.auth;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import tsh.d94.lib.core.apService.auth.AuthService;
import tsh.d94.lib.core.apService.auth.dto.LoginRequest;
import tsh.d94.lib.core.apService.auth.dto.LoginResponse;
import tsh.d94.lib.core.apService.auth.dto.RegisterRequest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private AuthService authService;

	@Test
	void testRegister() throws Exception {
		RegisterRequest request = RegisterRequest.builder()
			.email("test@example.com")
			.name("Test User")
			.password("password123")
			.build();

		doNothing().when(authService).register(any());

		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated());
	}

	@Test
	void testLogin() throws Exception {
		LoginRequest request = LoginRequest.builder()
			.email("test@example.com")
			.password("password123")
			.build();

		LoginResponse response = LoginResponse.builder()
			.accessToken("mock-access-token")
			.refreshToken("mock-refresh-token")
			.tokenType("Bearer")
			.expiresIn(900)
			.build();

		org.mockito.Mockito.when(authService.login(any())).thenReturn(response);

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.accessToken").value("mock-access-token"));
	}
}
