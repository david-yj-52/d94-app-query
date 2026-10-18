package tsh.d94.app.server.data.orm.cira.ciraProjectMember;

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
	name = ApTableName.SN_CIRA_PROJECT_MEMBER,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraProjectMemberModel.UK01, columnNames = {"PROJECT_ID", "USER_ID"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraProjectMemberModel extends BaseModel {

	public static final String UK01 = "uk_ciraProjectMember_01";

	@Column(name = "PROJECT_ID", length = 100, nullable = false)
	private String projectId;

	@Column(name = "USER_ID", length = 100, nullable = false)
	private String userId;

	@Column(name = "ROLE", length = 50, nullable = false)
	private String role;

}
