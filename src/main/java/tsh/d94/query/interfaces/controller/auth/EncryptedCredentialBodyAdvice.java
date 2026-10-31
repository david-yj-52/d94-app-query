package tsh.d94.app.server.interfaces.controller.auth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tsh.d94.app.server.apService.auth.CredentialCryptoService;
import tsh.d94.app.server.apService.auth.CredentialInvalidException;
import tsh.d94.app.server.apService.auth.dto.EncryptedCredential;
import tsh.d94.lib.core.apService.auth.dto.LoginRequest;
import tsh.d94.lib.core.apService.auth.dto.RegisterRequest;

/**
 * 로그인·회원가입 본문 {"enc": {...}} 를 lib-core BaseAuthController 가 받기 전에 평문 JSON 으로 바꾼다.
 * lib-core 를 고치지 않고 d94-query 에서 암호화를 강제하기 위한 구조.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class EncryptedCredentialBodyAdvice extends RequestBodyAdviceAdapter {

	private final CredentialCryptoService credentialCryptoService;
	private final ObjectMapper objectMapper;

	@Override
	public boolean supports(MethodParameter methodParameter, Type targetType,
		Class<? extends HttpMessageConverter<?>> converterType) {
		return targetType == LoginRequest.class || targetType == RegisterRequest.class;
	}

	@Override
	public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
		Type targetType, Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
		String purpose = targetType == LoginRequest.class
			? CredentialCryptoService.PURPOSE_LOGIN
			: CredentialCryptoService.PURPOSE_REGISTER;
		byte[] body = inputMessage.getBody().readAllBytes();

		JsonNode root;
		try {
			root = objectMapper.readTree(body);
		} catch (JsonProcessingException e) {
			throw new CredentialInvalidException("body is not JSON");
		}
		JsonNode enc = root == null ? null : root.get("enc");

		byte[] plain;
		if (enc != null && enc.isObject()) {
			EncryptedCredential envelope = objectMapper.treeToValue(enc, EncryptedCredential.class);
			plain = objectMapper.writeValueAsBytes(credentialCryptoService.decrypt(envelope, purpose));
		} else if (credentialCryptoService.isPlaintextAllowed()) {
			log.warn("[credential] 평문 {} 요청을 받았습니다 (allow-plaintext=true)", purpose);
			plain = body;
		} else {
			throw new CredentialInvalidException("plaintext " + purpose + " rejected");
		}

		HttpHeaders headers = new HttpHeaders();
		headers.putAll(inputMessage.getHeaders());
		headers.remove(HttpHeaders.CONTENT_LENGTH);
		return new HttpInputMessage() {
			@Override
			public InputStream getBody() {
				return new ByteArrayInputStream(plain);
			}

			@Override
			public HttpHeaders getHeaders() {
				return headers;
			}
		};
	}
}
