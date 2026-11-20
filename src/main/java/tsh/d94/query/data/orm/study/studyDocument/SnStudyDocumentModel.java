package tsh.d94.app.server.data.orm.study.studyDocument;

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
@Table(name = ApTableName.SN_STUDY_DOCUMENT)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnStudyDocumentModel extends BaseModel {

	@Column(name = "OWNER_ID", length = 200, nullable = false)
	private String ownerId;

	@Column(name = "TITLE", length = 500, nullable = false)
	private String title;

	@Column(name = "FILE_NM", length = 500, nullable = false)
	private String fileNm;

	@Column(name = "FILE_SIZE", nullable = false)
	private Long fileSize;

	@Column(name = "STORAGE_KEY", length = 500)
	private String storageKey;

	@Column(name = "PAGE_COUNT")
	private Integer pageCount;

	@Column(name = "STATUS", length = 20, nullable = false)
	private String status;

	@Column(name = "FAIL_REASON", columnDefinition = "TEXT")
	private String failReason;

	@Column(name = "PARSER_VERSION", length = 20)
	private String parserVersion;

	/** 마지막 읽던 페이지 섹션 (기기 간 이어 읽기) */
	@Column(name = "LAST_SECTION_ID", length = 100)
	private String lastSectionId;

	/** 마지막으로 화면 맨 위에 있던 블록 */
	@Column(name = "LAST_BLOCK_KEY", length = 10)
	private String lastBlockKey;

}
