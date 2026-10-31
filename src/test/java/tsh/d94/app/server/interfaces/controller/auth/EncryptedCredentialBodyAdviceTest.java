package tsh.d94.app.server.interfaces.controller.auth;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;

import com.fasterxml.jackson.databind.ObjectMapper;

import tsh.d94.app.server.apService.auth.CredentialCryptoService;
import tsh.d94.app.server.apService.auth.CredentialCryptoServiceTestSupport;
import tsh.d94.app.server.apService.auth.CredentialInvalidException;
import tsh.d94.app.server.apService.auth.dto.EncryptedCredential;
import tsh.d94.lib.core.apService.auth.dto.LoginRequest;
import tsh.d94.lib.core.apService.auth.dto.RegisterRequest;

class EncryptedCredentialBodyAdviceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private EncryptedCredentialBodyAdvice advice(CredentialCryptoService service) {
		return new EncryptedCredentialBodyAdvice(service, objectMapper);
	}

	private HttpInputMessage read(EncryptedCredentialBodyAdvice advice, String body, Class<?> target) throws Exception {
		return advice.beforeBodyRead(new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8)), null, target,
			MappingJackson2HttpMessageConverter.class);
	}

	@Test
	@DisplayName("로그인·회원가입 DTO 에만 적용된다")
	void supportsOnlyAuthRequests() {
		EncryptedCredentialBodyAdvice advice = advice(new CredentialCryptoService("", 300, false, objectMapper));

		assertThat(advice.supports(null, LoginRequest.class, MappingJackson2HttpMessageConverter.class)).isTrue();
		assertThat(advice.supports(null, RegisterRequest.class, MappingJackson2HttpMessageConverter.class)).isTrue();
		assertThat(advice.supports(null, String.class, MappingJackson2HttpMessageConverter.class)).isFalse();
	}

	@Test
	@DisplayName("{enc} 본문을 LoginRequest 로 읽을 수 있는 평문 JSON 으로 바꾼다")
	void decryptsEnvelopeIntoLoginRequest() throws Exception {
		CredentialCryptoService service = new CredentialCryptoService("", 300, false, objectMapper);
		EncryptedCredential enc = CredentialCryptoServiceTestSupport.encrypt(service,
			"{\"email\":\"a@b.com\",\"password\":\"pw\",\"ts\":" + System.currentTimeMillis() + ",\"nonce\":\"n\"}",
			"login");
		String body = objectMapper.writeValueAsString(Map.of("enc", enc));

		HttpInputMessage out = read(advice(service), body, LoginRequest.class);
		LoginRequest request = objectMapper.readValue(out.getBody(), LoginRequest.class);

		assertThat(request.getEmail()).isEqualTo("a@b.com");
		assertThat(request.getPassword()).isEqualTo("pw");
	}

	@Test
	@DisplayName("평문 본문은 기본적으로 거절한다")
	void rejectsPlaintextByDefault() {
		EncryptedCredentialBodyAdvice advice = advice(new CredentialCryptoService("", 300, false, objectMapper));

		assertThatThrownBy(() -> read(advice, "{\"email\":\"a@b.com\",\"password\":\"pw\"}", LoginRequest.class))
			.isInstanceOf(CredentialInvalidException.class);
	}

	@Test
	@DisplayName("allow-plaintext=true 면 평문 본문을 그대로 넘긴다 (배포 전환용)")
	void passesPlaintextWhenAllowed() throws Exception {
		EncryptedCredentialBodyAdvice advice = advice(new CredentialCryptoService("", 300, true, objectMapper));

		HttpInputMessage out = read(advice, "{\"email\":\"a@b.com\",\"password\":\"pw\"}", LoginRequest.class);

		assertThat(objectMapper.readValue(out.getBody(), LoginRequest.class).getEmail()).isEqualTo("a@b.com");
	}

	@Test
	@DisplayName("JSON 이 아닌 본문은 거절한다")
	void rejectsNonJson() {
		EncryptedCredentialBodyAdvice advice = advice(new CredentialCryptoService("", 300, true, objectMapper));

		assertThatThrownBy(() -> read(advice, "email=a&password=b", LoginRequest.class))
			.isInstanceOf(CredentialInvalidException.class);
	}
}
