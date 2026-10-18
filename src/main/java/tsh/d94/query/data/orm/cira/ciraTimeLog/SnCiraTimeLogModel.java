package tsh.d94.app.server.data.orm.cira.ciraTimeLog;

import org.hibernate.envers.Audited;

import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
	name = ApTableName.SN_CIRA_TIME_LOG
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraTimeLogModel extends BaseModel {

	public static final String UK01 = "uk_ciraTimeLog_01";

	@Column(name = "ISSUE_ID", length = 100, nullable = false)
	private String issueId;

	@Column(name = "USER_ID", length = 100, nullable = false)
	private String userId;

	@Column(name = "LOG_HRS", nullable = false)
	private java.math.BigDecimal logHrs;

	@Column(name = "LOG_DT", nullable = false)
	private java.time.LocalDate logDt;

	@Column(name = "DESCR")
	private String descr;

}
