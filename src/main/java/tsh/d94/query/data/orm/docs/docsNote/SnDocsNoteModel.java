package tsh.d94.app.server.data.orm.docs.docsNote;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/**
 * 노트. CONTENT(Markdown 원문)가 원천이고 KIND·STREAM 은 frontmatter 색인.
 * ponytail: @Audited 미사용 — 버전은 SN_DOCS_NOTE_VER. 물리 삭제. 컬럼 길이는 V570 과 짝.
 */
@Entity
@Table(name = ApTableName.SN_DOCS_NOTE)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnDocsNoteModel extends BaseModel {

	@Column(name = "OWNER_ID", length = 200, nullable = false)
	private String ownerId;

	/** null = vault 최상위 */
	@Column(name = "FOLDER_ID")
	private String folderId;

	@Column(name = "TITLE", length = 500, nullable = false)
	private String title;

	@Column(name = "CONTENT", columnDefinition = "text")
	private String content;

	@Column(name = "KIND", length = 20, nullable = false)
	private String kind;

	@Column(name = "STREAM", length = 50)
	private String stream;

	@Column(name = "VERSION", nullable = false)
	private Integer version;
}
