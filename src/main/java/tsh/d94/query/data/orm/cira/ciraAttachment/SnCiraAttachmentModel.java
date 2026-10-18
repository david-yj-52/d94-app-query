package tsh.d94.app.server.data.orm.cira.ciraAttachment;

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
	name = ApTableName.SN_CIRA_ATTACHMENT
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraAttachmentModel extends BaseModel {

	public static final String UK01 = "uk_ciraAttachment_01";

	@Column(name = "ISSUE_ID", length = 100, nullable = false)
	private String issueId;

	@Column(name = "COMMENT_ID", length = 100)
	private String commentId;

	@Column(name = "FILE_NM", length = 255, nullable = false)
	private String fileNm;

	@Column(name = "FILE_PATH", length = 1000, nullable = false)
	private String s3Key;

	@Column(name = "FILE_SIZE")
	private Long fileSize;

	@Column(name = "MIME_TYPE", length = 100)
	private String mimeType;

	@Column(name = "UPLOADED_BY", length = 100)
	private String uploadedBy;

}
