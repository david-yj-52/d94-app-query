package tsh.d94.app.server.apService.auth;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import tsh.d94.app.server.apService.auth.dto.EncryptedCredential;

/** 테스트용: d94-web encryptCredential 과 같은 방식으로 Java 에서 봉투를 만든다 */
public final class CredentialCryptoServiceTestSupport {

	private CredentialCryptoServiceTestSupport() {
	}

	public static EncryptedCredential encrypt(CredentialCryptoService service, String json, String purpose) throws Exception {
		PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
			new X509EncodedKeySpec(Base64.getDecoder().decode(service.getPublicKey().getPublicKey())));
		SecureRandom random = new SecureRandom();
		byte[] aesKey = new byte[32];
		byte[] iv = new byte[12];
		random.nextBytes(aesKey);
		random.nextBytes(iv);

		Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
		gcm.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new GCMParameterSpec(128, iv));
		gcm.updateAAD(CredentialCryptoService.aad(purpose));
		byte[] data = gcm.doFinal(json.getBytes(StandardCharsets.UTF_8));

		Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
		rsa.init(Cipher.ENCRYPT_MODE, publicKey, CredentialCryptoService.OAEP_SHA256);

		Base64.Encoder b64 = Base64.getEncoder();
		return EncryptedCredential.builder()
			.v(1)
			.alg(CredentialCryptoService.ALG)
			.kid(service.getPublicKey().getKid())
			.key(b64.encodeToString(rsa.doFinal(aesKey)))
			.iv(b64.encodeToString(iv))
			.data(b64.encodeToString(data))
			.build();
	}
}
