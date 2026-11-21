package tsh.d94.app.server.apService.ayaan;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.support.IntegrationTest;

/** V500 스키마·seed 검증 (ayaan phase1 spec 3장, 부록 A). */
@IntegrationTest
class AyaanSchemaIT {

	@Autowired JdbcTemplate jdbc;

	@Test
	void seedConfigHasSpecDefaults() {
		// V545 이관: SN_AYN_CONFIG → SN_SETTING 'ayaan.*'
		assertThat(jdbc.queryForObject("SELECT SETTING_VALUE FROM SN_SETTING WHERE TENANT='TSH' AND SETTING_KEY='ayaan.EXHAUST_POLICY'", String.class))
			.isEqualTo("WAIT_SAME_TOOL");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SN_SETTING WHERE SETTING_KEY LIKE 'ayaan.%'", Integer.class)).isEqualTo(12);
		assertThat(jdbc.queryForObject("SELECT to_regclass('sn_ayn_config') IS NULL", Boolean.class)).isTrue();
	}

	@Test
	void seedToolsOnlyClaudeEnabled() {
		assertThat(jdbc.queryForList("SELECT TOOL_CD FROM SN_AYN_TOOL WHERE ENABLED", String.class)).containsExactly("CLAUDE");
		assertThat(jdbc.queryForObject("SELECT FALLBACK_RESET_RULE FROM SN_AYN_TOOL WHERE TOOL_CD='GEMINI'", String.class))
			.isEqualTo("DAILY 00:00 America/Los_Angeles");
	}

	@Test
	void activeTaskPerIssueIsUnique() {
		String issue = "UQ-" + UUID.randomUUID();
		insertTask(issue, "QUEUED");
		assertThatThrownBy(() -> insertTask(issue, "QUEUED")).isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertTask(issue, "RUNNING")).isInstanceOf(DataIntegrityViolationException.class);
		insertTask(issue, "DONE");
	}

	private void insertTask(String issueKey, String status) {
		jdbc.update("INSERT INTO SN_AYN_TASK (OBJ_ID, ISSUE_KEY, SOURCE, PROJECT_KEY, WORKSPACE_PATH, PROMPT, TOOL_CD, STATUS, RESUME_COUNT) "
			+ "VALUES (?, ?, 'API', 'UQ', '/workspace/uq', 'p', 'CLAUDE', ?, 0)", UUID.randomUUID().toString(), issueKey, status);
	}

}
