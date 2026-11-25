package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveType;
import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.dto.UpdateProjectRequest;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/**
 * V600: 이전 버전(V590)에서 전역 필드를 타입 세트·프로젝트 추가 필드에 "참조"로 둔 데이터를
 * 새 규칙(가져갈 때 복사)으로 바꾸는지. Flyway 가 이미 V600 을 적용한 DB 에 옛 모양 데이터를 만든 뒤 스크립트를 다시 실행한다.
 */
@IntegrationTest
class CiraFieldLibraryMigrationIT {

	private static final String V600 = "db/migration/V600__cira_field_library.sql";

	@Autowired
	private CiraFieldService fields;
	@Autowired
	private ProjectService projects;
	@Autowired
	private IssueService issues;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private DataSource dataSource;

	private String typeT;
	private String globalG;
	private ProjectResponse p1;
	private ProjectResponse p2;
	private String issue1;
	private String issue2;

	@BeforeEach
	void setUp() {
		String email = CiraFixtures.uniqueEmail("owner");
		TestContexts.login(email);
		fixtures.user(email);
		typeT = fields.createType(new SaveType("T-" + UUID.randomUUID().toString().substring(0, 6), null)).id();
		globalG = UUID.randomUUID().toString();
		jdbc.update("""
			insert into SN_CIRA_CUSTOM_FIELD(OBJ_ID, FIELD_NM, FIELD_TYPE, REQUIRED_YN, SORT_ORD, TENANT, USE_STAT_CD, CREATED_AT, MODIFIED_AT)
			values(?, ?, 'TEXT', 'N', 0, 'TSH', 'Usable', now(), now())""", globalG, "고객사-" + globalG.substring(0, 6));

		p1 = withType(fixtures.project("KANBAN"), typeT);
		p2 = fixtures.project("KANBAN");
		issue1 = issue(p1);
		issue2 = issue(p2);

		// 옛 모양: 세트·추가 필드가 전역 필드를 직접 가리키고, 값도 전역 필드 id 로 저장
		jdbc.update("insert into SN_CIRA_PROJECT_TYPE_FIELD(PROJECT_TYPE_ID, FIELD_KEY, SORT_ORD) values(?,?,9)", typeT, "cf:" + globalG);
		jdbc.update("insert into SN_CIRA_PROJECT_FIELD(PROJECT_ID, FIELD_KEY, SORT_ORD) values(?,?,1)", p1.getId(), "cf:" + globalG);
		jdbc.update("insert into SN_CIRA_PROJECT_FIELD(PROJECT_ID, FIELD_KEY, SORT_ORD) values(?,?,1)", p2.getId(), "cf:" + globalG);
		value(issue1, "p1");
		value(issue2, "p2");
		runV600();
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private ProjectResponse withType(ProjectResponse p, String typeId) {
		UpdateProjectRequest req = new UpdateProjectRequest();
		req.setProjectTypeId(typeId);
		return projects.updateProject(p.getId(), req);
	}

	private String issue(ProjectResponse p) {
		return issues.createIssue(p.getId(), CreateIssueRequest.builder().title("t")
			.statusId(fixtures.statusId(p.getId(), "To Do")).build()).getId();
	}

	private void value(String issueId, String text) {
		jdbc.update("insert into SN_CIRA_ISSUE_CF_VALUE(OBJ_ID, ISSUE_ID, CUSTOM_FIELD_ID, VAL_TEXT) values(gen_random_uuid()::text,?,?,?)",
			issueId, globalG, text);
	}

	private void runV600() {
		try (Connection c = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(c, new ClassPathResource(V600));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private List<String> keys(String sql, String id) {
		return jdbc.queryForList(sql, String.class, id);
	}

	private String valueFieldOf(String issueId) {
		return jdbc.queryForObject("select CUSTOM_FIELD_ID from SN_CIRA_ISSUE_CF_VALUE where ISSUE_ID=?", String.class, issueId);
	}

	@Test
	@DisplayName("타입 세트의 전역 참조 → 그 타입의 타입 필드 복사본, 그 타입 프로젝트의 값도 새 id 로")
	void migrate_typeSetGlobalRef_becomesTypeCopy() {
		List<String> set = keys("select FIELD_KEY from SN_CIRA_PROJECT_TYPE_FIELD where PROJECT_TYPE_ID=? and FIELD_KEY like 'cf:%'", typeT);
		assertThat(set).hasSize(1).doesNotContain("cf:" + globalG);
		String copy = set.get(0).substring(3);
		assertThat(jdbc.queryForMap("select PROJECT_TYPE_ID, SOURCE_FIELD_ID, PROJECT_ID from SN_CIRA_CUSTOM_FIELD where OBJ_ID=?", copy))
			.containsEntry("project_type_id", typeT).containsEntry("source_field_id", globalG).containsEntry("project_id", null);
		assertThat(valueFieldOf(issue1)).isEqualTo(copy);
	}

	@Test
	@DisplayName("프로젝트 추가 필드의 전역 참조 → 그 프로젝트 필드 복사본, 값도 새 id 로")
	void migrate_projectGlobalRef_becomesProjectCopy() {
		List<String> added = keys("select FIELD_KEY from SN_CIRA_PROJECT_FIELD where PROJECT_ID=?", p2.getId());
		assertThat(added).hasSize(1).doesNotContain("cf:" + globalG);
		String copy = added.get(0).substring(3);
		assertThat(jdbc.queryForMap("select PROJECT_ID, SOURCE_FIELD_ID from SN_CIRA_CUSTOM_FIELD where OBJ_ID=?", copy))
			.containsEntry("project_id", p2.getId()).containsEntry("source_field_id", globalG);
		assertThat(valueFieldOf(issue2)).isEqualTo(copy);
	}

	@Test
	@DisplayName("세트에도 있던(가려져 있던) 프로젝트 추가 필드 참조는 지우기만 한다")
	void migrate_shadowedProjectRef_removed() {
		assertThat(keys("select FIELD_KEY from SN_CIRA_PROJECT_FIELD where PROJECT_ID=?", p1.getId())).isEmpty();
	}

	@Test
	@DisplayName("다시 실행해도 결과가 같고, 원본 전역 필드는 라이브러리로 남는다")
	void migrate_isIdempotent() {
		Integer before = jdbc.queryForObject("select count(*) from SN_CIRA_CUSTOM_FIELD", Integer.class);
		runV600();
		assertThat(jdbc.queryForObject("select count(*) from SN_CIRA_CUSTOM_FIELD", Integer.class)).isEqualTo(before);
		assertThat(jdbc.queryForObject("select count(*) from SN_CIRA_CUSTOM_FIELD where OBJ_ID=?", Integer.class, globalG)).isEqualTo(1);
	}
}
