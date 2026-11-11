package tsh.d94.app.server.data.orm.ayaan.aynToolQuota;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.app.server.data.orm.ayaan.AynRefBase;

@Entity
@Table(name = ApTableName.SN_AYN_TOOL_QUOTA)
@IdClass(SnAynToolQuotaModel.Key.class)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynToolQuotaModel extends AynRefBase {

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class Key implements Serializable {

		private String toolCd;
		private String windowCd;

	}

	@Id
	@Column(name = "TOOL_CD", length = 20)
	private String toolCd;

	@Id
	@Column(name = "WINDOW_CD", length = 20)
	private String windowCd;

	@Column(name = "USED_PCT", precision = 5, scale = 2)
	private BigDecimal usedPct;

	@Column(name = "USED_TOKENS")
	private Long usedTokens;

	@Column(name = "LIMIT_TOKENS")
	private Long limitTokens;

	@Column(name = "RESETS_AT")
	private LocalDateTime resetsAt;

	@Column(name = "STATUS", length = 20, nullable = false)
	private String status;

	@Column(name = "SOURCE", length = 20, nullable = false)
	private String source;

	@Column(name = "OBSERVED_AT", nullable = false)
	private LocalDateTime observedAt;

}
