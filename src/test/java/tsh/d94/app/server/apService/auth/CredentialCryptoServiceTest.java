package tsh.d94.app.server.apService.auth;

import static org.assertj.core.api.Assertions.*;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import tsh.d94.app.server.apService.auth.dto.EncryptedCredential;

/**
 * web-envelope-fixture.json 은 d94-web src/lib/crypto/credential.ts(encryptCredential)를 Node 로 실행해 만든 값이다.
 * 픽스처의 개인키는 이 테스트 전용으로 생성한 키다 (운영 키 아님).
 */
class CredentialCryptoServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private JsonNode fixture;
	private long fixtureTs;

	@BeforeEach
	void setUp() throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/credential/web-envelope-fixture.json")) {
			fixture = objectMapper.readTree(in);
		}
		fixtureTs = fixture.get("ts").asLong();
	}

	private CredentialCryptoService fixtureService(long nowMillis) {
		Clock clock = Clock.fixed(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC);
		return new CredentialCryptoService(fixture.get("privateKeyPem").asText(), 300, false, objectMapper, clock);
	}

	/** 픽스처 kid 는 임의값 → 서비스가 계산한 kid 로 바꿔 넣는다 (kid 는 키 선택용이라 암호문과 무관) */
	private EncryptedCredential fixtureEnvelope(String name, CredentialCryptoService service) throws Exception {
		EncryptedCredential enc = objectMapper.treeToValue(fixture.get(name), EncryptedCredential.class);
		enc.setKid(service.getPublicKey().getKid());
		return enc;
	}

	@Test
	@DisplayName("웹 모듈이 만든 로그인 봉투를 복호화하고 ts·nonce 를 제거한다")
	void decryptsWebLoginEnvelope() throws Exception {
		CredentialCryptoService service = fixtureService(fixtureTs + 1_000);

		ObjectNode fields = service.decrypt(fixtureEnvelope("login", service), CredentialCryptoService.PURPOSE_LOGIN);

		assertThat(fields.get("email").asText()).isEqualTo("user@test.com");
		assertThat(fields.get("password").asText()).isEqualTo("p@ssw0rd!한글");
		assertThat(fields.has("ts")).isFalse();
		assertThat(fields.has("nonce")).isFalse();
	}

	@Test
	@DisplayName("웹 모듈이 만든 회원가입 봉투를 복호화한다")
	void decryptsWebRegisterEnvelope() throws Exception {
		CredentialCryptoService service = fixtureService(fixtureTs);

		ObjectNode fields = service.decrypt(fixtureEnvelope("register", service), CredentialCryptoService.PURPOSE_REGISTER);

		assertThat(fields.get("email").asText()).isEqualTo("new@test.com");
		assertThat(fields.get("name").asText()).isEqualTo("홍길동");
		assertThat(fields.get("password").asText()).isEqualTo("Passw0rd");
	}

	@Test
	@DisplayName("같은 봉투를 다시 보내면 거절한다 (nonce 재사용)")
	void rejectsReplay() throws Exception {
		CredentialCryptoService service = fixtureService(fixtureTs);
		EncryptedCredential enc = fixtureEnvelope("login", service);
		service.decrypt(enc, CredentialCryptoService.PURPOSE_LOGIN);

		assertThatThrownBy(() -> service.decrypt(enc, CredentialCryptoService.PURPOSE_LOGIN))
			.isInstanceOf(CredentialInvalidException.class)
			.hasMessageContaining("replayed");
	}

	@Test
	@DisplayName("허용 시간(300초)을 넘긴 봉투는 거절한다")
	void rejectsExpired() throws Exception {
		CredentialCryptoService service = fixtureService(fixtureTs + 301_000);

		assertThatThrownBy(() -> service.decrypt(fixtureEnvelope("login", service), CredentialCryptoService.PURPOSE_LOGIN))
			.isInstanceOf(CredentialInvalidException.class)
			.hasMessageContaining("expired");
	}

	@Test
	@DisplayName("로그인 봉투를 회원가입 용도로 쓰면 거절한다 (AAD 불일치)")
	void rejectsPurposeMismatch() throws Exception {
		CredentialCryptoService service = fixtureService(fixtureTs);

		assertThatThrownBy(() -> service.decrypt(fixtureEnvelope("login", service), CredentialCryptoService.PURPOSE_REGISTER))
			.isInstanceOf(CredentialInvalidException.class)
			.hasMessageContaining("decrypt failed");
	}

	@Test
	@DisplayName("암호문이 변조되면 거절한다 (GCM 태그 검증)")
	void rejectsTampered() throws Exception {
		CredentialCryptoService service = fixtureService(fixtureTs);
		EncryptedCredential enc = fixtureEnvelope("login", service);
		byte[] data = Base64.getDecoder().decode(enc.getData());
		data[0] ^= 1;
		enc.setData(Base64.getEncoder().encodeToString(data));

		assertThatThrownBy(() -> service.decrypt(enc, CredentialCryptoService.PURPOSE_LOGIN))
			.isInstanceOf(CredentialInvalidException.class);
	}

	@Test
	@DisplayName("다른 키(kid)로 암호화한 봉투는 거절한다")
	void rejectsUnknownKid() throws Exception {
		CredentialCryptoService service = fixtureService(fixtureTs);
		EncryptedCredential enc = fixtureEnvelope("login", service);
		enc.setKid("other");

		assertThatThrownBy(() -> service.decrypt(enc, CredentialCryptoService.PURPOSE_LOGIN))
			.isInstanceOf(CredentialInvalidException.class)
			.hasMessageContaining("unknown kid");
	}

	@Test
	@DisplayName("개인키 설정이 없으면 임시 키를 만들고, 그 공개키로 암호화한 값을 복호화한다")
	void ephemeralKeyRoundTrip() throws Exception {
		CredentialCryptoService service = new CredentialCryptoService("", 300, false, objectMapper);
		String payload = "{\"email\":\"a@b.com\",\"password\":\"pw\",\"ts\":" + System.currentTimeMillis()
			+ ",\"nonce\":\"n-1\"}";

		ObjectNode fields = service.decrypt(CredentialCryptoServiceTestSupport.encrypt(service, payload, "login"), CredentialCryptoService.PURPOSE_LOGIN);

		assertThat(fields.get("email").asText()).isEqualTo("a@b.com");
	}

	@Test
	@DisplayName("ts·nonce 가 없는 평문은 거절한다")
	void rejectsMissingFreshnessFields() throws Exception {
		CredentialCryptoService service = new CredentialCryptoService("", 300, false, objectMapper);

		assertThatThrownBy(() -> service.decrypt(CredentialCryptoServiceTestSupport.encrypt(service, "{\"email\":\"a@b.com\"}", "login"), "login"))
			.isInstanceOf(CredentialInvalidException.class)
			.hasMessageContaining("missing ts/nonce");
	}

	@Test
	@DisplayName("읽을 수 없는 개인키 설정이면 기동 시 실패한다")
	void failsOnInvalidPrivateKey() {
		assertThatThrownBy(() -> new CredentialCryptoService("not-a-key", 300, false, objectMapper))
			.isInstanceOf(IllegalStateException.class);
	}
}
