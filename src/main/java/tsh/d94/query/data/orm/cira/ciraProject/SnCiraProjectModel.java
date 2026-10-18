package tsh.d94.app.server.data.orm.cira.ciraProject;

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
	name = ApTableName.SN_CIRA_PROJECT,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraProjectModel.UK01, columnNames = {"PROJECT_KEY"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraProjectModel extends BaseModel {

	public static final String UK01 = "uk_ciraProject_01";

	@Column(name = "PROJECT_KEY", length = 20, nullable = false)
	private String projectKey;

	@Column(name = "PROJECT_NM", length = 200, nullable = false)
	private String projectNm;

	@Column(name = "DESCR")
	private String descr;

	@Column(name = "PROJECT_TYPE", length = 30, nullable = false)
	private String projectType;

	@Column(name = "OWNER_ID", length = 100)
	private String ownerId;

	@Column(name = "ISSUE_SEQUENCE", nullable = false)
	private Integer issueSequence;

	@Column(name = "DELETED_AT")
	private java.time.LocalDateTime deletedAt;

}
