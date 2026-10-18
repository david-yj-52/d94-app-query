package tsh.d94.app.server.data.orm.organization.stOrgWrkrRel;

import org.hibernate.annotations.Comment;
import org.hibernate.envers.Audited;

import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

// WRKR = WORKER
@Entity
@Table(
	name = ApTableName.ST_ORG_WRKR_REL,
	uniqueConstraints = {
		@UniqueConstraint(name = StOrgWrkrRelModel.UK01, columnNames = {"workerId", "workerNm"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class StOrgWrkrRelModel extends BaseModel {

	public static final String UK01 = "uk_org_wrkr_rel_01";

	@Comment("worker id")
	@Column(name = "WRKR_ID", nullable = false, length = 100)
	private String workerId;

	@Comment("worker name")
	@Column(name = "WRKR_NM", nullable = false, length = 100)
	private String workerNm;

	@Comment("organization company id")
	@Column(name = "ORG_COMPANY_ID", nullable = false, length = 100)
	private String orgCompanyId;

	@Comment("organization division(부서) name")
	@Column(name = "ORG_DIVISION_ID", nullable = false, length = 100)
	private String orgDivisionId;

	@Comment("organization team(팀) name")
	@Column(name = "ORG_TEAM_ID", nullable = false, length = 100)
	private String orgTeamId;

	@Comment("organization System or separated job operate unit (단위 업무 수행)")
	@Column(name = "ORG_MODULE_ID", nullable = false, length = 100)
	private String orgModuleId;

	@Comment("organization task name ex) system operation role.")
	@Column(name = "ORG_ROLE_ID", nullable = false)
	private String orgRoleId;

	@Comment("level of employee. 1: min ~ 10: max")
	@Min(value = 1, message = "level should be over 1.")
	@Max(value = 10, message = "level should be under 10.")
	@Column(name = "ROLE_LEVEL", nullable = false)
	private int roleLevel;

	@Comment("context about this job considered it's level")
	@Column(name = "KNOWLEDGE_BACKGROUND", nullable = false)
	private String knowledgeBackground;
}
