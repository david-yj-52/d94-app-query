package tsh.d94.app.server.data.orm.masterPrompt.stMptRoleTmpltRel;

import org.hibernate.annotations.Comment;
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
	name = ApTableName.ST_MPT_ROLE_TMPLT_REL,
	uniqueConstraints = {
		@UniqueConstraint(name = StMptWrkrTmpltRelModel.UK01, columnNames = {"workerId", "templateId"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
// TMPLT = TEMPLATE
public class StMptWrkrTmpltRelModel extends BaseModel {

	public static final String UK01 = "uk_mpt_wrkr_tmplt_01";
	@Comment("worker id")
	@Column(name = "WRKR_ID", nullable = false, length = 100)
	private String workerId;

	@Comment("template id")
	@Column(name = "TMPL_ID", nullable = false, length = 100)
	private String templateId;

}
