package tsh.d94.app.server.apService.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 암호화된 자격 증명 봉투. 로그인·회원가입 본문은 {"enc": EncryptedCredential} 하나다.
 * key = RSA-OAEP(SHA-256) 로 감싼 AES-256 키, iv = GCM IV(12바이트), data = GCM 암호문+태그. 모두 base64.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EncryptedCredential {
	private int v;
	private String alg;
	private String kid;
	private String key;
	private String iv;
	private String data;
}
