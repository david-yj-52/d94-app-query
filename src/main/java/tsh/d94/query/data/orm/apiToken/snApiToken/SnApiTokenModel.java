package tsh.d94.app.server.data.orm.apiToken.snApiToken;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/** API 토큰. 원문은 저장하지 않는다. ponytail: @Audited 미사용 — LAST_USED_AT 갱신마다 _HIST 가 쌓이므로 */
@Entity
@Table(name = ApTableName.SN_API_TOKEN)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnApiTokenModel extends BaseModel {

	@Column(name = "OWNER_ID", length = 200, nullable = false)
	private String ownerId;

	@Column(name = "SERVICE_CD", length = 40, nullable = false)
	private String serviceCd;

	@Column(name = "TOKEN_NM", length = 100, nullable = false)
	private String tokenNm;

	@Column(name = "TOKEN_HASH", length = 64, nullable = false, columnDefinition = "CHAR(64)")
	private String tokenHash;

	@Column(name = "TOKEN_PREFIX", length = 20, nullable = false)
	private String tokenPrefix;

	@Column(name = "ACCESS_CD", length = 10, nullable = false)
	private String accessCd;

	@Column(name = "EXPIRES_AT", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "LAST_USED_AT")
	private LocalDateTime lastUsedAt;

	@Column(name = "REVOKED_AT")
	private LocalDateTime revokedAt;
}
