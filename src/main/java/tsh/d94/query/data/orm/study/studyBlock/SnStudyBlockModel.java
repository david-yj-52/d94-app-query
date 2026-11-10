package tsh.d94.app.server.data.orm.study.studyBlock;

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
@Table(name = ApTableName.SN_STUDY_BLOCK)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnStudyBlockModel extends BaseModel {

	@Column(name = "DOCUMENT_ID", length = 100, nullable = false)
	private String documentId;

	@Column(name = "SECTION_ID", length = 100, nullable = false)
	private String sectionId;

	@Column(name = "BLOCK_KEY", length = 10, nullable = false)
	private String blockKey;

	@Column(name = "PARENT_BLOCK_KEY", length = 10)
	private String parentBlockKey;

	@Column(name = "ORD", nullable = false)
	private Integer ord;

	@Column(name = "BLOCK_TYP", length = 20, nullable = false)
	private String blockTyp;

	@Column(name = "LVL")
	private Integer lvl;

	@Column(name = "BLOCK_TEXT", columnDefinition = "TEXT")
	private String text;

	@Column(name = "PAGE_NO")
	private Integer pageNo;

	@Column(name = "ROW_IDX")
	private Integer rowIdx;

	@Column(name = "COL_IDX")
	private Integer colIdx;

	@Column(name = "FIGURE_KEY", length = 300)
	private String figureKey;

}
