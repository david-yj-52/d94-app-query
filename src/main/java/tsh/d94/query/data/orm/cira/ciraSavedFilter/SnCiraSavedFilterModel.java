package tsh.d94.app.server.data.orm.cira.ciraSavedFilter;

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
	name = ApTableName.SN_CIRA_SAVED_FILTER
)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@Audited
public class SnCiraSavedFilterModel extends BaseModel {

	public static final String UK01 = "uk_ciraSavedFilter_01";

	@Column(name = "USER_ID", length = 100, nullable = false)
	private String userId;

	@Column(name = "PROJECT_ID", length = 100)
	private String projectId;

	@Column(name = "FILTER_NM", length = 200, nullable = false)
	private String filterNm;

	@Column(name = "JQL_QUERY")
	private String jqlQuery;

	@Column(name = "SHARED_YN", length = 1, nullable = false)
	private String sharedYn;

}
