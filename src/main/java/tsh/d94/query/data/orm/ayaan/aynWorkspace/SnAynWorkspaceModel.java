package tsh.d94.app.server.data.orm.ayaan.aynWorkspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import tsh.d94.app.server.constant.ApTableName;
import tsh.d94.app.server.data.orm.ayaan.AynRefBase;

@Entity
@Table(name = ApTableName.SN_AYN_WORKSPACE)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class SnAynWorkspaceModel extends AynRefBase {

	@Id
	@Column(name = "PROJECT_KEY", length = 50)
	private String projectKey;

	@Column(name = "WORKSPACE_PATH", length = 500, nullable = false)
	private String workspacePath;

	@Column(name = "DEFAULT_TOOL", length = 20, nullable = false)
	private String defaultTool;

	@Column(name = "PROMPT_TEMPLATE", columnDefinition = "TEXT")
	private String promptTemplate;

}
