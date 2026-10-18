package tsh.d94.app.server.data.orm.cira.ciraVersion;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.envers.Audited;

import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
	name = ApTableName.SN_CIRA_VERSION,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraVersionModel.UK01, columnNames = {"PROJECT_ID", "VERSION_NM"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraVersionModel extends BaseModel {

	public static final String UK01 = "uk_ciraVersion_01";

	@Column(name = "PROJECT_ID", length = 100, nullable = false)
	private String projectId;

	@Column(name = "VERSION_NM", length = 50, nullable = false)
	private String versionNm;

	@Column(name = "DESCR", columnDefinition = "TEXT")
	private String descr;

	@Column(name = "STATUS", length = 50, nullable = false)
	private String status;

	@Column(name = "PLAN_REL_DT")
	private LocalDate planRelDt;

	@Column(name = "RELEASED_AT", columnDefinition = "TIMESTAMP WITH TIME ZONE")
	private OffsetDateTime releasedAt;

}
