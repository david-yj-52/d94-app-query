package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveType;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/**
 * V650: 공간 개념 이전 모양(프로젝트에 Default 공간 없음, SPACE_ID null, 타입 세트 참조)의 데이터를
 * Default 공간 소유로 백필하는지. Flyway 가 이미 V650 을 적용한 DB 에 옛 모양을 만든 뒤 스크립트를 다시 실행한다(멱등).
 */
@IntegrationTest
class CiraSpaceMigrationIT {

	private static final String V650 = "db/migration/V650__cira_space_feature_docs.sql";

	@Autowired CiraFixtures fixtures;
	@Autowired CiraFieldService fields;
	@Autowired JdbcTemplate jdbc;
	@Autowired DataSource dataSource;

	@AfterEach
	void tearDown() {
		jdbc.execute("alter table SN_CIRA_PROJECT drop column if exists PROJECT_TYPE_ID");
		TestContexts.clear();
	}

	@Test
	void backfillsDefaultSpace_andExpandsTypeFieldSet() throws Exception {
		String email = CiraFixtures.uniqueEmail("mig");
		TestContexts.login(email);
		String ownerId = fixtures.user(email).getObjId();
		ProjectResponse p = fixtures.project("KANBAN");
		String issueId = fixtures.issue(p.getId(), "옛 이슈", null).getId();
		// 다른 테스트와 겹치지 않게 이 테스트 전용 타입
		String typeId = fields.createType(new SaveType("MIG-" + java.util.UUID.randomUUID().toString().substring(0, 6), null)).id();
		// 옛 모양: V660 이 지운 프로젝트 타입 칼럼을 되살린다 (tearDown 에서 다시 지움)
		jdbc.execute("alter table SN_CIRA_PROJECT add column if not exists PROJECT_TYPE_ID varchar(100)");
		jdbc.update("update SN_CIRA_PROJECT set PROJECT_TYPE_ID=? where OBJ_ID=?", typeId, p.getId());

		// 옛 모양으로 되돌린다: 공간 없음, SPACE_ID null, 필드 세트 = 타입 세트(참조) + 프로젝트 추가 1개
		String pid = p.getId();
		for (String t : List.of("SN_CIRA_ISSUE", "SN_CIRA_ISSUE_STATUS", "SN_CIRA_ISSUE_TRANSITION", "SN_CIRA_BOARD",
			"SN_CIRA_PROJECT_FIELD", "SN_CIRA_USER_COLUMN_PREF")) {
			jdbc.update("update " + t + " set SPACE_ID=null where PROJECT_ID=?", pid);
		}
		jdbc.update("delete from SN_CIRA_PROJECT_FIELD where PROJECT_ID=?", pid);
		jdbc.update("delete from SN_CIRA_SPACE where PROJECT_ID=?", pid);
		jdbc.update("delete from SN_CIRA_PROJECT_TYPE_FIELD where PROJECT_TYPE_ID=?", typeId); // 이 테스트 전용 타입
		jdbc.update("insert into SN_CIRA_PROJECT_TYPE_FIELD(PROJECT_TYPE_ID, FIELD_KEY, SORT_ORD, DEFAULT_VISIBLE_YN) values(?,?,1,'N')",
			typeId, "sys:priority");
		jdbc.update("insert into SN_CIRA_PROJECT_FIELD(PROJECT_ID, FIELD_KEY, SORT_ORD) values(?,?,1)", pid, "sys:dueDate");
		jdbc.update("insert into SN_CIRA_PROJECT_FIELD(PROJECT_ID, FIELD_KEY, SORT_ORD) values(?,?,2)", pid, "sys:priority"); // 타입과 중복

		// 옛 모양에는 SPACE_ID 유니크가 없었다 — 다시 실행하려면 V650 이 만든 제약을 먼저 지운다
		jdbc.execute("alter table SN_CIRA_PROJECT_FIELD drop constraint if exists uk_ciraProjectField_02");
		jdbc.execute("alter table SN_CIRA_USER_COLUMN_PREF drop constraint if exists uk_ciraUserColumnPref_02");
		jdbc.execute("alter table SN_CIRA_ISSUE_STATUS drop constraint if exists uk_ciraIssueStatus_02");
		try (Connection c = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(c, new ClassPathResource(V650));
		}

		Map<String, Object> space = jdbc.queryForMap("select OBJ_ID, ADMIN_USER_ID, IS_DEFAULT_YN from SN_CIRA_SPACE where PROJECT_ID=?", pid);
		String sid = (String)space.get("OBJ_ID");
		assertThat(space.get("ADMIN_USER_ID")).isEqualTo(ownerId);
		assertThat(space.get("IS_DEFAULT_YN")).isEqualTo("Y");
		assertThat(jdbc.queryForObject("select SPACE_ID from SN_CIRA_ISSUE where OBJ_ID=?", String.class, issueId)).isEqualTo(sid);
		for (String t : List.of("SN_CIRA_ISSUE_STATUS", "SN_CIRA_ISSUE_TRANSITION", "SN_CIRA_BOARD")) {
			assertThat(jdbc.queryForObject("select count(*) from " + t + " where PROJECT_ID=? and SPACE_ID is distinct from ?",
				Integer.class, pid, sid)).as(t).isZero();
		}
		// 타입 세트(기본 표시 N 유지) → 프로젝트 추가, 중복 키는 한 번만
		assertThat(jdbc.queryForList("select FIELD_KEY || ':' || DEFAULT_VISIBLE_YN from SN_CIRA_PROJECT_FIELD where SPACE_ID=? order by SORT_ORD",
			String.class, sid)).containsExactly("sys:priority:N", "sys:dueDate:Y");
		assertThat(jdbc.queryForObject("select count(*) from SN_CIRA_CIRA_ISSUE_TYPE where TYPE_NM='Epic'", Integer.class)).isZero();
	}
}
