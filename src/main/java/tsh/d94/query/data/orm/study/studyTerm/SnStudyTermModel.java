package tsh.d94.app.server.data.orm.study.studyTerm;

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
@Table(name = ApTableName.SN_STUDY_TERM)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnStudyTermModel extends BaseModel {

	@Column(name = "OWNER_ID", length = 200, nullable = false)
	private String ownerId;

	@Column(name = "DOCUMENT_ID", length = 100)
	private String documentId;

	@Column(name = "TERM", length = 300, nullable = false)
	private String term;

	@Column(name = "NORM_TERM", length = 300, nullable = false)
	private String normTerm;

	@Column(name = "DEFINITION", columnDefinition = "TEXT", nullable = false)
	private String definition;

}
