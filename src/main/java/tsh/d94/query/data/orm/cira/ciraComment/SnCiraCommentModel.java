package tsh.d94.app.server.data.orm.cira.ciraComment;

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
	name = ApTableName.SN_CIRA_COMMENT
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraCommentModel extends BaseModel {

	public static final String UK01 = "uk_ciraComment_01";

	@Column(name = "ISSUE_ID", length = 100, nullable = false)
	private String issueId;

	@Column(name = "AUTHOR_ID", length = 100, nullable = false)
	private String authorId;

	@Column(name = "PARENT_ID", length = 100)
	private String parentId;

	@Column(name = "CONTENT")
	private String content;

}
