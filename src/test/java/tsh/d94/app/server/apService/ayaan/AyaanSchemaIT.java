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
		assertThat(jdbc.queryForObject("SELECT CONFIG_VALUE FROM SN_AYN_CONFIG WHERE CONFIG_KEY='EXHAUST_POLICY'", String.class))
			.isEqualTo("WAIT_SAME_TOOL");
		// 다른 IT 가 키를 추가하므로 시드 행만 센다
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SN_AYN_CONFIG WHERE CREATED_BY = 'flyway'", Integer.class)).isEqualTo(12);
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

	@Test
	void waitingTaskCountsAsActiveForIssue() {
		String issue = "UW-" + UUID.randomUUID();
		insertTask(issue, "WAITING");
		assertThatThrownBy(() -> insertTask(issue, "QUEUED")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void seedSkillsPermissionsAndNotifyEvents() {
		assertThat(jdbc.queryForList("SELECT SKILL_CD FROM SN_AYN_SKILL ORDER BY SORT_NO", String.class))
			.containsExactly("BRAINSTORM", "PONYTAIL", "EXEC_PLAN");
		// CLAUDE 기본 skill 시드는 여기서 보지 않는다: AyaanInternalIT 가 PUT /tools/CLAUDE 로 nullable 필드를 지운다
		assertThat(jdbc.queryForObject("SELECT DECISION FROM SN_AYN_PERMISSION WHERE CATEGORY='GIT_PUSH' AND TOOL_CD='*'", String.class))
			.isEqualTo("ASK");
		assertThat(jdbc.queryForObject("SELECT CONFIG_VALUE FROM SN_AYN_CONFIG WHERE CONFIG_KEY='NOTIFY_EVENTS'", String.class))
			.endsWith(",WAITING,PLAN_DONE");
	}

	private void insertTask(String issueKey, String status) {
		jdbc.update("INSERT INTO SN_AYN_TASK (OBJ_ID, ISSUE_KEY, SOURCE, PROJECT_KEY, WORKSPACE_PATH, PROMPT, TOOL_CD, STATUS, RESUME_COUNT) "
			+ "VALUES (?, ?, 'API', 'UQ', '/workspace/uq', 'p', 'CLAUDE', ?, 0)", UUID.randomUUID().toString(), issueKey, status);
	}

}
