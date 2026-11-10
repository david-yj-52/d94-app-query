package tsh.d94.app.server.data.orm.study.studyAnnotation;

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
@Table(name = ApTableName.SN_STUDY_ANNOTATION)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnStudyAnnotationModel extends BaseModel {

	@Column(name = "OWNER_ID", length = 200, nullable = false)
	private String ownerId;

	@Column(name = "DOCUMENT_ID", length = 100, nullable = false)
	private String documentId;

	@Column(name = "START_BLOCK_KEY", length = 10, nullable = false)
	private String startBlockKey;

	@Column(name = "START_OFFSET", nullable = false)
	private Integer startOffset;

	@Column(name = "END_BLOCK_KEY", length = 10, nullable = false)
	private String endBlockKey;

	@Column(name = "END_OFFSET", nullable = false)
	private Integer endOffset;

	@Column(name = "QUOTE", columnDefinition = "TEXT", nullable = false)
	private String quote;

	@Column(name = "COLOR", length = 10, nullable = false)
	private String color;

	@Column(name = "NOTE", columnDefinition = "TEXT")
	private String note;

}
