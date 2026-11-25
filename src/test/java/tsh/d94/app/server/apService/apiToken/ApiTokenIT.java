package tsh.d94.app.server.apService.apiToken;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import tsh.d94.app.server.apService.apiToken.ApiTokenDtos.IssueRequest;
import tsh.d94.app.server.apService.apiToken.ApiTokenDtos.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/** 실제 보안 체인(MockMvc + 전체 필터)으로 API 토큰 인증·범위·폐기를 확인 */
@IntegrationTest
@AutoConfigureMockMvc
class ApiTokenIT {

	static final String T = TestContexts.TENANT;

	@Autowired MockMvc mvc;
	@Autowired ApiTokenService service;
	@Autowired CiraFixtures fixtures;
	@Autowired JdbcTemplate jdbc;

	@AfterEach
	void clear() {
		TestContexts.clear();
	}

	private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, String token)
		throws Exception {
		TestContexts.clear(); // 요청은 필터가 만든 컨텍스트로만 동작해야 한다
		return mvc.perform(b.header("Authorization", "Bearer " + token));
	}

	/** 사용자 + 그 사용자가 소유한 프로젝트 */
	private ProjectResponse ownerWithProject(String email) {
		TestContexts.login(email);
		fixtures.user(email);
		return fixtures.project("SCRUM");
	}

	private IssueResponse issue(String email, String access) {
		return service.issue(T, email, new IssueRequest("cira", "it", access, null));
	}

	@Test
	void readTokenActsAsOwnerWithinCiraOnly() throws Exception {
		String email = CiraFixtures.uniqueEmail("tok");
		ProjectResponse p = ownerWithProject(email);
		IssueResponse read = issue(email, "READ");

		call(get("/api/v1/projects/" + p.getId()), read.token()).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.key").value(p.getKey()));
		assertThat(jdbc.queryForObject("SELECT LAST_USED_AT FROM SN_API_TOKEN WHERE OBJ_ID=?", Object.class,
			read.info().id())).isNotNull();

		call(post("/api/v1/projects/" + p.getId() + "/issues").contentType(MediaType.APPLICATION_JSON).content("{}"),
			read.token()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("API_TOKEN_READ_ONLY"));
		// 토큰으로 토큰 발급·설정·다른 서비스 금지
		call(get("/api/v1/api-tokens"), read.token()).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("API_TOKEN_SCOPE"));
		call(post("/api/v1/api-tokens").contentType(MediaType.APPLICATION_JSON).content("{}"), read.token())
			.andExpect(status().isForbidden());
		call(get("/api/v1/settings"), read.token()).andExpect(status().isForbidden());
		call(get("/api/v1/study/documents"), read.token()).andExpect(status().isForbidden());
	}

	@Test
	void writeTokenPassesFilterOnWrites() throws Exception {
		String email = CiraFixtures.uniqueEmail("tokw");
		ProjectResponse p = ownerWithProject(email);
		IssueResponse write = issue(email, "WRITE");

		int st = call(post("/api/v1/projects/" + p.getId() + "/issues").contentType(MediaType.APPLICATION_JSON)
			.content("{}"), write.token()).andReturn().getResponse().getStatus();
		assertThat(st).isNotIn(401, 403); // 본문이 비어 검증 오류는 날 수 있지만 인증·인가는 통과
	}

	@Test
	void ownerPermissionStillApplies() throws Exception {
		ProjectResponse p = ownerWithProject(CiraFixtures.uniqueEmail("own"));
		String other = CiraFixtures.uniqueEmail("oth");
		TestContexts.login(other);
		fixtures.user(other);
		IssueResponse otherToken = issue(other, "WRITE");

		// CIRA 는 조회는 열려 있고 변경만 프로젝트 ADMIN 검사 → 토큰도 소유자 권한으로 같은 검사를 받는다
		int st = call(put("/api/v1/projects/" + p.getId()).contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"hacked\"}"), otherToken.token()).andReturn().getResponse().getStatus();
		assertThat(st).isGreaterThanOrEqualTo(400).isNotIn(401);
		assertThat(jdbc.queryForObject("SELECT PROJECT_NM FROM SN_CIRA_PROJECT WHERE OBJ_ID=?", String.class, p.getId()))
			.isEqualTo(p.getName());
	}

	@Test
	void invalidRevokedExpiredSuspendedAreUnauthorized() throws Exception {
		String email = CiraFixtures.uniqueEmail("tokx");
		ProjectResponse p = ownerWithProject(email);
		String url = "/api/v1/projects/" + p.getId();

		call(get(url), "d94_cira_doesnotexist").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("API_TOKEN_INVALID"));

		IssueResponse revoked = issue(email, "READ");
		service.revoke(T, email, revoked.info().id());
		call(get(url), revoked.token()).andExpect(status().isUnauthorized());

		IssueResponse expired = issue(email, "READ");
		jdbc.update("UPDATE SN_API_TOKEN SET EXPIRES_AT = now() - interval '1 minute' WHERE OBJ_ID=?", expired.info().id());
		call(get(url), expired.token()).andExpect(status().isUnauthorized());

		IssueResponse ok = issue(email, "READ");
		jdbc.update("UPDATE GS_USER SET USE_STAT_CD='UnUsable' WHERE EMAIL=?", email);
		call(get(url), ok.token()).andExpect(status().isUnauthorized());
	}

	@Test
	void adminCanDisableService() throws Exception {
		String email = CiraFixtures.uniqueEmail("tokd");
		ProjectResponse p = ownerWithProject(email);
		IssueResponse read = issue(email, "READ");
		try {
			service.setServiceEnabled(T, "cira", false);
			call(get("/api/v1/projects/" + p.getId()), read.token()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("API_TOKEN_SERVICE_DISABLED"));
			assertThatThrownBy(() -> issue(email, "READ"))
				.isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
		} finally {
			service.setServiceEnabled(T, "cira", true);
		}
		call(get("/api/v1/projects/" + p.getId()), read.token()).andExpect(status().isOk());
	}

	@Test
	void docsTokenCallsDocsOnly() throws Exception {
		String email = CiraFixtures.uniqueEmail("tokdocs");
		ProjectResponse p = ownerWithProject(email);
		IssueResponse docs = service.issue(T, email, new IssueRequest("docs", "it", "READ", null));

		assertThat(docs.token()).startsWith("d94_docs_");
		call(get("/api/v1/docs/folders"), docs.token()).andExpect(status().isOk());
		call(get("/api/v1/projects/" + p.getId()), docs.token()).andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("API_TOKEN_SCOPE"));
	}

	@Test
	void globalTokenCallsEveryServiceButNotAdminApis() throws Exception {
		String email = CiraFixtures.uniqueEmail("tokall");
		ProjectResponse p = ownerWithProject(email);
		IssueResponse all = service.issue(T, email, new IssueRequest("all", "it", "READ", null));

		assertThat(all.token()).startsWith("d94_all_");
		call(get("/api/v1/projects/" + p.getId()), all.token()).andExpect(status().isOk());
		call(get("/api/v1/docs/folders"), all.token()).andExpect(status().isOk());
		call(get("/api/v1/interface-tests"), all.token()).andExpect(status().isOk());
		call(get("/api/v1/api-tokens"), all.token()).andExpect(status().isForbidden());
		call(get("/api/v1/settings"), all.token()).andExpect(status().isForbidden());

		// 서비스를 끄면 전역 토큰도 그 서비스는 막히고 다른 서비스는 그대로
		try {
			service.setServiceEnabled(T, "cira", false);
			call(get("/api/v1/projects/" + p.getId()), all.token()).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("API_TOKEN_SERVICE_DISABLED"));
			call(get("/api/v1/docs/folders"), all.token()).andExpect(status().isOk());
		} finally {
			service.setServiceEnabled(T, "cira", true);
		}
		// 전역 토큰 자체를 끄면 모든 호출이 막힌다
		try {
			service.setServiceEnabled(T, "all", false);
			call(get("/api/v1/docs/folders"), all.token()).andExpect(status().isForbidden());
		} finally {
			service.setServiceEnabled(T, "all", true);
		}
	}

	@Test
	void listAndRevokeAreOwnerScoped() {
		String a = CiraFixtures.uniqueEmail("la");
		IssueResponse t = issue(a, "READ");
		assertThat(service.list(T, a)).extracting(v -> v.id()).containsExactly(t.info().id());
		assertThat(t.info().prefix()).isEqualTo(t.token().substring(0, 12));
		assertThatThrownBy(() -> service.revoke(T, CiraFixtures.uniqueEmail("lb"), t.info().id()))
			.isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
		service.revoke(T, a, t.info().id());
		assertThat(service.list(T, a)).isEmpty();
	}
}
