package tsh.d94.app.server.apService.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 자격 증명 암호화용 공개키. publicKey = X.509 SPKI DER 의 base64 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CredentialPublicKeyResponse {
	private String kid;
	private String alg;
	private String publicKey;
}
