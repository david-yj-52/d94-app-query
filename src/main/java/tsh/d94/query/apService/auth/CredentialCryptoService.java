package tsh.d94.app.server.apService.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import tsh.d94.app.server.apService.auth.dto.CredentialPublicKeyResponse;
import tsh.d94.app.server.apService.auth.dto.EncryptedCredential;

/**
 * 로그인·회원가입 자격 증명 복호화 (d94-web docs/decisions/0004-credential-encryption.md).
 * 형식: RSA-OAEP(SHA-256, MGF1-SHA256) 로 감싼 AES-256-GCM 키, AAD = "d94-auth:v1:{purpose}".
 * 평문 JSON 의 ts(epoch ms)·nonce 로 재전송을 막는다. 개인키는 환경 변수(AUTH_CREDENTIAL_PRIVATE_KEY)로만 받는다.
 */
@Slf4j
@Service
public class CredentialCryptoService {

	public static final String ALG = "RSA-OAEP-256+A256GCM";
	public static final String PURPOSE_LOGIN = "login";
	public static final String PURPOSE_REGISTER = "register";

	// Java 기본 OAEP 는 MGF1-SHA1 이라 WebCrypto(RSA-OAEP, SHA-256)와 맞지 않는다 → MGF1 도 SHA-256 으로 명시
	static final OAEPParameterSpec OAEP_SHA256 =
		new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
	private static final int GCM_TAG_BITS = 128;
	private static final int GCM_IV_BYTES = 12;
	private static final int AES_KEY_BYTES = 32;

	private final PrivateKey privateKey;
	private final CredentialPublicKeyResponse publicKeyResponse;
	private final long maxAgeMillis;
	private final boolean plaintextAllowed;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	/** nonce → 만료 시각(ms). 단일 인스턴스 전제 (다중 인스턴스면 Redis 로 옮겨야 함) */
	private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();

	@Autowired
	public CredentialCryptoService(
		@Value("${application.security.credential.private-key:}") String privateKeyPem,
		@Value("${application.security.credential.max-age-seconds:300}") long maxAgeSeconds,
		@Value("${application.security.credential.allow-plaintext:false}") boolean plaintextAllowed,
		ObjectMapper objectMapper
	) {
		this(privateKeyPem, maxAgeSeconds, plaintextAllowed, objectMapper, Clock.systemUTC());
	}

	CredentialCryptoService(String privateKeyPem, long maxAgeSeconds, boolean plaintextAllowed,
		ObjectMapper objectMapper, Clock clock) {
		KeyPair keyPair = loadOrGenerate(privateKeyPem);
		byte[] spki = keyPair.getPublic().getEncoded();
		this.privateKey = keyPair.getPrivate();
		this.publicKeyResponse = CredentialPublicKeyResponse.builder()
			.kid(fingerprint(spki))
			.alg(ALG)
			.publicKey(Base64.getEncoder().encodeToString(spki))
			.build();
		this.maxAgeMillis = maxAgeSeconds * 1000;
		this.plaintextAllowed = plaintextAllowed;
		this.objectMapper = objectMapper;
		this.clock = clock;
		if (plaintextAllowed) {
			log.warn("[credential] allow-plaintext=true: 평문 자격 증명도 받습니다. 배포 전환 기간에만 켜세요.");
		}
	}

	public CredentialPublicKeyResponse getPublicKey() {
		return publicKeyResponse;
	}

	public boolean isPlaintextAllowed() {
		return plaintextAllowed;
	}

	/** 봉투를 풀어 ts·nonce 를 뺀 필드(email·password·name)를 돌려준다 */
	public ObjectNode decrypt(EncryptedCredential enc, String purpose) {
		if (enc == null || enc.getV() != 1 || !ALG.equals(enc.getAlg())) {
			throw new CredentialInvalidException("unsupported envelope version/alg");
		}
		if (!publicKeyResponse.getKid().equals(enc.getKid())) {
			// 서버 키가 바뀐 뒤 예전 공개키로 암호화한 경우 — 클라이언트는 공개키를 다시 받아 재시도하면 된다
			throw new CredentialInvalidException("unknown kid " + enc.getKid());
		}

		JsonNode payload;
		try {
			Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
			rsa.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
			byte[] aesKey = rsa.doFinal(decodeBase64(enc.getKey()));
			byte[] iv = decodeBase64(enc.getIv());
			if (aesKey.length != AES_KEY_BYTES || iv.length != GCM_IV_BYTES) {
				throw new CredentialInvalidException("invalid key/iv length");
			}

			Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
			gcm.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
			gcm.updateAAD(aad(purpose));
			payload = objectMapper.readTree(gcm.doFinal(decodeBase64(enc.getData())));
		} catch (CredentialInvalidException e) {
			throw e;
		} catch (Exception e) {
			// 키·용도(AAD) 불일치, 변조, JSON 오류. 예외 메시지에 평문이 섞일 수 있어 클래스 이름만 남긴다
			throw new CredentialInvalidException("decrypt failed: " + e.getClass().getSimpleName());
		}

		if (!(payload instanceof ObjectNode fields)) {
			throw new CredentialInvalidException("payload is not an object");
		}
		verifyFreshness(fields);
		fields.remove("ts");
		fields.remove("nonce");
		return fields;
	}

	static byte[] aad(String purpose) {
		return ("d94-auth:v1:" + purpose).getBytes(StandardCharsets.UTF_8);
	}

	private void verifyFreshness(ObjectNode fields) {
		JsonNode ts = fields.get("ts");
		JsonNode nonce = fields.get("nonce");
		if (ts == null || !ts.canConvertToLong() || nonce == null || !nonce.isTextual() || nonce.asText().isBlank()) {
			throw new CredentialInvalidException("missing ts/nonce");
		}
		long now = clock.millis();
		if (Math.abs(now - ts.asLong()) > maxAgeMillis) {
			throw new CredentialInvalidException("expired ts (skew " + (now - ts.asLong()) + "ms)");
		}
		usedNonces.values().removeIf(expiresAt -> expiresAt < now);
		// 허용 창(±maxAge) 안에서는 다시 들어올 수 있으니 ts + maxAge 까지 기억한다
		if (usedNonces.putIfAbsent(nonce.asText(), ts.asLong() + maxAgeMillis) != null) {
			throw new CredentialInvalidException("replayed nonce");
		}
	}

	private static byte[] decodeBase64(String value) {
		if (value == null) {
			throw new CredentialInvalidException("missing field");
		}
		try {
			return Base64.getDecoder().decode(value);
		} catch (IllegalArgumentException e) {
			throw new CredentialInvalidException("invalid base64");
		}
	}

	private static KeyPair loadOrGenerate(String privateKeyPem) {
		try {
			if (privateKeyPem == null || privateKeyPem.isBlank()) {
				log.warn("[credential] AUTH_CREDENTIAL_PRIVATE_KEY 없음 → 임시 RSA 키를 만듭니다 (재기동하면 바뀜). 운영에서는 반드시 설정하세요.");
				KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
				generator.initialize(2048);
				return generator.generateKeyPair();
			}
			// PEM(-----BEGIN PRIVATE KEY-----) 또는 base64 DER. env 에 넣으면서 생긴 "\n" 문자열도 허용
			String base64 = privateKeyPem
				.replace("\\n", "")
				.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "")
				.replaceAll("\\s", "");
			KeyFactory factory = KeyFactory.getInstance("RSA");
			PrivateKey key = factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
			if (!(key instanceof RSAPrivateCrtKey crt)) {
				throw new IllegalStateException("AUTH_CREDENTIAL_PRIVATE_KEY 는 PKCS#8 RSA(CRT) 개인키여야 합니다.");
			}
			PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
			return new KeyPair(publicKey, key);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("AUTH_CREDENTIAL_PRIVATE_KEY 를 읽을 수 없습니다 (PKCS#8 PEM 또는 base64 DER).", e);
		}
	}

	private static String fingerprint(byte[] spki) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(spki);
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
