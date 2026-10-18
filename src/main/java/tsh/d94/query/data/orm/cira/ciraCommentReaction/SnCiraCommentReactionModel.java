package tsh.d94.app.server.data.orm.cira.ciraCommentReaction;

import org.hibernate.envers.Audited;

import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(
	name = ApTableName.SN_CIRA_COMMENT_REACTION,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraCommentReactionModel.UK01, columnNames = {"COMMENT_ID", "USER_ID",
			"REACTION_TYPE"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraCommentReactionModel extends BaseModel {

	public static final String UK01 = "uk_ciraCommentReaction_01";

	@Column(name = "COMMENT_ID", length = 100, nullable = false)
	private String commentId;

	@Column(name = "USER_ID", length = 100, nullable = false)
	private String userId;

	@Column(name = "REACTION_TYPE", length = 30, nullable = false)
	private String reactionType;

}
