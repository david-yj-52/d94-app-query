package tsh.d94.app.server.data.orm.organization.stOrgUnitDef;

import org.hibernate.annotations.Comment;
import org.hibernate.envers.Audited;

import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.app.server.data.orm.organization.constant.OrgUnitTyp;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
	name = ApTableName.ST_ORG_UNIT_DEF,
	uniqueConstraints = {
		@UniqueConstraint(name = StOrgUnitDefModel.UK01, columnNames = {"unitId", "unitTyp", "unitNm"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class StOrgUnitDefModel extends BaseModel {

	public static final String UK01 = "uk_org_unit_def_01";

	@Comment("Unit id")
	@Column(name = "UNIT_ID", nullable = false, length = 100)
	private String unitId;

	@Comment("Definition level")
	@Column(name = "UNIT_TYP", nullable = false, length = 100)
	@Enumerated(EnumType.STRING)
	private OrgUnitTyp unitTyp;

	@Comment("Unit name")
	@Column(name = "UNIT_NM", nullable = false, length = 100)
	private String unitNm;

	@Comment("Unit description")
	@Column(name = "UNIT_DESC", nullable = false)
	private String unitDesc;

	@Comment("Vision per unit")
	@Column(name = "UNIT_VISION", nullable = false)
	private String unitVision;
}
