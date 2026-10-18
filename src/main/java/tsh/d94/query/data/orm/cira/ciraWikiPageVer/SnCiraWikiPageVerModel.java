package tsh.d94.app.server.data.orm.cira.ciraWikiPageVer;

import java.time.OffsetDateTime;

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
	name = ApTableName.SN_CIRA_WIKI_PAGE_VER
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraWikiPageVerModel extends BaseModel {

	@Column(name = "PAGE_ID", length = 100, nullable = false)
	private String pageId;

	@Column(name = "VERSION", nullable = false)
	private Integer version;

	@Column(name = "CONTENT", columnDefinition = "TEXT")
	private String content;

	@Column(name = "EDITED_BY", length = 100)
	private String editedBy;

	@Column(name = "EDITED_AT")
	private OffsetDateTime editedAt;

}
