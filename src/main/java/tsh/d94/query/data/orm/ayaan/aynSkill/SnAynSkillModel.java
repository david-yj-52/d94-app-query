package tsh.d94.app.server.data.orm.ayaan.aynSkill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.app.server.data.orm.ayaan.AynRefBase;

@Entity
@Table(name = ApTableName.SN_AYN_SKILL)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynSkillModel extends AynRefBase {

	@Id
	@Column(name = "SKILL_CD", length = 50)
	private String skillCd;

	@Column(name = "SKILL_NM", length = 100, nullable = false)
	private String skillNm;

	@Column(name = "TOOL_CD", length = 20, nullable = false)
	private String toolCd;

	@Column(name = "PHASE", length = 10, nullable = false)
	private String phase;

	@Column(name = "INVOKE_TEXT", length = 500, nullable = false)
	private String invokeText;

	@Column(name = "PLUGIN_REF", length = 200)
	private String pluginRef;

	@Column(name = "DESCRIPTION", columnDefinition = "TEXT")
	private String description;

	@Column(name = "ENABLED", nullable = false)
	private Boolean enabled;

	@Column(name = "SORT_NO", nullable = false)
	private Integer sortNo;

}
