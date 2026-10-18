package tsh.d94.app.server.data.orm.cira.ciraWikiPage;

import org.hibernate.envers.Audited;

import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
	name = ApTableName.SN_CIRA_WIKI_PAGE
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraWikiPageModel extends BaseModel {

	@Column(name = "PROJECT_ID", length = 100, nullable = false)
	private String projectId;

	@Column(name = "PARENT_ID", length = 100)
	private String parentId;

	@Column(name = "TITLE", length = 500, nullable = false)
	private String title;

	@Column(name = "CONTENT", columnDefinition = "TEXT")
	private String content;

	@Column(name = "CONTENT_HTML", columnDefinition = "TEXT")
	private String contentHtml;

	@Column(name = "AUTHOR_ID", length = 100)
	private String authorId;

	@Column(name = "SORT_ORDER")
	private Integer sortOrder;

	@Column(name = "VERSION")
	private Integer version;

}
