package tsh.d94.app.server.data.orm.cira.ciraNotifPref;

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
	name = ApTableName.SN_CIRA_NOTIF_PREF,
	uniqueConstraints = {
		@UniqueConstraint(name = SnCiraNotifPrefModel.UK01, columnNames = {"USER_ID", "CHANNEL", "EVENT_TYPE"})
	}
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraNotifPrefModel extends BaseModel {

	public static final String UK01 = "uk_ciraNotifPref_01";

	@Column(name = "USER_ID", length = 100, nullable = false)
	private String userId;

	@Column(name = "CHANNEL", length = 30, nullable = false)
	private String channel;

	@Column(name = "EVENT_TYPE", length = 50, nullable = false)
	private String eventType;

	@Column(name = "ENABLED_YN", length = 1, nullable = false)
	private String enabledYn;

}
