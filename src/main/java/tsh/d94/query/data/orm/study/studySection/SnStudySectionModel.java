package tsh.d94.app.server.data.orm.study.studySection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/** ponytail: @Audited 미사용 — 블록 수만 행의 _HIST 기록이 필요 없다. 삭제는 물리 삭제. */
@Entity
@Table(name = ApTableName.SN_STUDY_SECTION)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnStudySectionModel extends BaseModel {

	@Column(name = "DOCUMENT_ID", length = 100, nullable = false)
	private String documentId;

	@Column(name = "PARENT_ID", length = 100)
	private String parentId;

	@Column(name = "SECTION_KEY", length = 10, nullable = false)
	private String sectionKey;

	@Column(name = "LVL", nullable = false)
	private Integer lvl;

	@Column(name = "TITLE", length = 1000, nullable = false)
	private String title;

	@Column(name = "ORD", nullable = false)
	private Integer ord;

	@Column(name = "PAGE_NO")
	private Integer pageNo;

	@Column(name = "ANCHOR_BLOCK_KEY", length = 10)
	private String anchorBlockKey;

	@Column(name = "PAGE_SECTION_ID", length = 100)
	private String pageSectionId;

}
