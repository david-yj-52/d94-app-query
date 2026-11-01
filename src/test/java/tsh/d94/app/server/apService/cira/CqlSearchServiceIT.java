package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.apService.cira.cql.CqlParseException;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/**
 * CQL → JPA Specification 이 PostgreSQL 에서 실제로 실행되고, 결과 범위(tenant·프로젝트 멤버·삭제)가 맞는지 확인.
 * 검색어에 테스트마다 고유한 표식(marker)을 넣어 다른 테스트 데이터와 섞이지 않게 한다.
 */
@IntegrationTest
class CqlSearchServiceIT {

	@Autowired
	private CqlSearchService cqlSearchService;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;

	private String marker;
	private String email;
	private GsUserModel me;
	private ProjectResponse project;

	@BeforeEach
	void setUp() {
		marker = "mk" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		email = CiraFixtures.uniqueEmail("cql");
		TestContexts.login(email);
		me = fixtures.user(email);
		project = fixtures.project("KANBAN");
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private List<String> searchTitles(String cql) {
		return cqlSearchService.search(cql, PageRequest.of(0, 50)).getContent().stream()
			.map(IssueResponse::getTitle).toList();
	}

	@Test
	@DisplayName("text ~ / AND / IN / ORDER BY 가 PostgreSQL 에서 실행되고 결과가 맞다")
	void search_basicOperators() {
		fixtures.issue(project.getId(), marker + " alpha", me.getObjId());
		fixtures.issue(project.getId(), marker + " beta", null);

		assertThat(searchTitles("text ~ \"" + marker + "\" ORDER BY created DESC"))
			.containsExactly(marker + " beta", marker + " alpha");
		assertThat(searchTitles("text ~ \"" + marker + "\" AND priority IN (\"MEDIUM\", \"HIGH\")")).hasSize(2);
		assertThat(searchTitles("text ~ \"" + marker + "\" AND NOT text ~ \"alpha\""))
			.containsExactly(marker + " beta");
	}

	@Test
	@DisplayName("[D12] assignee = currentUser() 는 내게 배정된 이슈를 찾는다 (currentUser 는 email, 컬럼은 objId)")
	void search_currentUser_matchesMyIssues() {
		fixtures.issue(project.getId(), marker + " mine", me.getObjId());

		assertThat(searchTitles("text ~ \"" + marker + "\" AND assignee = currentUser()"))
			.containsExactly(marker + " mine");
	}

	@Test
	@DisplayName("[D13] 다른 tenant 의 이슈는 검색되지 않는다")
	void search_doesNotLeakOtherTenant() {
		String otherEmail = CiraFixtures.uniqueEmail("cql-other");
		TestContexts.login(otherEmail, "OTHER");
		fixtures.user(otherEmail);
		ProjectResponse otherProject = fixtures.project("KANBAN");
		fixtures.issue(otherProject.getId(), marker + " other-tenant", null);

		TestContexts.login(email);
		assertThat(searchTitles("text ~ \"" + marker + "\"")).doesNotContain(marker + " other-tenant");
	}

	@Test
	@DisplayName("[D13] 멤버가 아닌 프로젝트의 이슈는 검색되지 않는다")
	void search_doesNotLeakNonMemberProject() {
		String strangerEmail = CiraFixtures.uniqueEmail("cql-stranger");
		TestContexts.login(strangerEmail);
		fixtures.user(strangerEmail);
		ProjectResponse strangersProject = fixtures.project("KANBAN");
		fixtures.issue(strangersProject.getId(), marker + " private", null);

		TestContexts.login(email);
		assertThat(searchTitles("text ~ \"" + marker + "\"")).doesNotContain(marker + " private");
	}

	@Test
	@DisplayName("[D13] 삭제된(deleted_at) 이슈는 검색되지 않는다")
	void search_excludesDeletedIssues() {
		IssueResponse deleted = fixtures.issue(project.getId(), marker + " deleted", null);
		jdbc.update("update sn_cira_issue set deleted_at = now() where obj_id = ?", deleted.getId());

		assertThat(searchTitles("text ~ \"" + marker + "\"")).doesNotContain(marker + " deleted");
	}

	@Test
	@DisplayName("[D14] 잘못된 값(날짜 형식·숫자·모르는 필드)은 400 이 되는 CqlParseException 이어야 한다")
	void search_badValues_areParseErrors() {
		assertThatThrownBy(() -> searchTitles("created > \"2026-01-01\"")).isInstanceOf(CqlParseException.class);
		assertThatThrownBy(() -> searchTitles("storyPoints > \"many\"")).isInstanceOf(CqlParseException.class);
		assertThatThrownBy(() -> searchTitles("nosuchfield = \"x\"")).isInstanceOf(CqlParseException.class);
	}

}
