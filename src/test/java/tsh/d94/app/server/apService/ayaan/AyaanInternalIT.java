package tsh.d94.app.server.apService.ayaan;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/** ayaan 내부 API (서비스 토큰·조건부 전이·선점). 작업은 테스트마다 고유 toolCd 로 격리한다 (claim 이 DB 전역이라). */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ayaan.service-token=test-token")
class AyaanInternalIT {

	static final String BASE = "/api/v1/internal/ayaan";

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper om;
	@Autowired JdbcTemplate jdbc;
	@Autowired CiraFixtures fixtures;

	private static String uniq() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
	}

	private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String json) {
		b.header("X-Service-Token", "test-token");
		return json == null ? b : b.contentType(MediaType.APPLICATION_JSON).content(json);
	}

	private String insertTask(String tool, String ws, String status, String issueKey, String extraSetSql) {
		String id = UUID.randomUUID().toString();
		jdbc.update("INSERT INTO SN_AYN_TASK (OBJ_ID, ISSUE_KEY, SOURCE, PROJECT_KEY, WORKSPACE_PATH, PROMPT, TOOL_CD, STATUS, RESUME_COUNT, CREATED_AT, "
			+ "SRV_ID, TENANT, TRACE_ID, EVNT_NM, PREV_EVNT_NM, USE_STAT_CD) "
			+ "VALUES (?, ?, 'API', 'AY', ?, 'p', ?, ?, 0, now(), 'app-server', 'TSH', 'IT', 'ItFixture', 'None', 'Usable')", id, issueKey, ws, tool, status);
		if (extraSetSql != null) {
			jdbc.update("UPDATE SN_AYN_TASK SET " + extraSetSql + " WHERE OBJ_ID=?", id);
		}
		return id;
	}

	private String dbStatus(String id) {
		return jdbc.queryForObject("SELECT STATUS FROM SN_AYN_TASK WHERE OBJ_ID=?", String.class, id);
	}

	private int claim(String tool, int grace) throws Exception {
		String body = "{\"toolCd\":\"" + tool + "\",\"leaseOwner\":\"w1\",\"leaseSeconds\":60,\"graceSeconds\":" + grace + "}";
		return mvc.perform(auth(post(BASE + "/tasks/claim"), body)).andReturn().getResponse().getStatus();
	}

	private <T> List<T> parallel(Callable<T> a, Callable<T> b) throws Exception {
		ExecutorService ex = Executors.newFixedThreadPool(2);
		CountDownLatch gate = new CountDownLatch(1);
		try {
			Future<T> fa = ex.submit(() -> { gate.await(); return a.call(); });
			Future<T> fb = ex.submit(() -> { gate.await(); return b.call(); });
			gate.countDown();
			return List.of(fa.get(), fb.get());
		} finally {
			ex.shutdown();
		}
	}

	@Test
	void missingTokenIs401() throws Exception {
		mvc.perform(get(BASE + "/config")).andExpect(status().isUnauthorized());
		mvc.perform(get(BASE + "/config").header("X-Service-Token", "wrong")).andExpect(status().isUnauthorized());
		mvc.perform(auth(get(BASE + "/config"), null)).andExpect(status().isOk());
	}

	@Test
	void transitionWithWrongExpectedIs409() throws Exception {
		String id = insertTask(uniq(), "/ws/" + uniq(), "QUEUED", null, null);
		mvc.perform(auth(post(BASE + "/tasks/" + id + "/transition"),
			"{\"expectedStatus\":\"PAUSED\",\"nextStatus\":\"RUNNING\",\"fields\":{}}"))
			.andExpect(status().isConflict());
		assertThat(dbStatus(id)).isEqualTo("QUEUED");
		mvc.perform(auth(post(BASE + "/tasks/" + id + "/transition"),
			"{\"expectedStatus\":\"QUEUED\",\"nextStatus\":\"CANCELED\",\"fields\":{\"failReason\":\"x\"}}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"))
			.andExpect(jsonPath("$.data.failReason").value("x"));
	}

	@Test
	void concurrentClaimOnlyOneWins() throws Exception {
		String tool = uniq();
		String id = insertTask(tool, "/ws/" + uniq(), "QUEUED", null, null);
		List<Integer> codes = parallel(() -> claim(tool, 0), () -> claim(tool, 0));
		assertThat(codes).containsExactlyInAnyOrder(200, 204);
		assertThat(dbStatus(id)).isEqualTo("RUNNING");
	}

	@Test
	void claimSkipsBusyWorkspace() throws Exception {
		String tool = uniq();
		String ws = "/ws/" + uniq();
		insertTask(uniq(), ws, "RUNNING", null, null);
		insertTask(tool, ws, "QUEUED", null, null);
		assertThat(claim(tool, 0)).isEqualTo(204);
	}

	@Test
	void claimPausedOnlyAfterGrace() throws Exception {
		String tool = uniq();
		insertTask(tool, "/ws/" + uniq(), "PAUSED", null, "RESUME_AT = now() - interval '60 seconds'");
		assertThat(claim(tool, 120)).isEqualTo(204);
		assertThat(claim(tool, 0)).isEqualTo(200);
	}

	@Test
	void recoverExpiredLease() throws Exception {
		String id = insertTask(uniq(), "/ws/" + uniq(), "RUNNING", null,
			"LEASE_OWNER='w1', LEASE_UNTIL = now() - interval '10 seconds', CLI_SESSION_ID='sess-1'");
		mvc.perform(auth(post(BASE + "/tasks/recover"), "{}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@ == '" + id + "')]").exists());
		assertThat(dbStatus(id)).isEqualTo("PAUSED");
		assertThat(jdbc.queryForObject("SELECT PAUSE_REASON FROM SN_AYN_TASK WHERE OBJ_ID=?", String.class, id)).isEqualTo("RECOVERED");
		assertThat(jdbc.queryForObject("SELECT CLI_SESSION_ID FROM SN_AYN_TASK WHERE OBJ_ID=?", String.class, id)).isEqualTo("sess-1");
	}

	@Test
	void concurrentCreateSameIssueOnlyOne() throws Exception {
		String body = "{\"issueKey\":\"CC-" + uniq() + "\",\"source\":\"API\",\"projectKey\":\"CC\",\"workspacePath\":\"/ws/x\","
			+ "\"prompt\":\"p\",\"toolCd\":\"CLAUDE\",\"status\":\"QUEUED\"}";
		Callable<Integer> c = () -> mvc.perform(auth(post(BASE + "/tasks"), body)).andReturn().getResponse().getStatus();
		assertThat(parallel(c, c)).containsExactlyInAnyOrder(201, 409);
		// 409 본문에 기존 Task
		mvc.perform(auth(post(BASE + "/tasks"), body)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data.status").value("QUEUED"));
	}

	@Test
	void taskDetailSumsRunTokens() throws Exception {
		String id = insertTask(uniq(), "/ws/" + uniq(), "RUNNING", null, null);
		for (int[] t : new int[][] {{1, 100, 10}, {2, 50, 5}}) {
			String res = mvc.perform(auth(post(BASE + "/tasks/" + id + "/runs"), "{\"runSeq\":" + t[0] + ",\"toolCd\":\"CLAUDE\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
			String runId = om.readTree(res).at("/data/id").asText();
			mvc.perform(auth(put(BASE + "/runs/" + runId), "{\"outcome\":\"DONE\",\"inputTokens\":" + t[1] + ",\"outputTokens\":" + t[2] + ",\"costUsd\":0.5}"))
				.andExpect(status().isOk());
		}
		String res = mvc.perform(auth(get(BASE + "/tasks/" + id), null)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		JsonNode d = om.readTree(res).get("data");
		assertThat(d.get("runs")).hasSize(2);
		assertThat(d.at("/tokenTotals/input").asLong()).isEqualTo(150);
		assertThat(d.at("/tokenTotals/output").asLong()).isEqualTo(15);
		assertThat(d.at("/tokenTotals/costUsd").asDouble()).isEqualTo(1.0);
	}

	@Test
	void issueByKey() throws Exception {
		String email = CiraFixtures.uniqueEmail("ayaan");
		TestContexts.login(email);
		try {
			fixtures.user(email);
			ProjectResponse p = fixtures.project("SCRUM");
			IssueResponse i = fixtures.issue(p.getId(), "ayaan 이슈", null);
			mvc.perform(auth(get(BASE + "/issues/by-key/" + i.getIssueKey()), null)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.issueKey").value(i.getIssueKey()))
				.andExpect(jsonPath("$.data.title").value("ayaan 이슈"))
				.andExpect(jsonPath("$.data.content").value(i.getContent()))
				.andExpect(jsonPath("$.data.projectKey").value(p.getKey()));
			mvc.perform(auth(get(BASE + "/issues/by-key/NOPE-1"), null)).andExpect(status().isNotFound());
		} finally {
			TestContexts.clear();
		}
	}

	@Test
	void concurrentClaimSameWorkspaceDifferentToolsOnlyOne() throws Exception {
		String ws = "/ws/" + uniq();
		String toolA = uniq();
		String toolB = uniq();
		insertTask(toolA, ws, "QUEUED", null, null);
		insertTask(toolB, ws, "QUEUED", null, null);
		List<Integer> codes = parallel(() -> claim(toolA, 0), () -> claim(toolB, 0));
		assertThat(codes).containsExactlyInAnyOrder(200, 204);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SN_AYN_TASK WHERE WORKSPACE_PATH=? AND STATUS='RUNNING'", Integer.class, ws)).isEqualTo(1);
	}

	@Test
	void transitionToRunningInBusyWorkspaceIs409() throws Exception {
		String ws = "/ws/" + uniq();
		insertTask(uniq(), ws, "RUNNING", null, null);
		String id = insertTask(uniq(), ws, "QUEUED", null, null);
		mvc.perform(auth(post(BASE + "/tasks/" + id + "/transition"),
			"{\"expectedStatus\":\"QUEUED\",\"nextStatus\":\"RUNNING\",\"fields\":{}}")).andExpect(status().isConflict());
		assertThat(dbStatus(id)).isEqualTo("QUEUED");
	}

	@Test
	void transitionUnknownTaskIs404() throws Exception {
		mvc.perform(auth(post(BASE + "/tasks/" + UUID.randomUUID() + "/transition"),
			"{\"expectedStatus\":\"QUEUED\",\"nextStatus\":\"DONE\",\"fields\":{}}")).andExpect(status().isNotFound());
	}

	@Test
	void transitionInputValidationIs400() throws Exception {
		String id = insertTask(uniq(), "/ws/" + uniq(), "QUEUED", null, null);
		String t = BASE + "/tasks/" + id + "/transition";
		mvc.perform(auth(post(t), "{\"nextStatus\":\"DONE\"}")).andExpect(status().isBadRequest());
		mvc.perform(auth(post(t), "{\"expectedStatus\":\"QUEUED\"}")).andExpect(status().isBadRequest());
		mvc.perform(auth(post(t), "{\"expectedStatus\":\"QUEUED\",\"nextStatus\":\"DONE\",\"fields\":{\"bogus\":1}}")).andExpect(status().isBadRequest());
		mvc.perform(auth(post(t), "{\"expectedStatus\":\"QUEUED\",\"nextStatus\":\"DONE\",\"fields\":{\"resultSummary\":{\"a\":1}}}")).andExpect(status().isBadRequest());
		assertThat(dbStatus(id)).isEqualTo("QUEUED");
		// 숫자는 문자열로 안전 변환, null 은 그대로 반영
		mvc.perform(auth(post(t), "{\"expectedStatus\":\"QUEUED\",\"nextStatus\":\"DONE\",\"fields\":{\"resultSummary\":42,\"failReason\":null}}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.resultSummary").value("42"));
	}

	@Test
	void leaseExtendAndOwnerMismatch() throws Exception {
		String id = insertTask(uniq(), "/ws/" + uniq(), "RUNNING", null, "LEASE_OWNER='w1', LEASE_UNTIL = now() + interval '5 seconds'");
		mvc.perform(auth(post(BASE + "/tasks/" + id + "/lease"), "{\"leaseOwner\":\"w2\",\"leaseSeconds\":60}")).andExpect(status().isConflict());
		mvc.perform(auth(post(BASE + "/tasks/" + id + "/lease"), "{\"leaseOwner\":\"w1\",\"leaseSeconds\":600}")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT LEASE_UNTIL > now() + interval '300 seconds' FROM SN_AYN_TASK WHERE OBJ_ID=?", Boolean.class, id)).isTrue();
	}

	@Test
	void crudConfigToolWorkspaceQuota() throws Exception {
		String k = "K" + uniq();
		mvc.perform(auth(put(BASE + "/config"), "{\"" + k + "\":\"v1\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.data." + k).value("v1")).andExpect(jsonPath("$.data.EXHAUST_POLICY").value("WAIT_SAME_TOOL"));
		mvc.perform(auth(put(BASE + "/tools/CLAUDE"), "{\"model\":\"opus\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.model").value("opus")).andExpect(jsonPath("$.data.enabled").value(true));
		String pk = "W" + uniq().substring(0, 6);
		mvc.perform(auth(put(BASE + "/workspaces/" + pk), "{\"workspacePath\":\"/ws/a\",\"defaultTool\":\"CLAUDE\"}")).andExpect(status().isOk());
		mvc.perform(auth(get(BASE + "/workspaces"), null)).andExpect(jsonPath("$.data[?(@.projectKey=='" + pk + "')]").exists());
		mvc.perform(auth(delete(BASE + "/workspaces/" + pk), null)).andExpect(status().isOk());
		mvc.perform(auth(put(BASE + "/quotas/CLAUDE/FIVE_HOUR"),
			"{\"usedPct\":12.5,\"status\":\"OK\",\"source\":\"API\",\"observedAt\":\"2026-11-10T01:00:00Z\"}")).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.windowCd").value("FIVE_HOUR"));
		mvc.perform(auth(get(BASE + "/quotas"), null)).andExpect(jsonPath("$.data[?(@.toolCd=='CLAUDE')]").exists());
	}

	@Test
	void putToolAndWorkspaceNullClearsField() throws Exception {
		String cd = "T" + uniq().substring(0, 8);
		mvc.perform(auth(put(BASE + "/tools/" + cd), "{\"model\":\"opus\"}")).andExpect(status().isOk());
		mvc.perform(auth(put(BASE + "/tools/" + cd), "{\"model\":null,\"enabled\":null}")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT MODEL FROM SN_AYN_TOOL WHERE TOOL_CD=?", String.class, cd)).isNull();
		String pk = "W" + uniq().substring(0, 6);
		mvc.perform(auth(put(BASE + "/workspaces/" + pk), "{\"workspacePath\":\"/ws/a\",\"promptTemplate\":\"t\"}")).andExpect(status().isOk());
		mvc.perform(auth(put(BASE + "/workspaces/" + pk), "{\"workspacePath\":\"/ws/a\",\"promptTemplate\":null}")).andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT PROMPT_TEMPLATE FROM SN_AYN_WORKSPACE WHERE PROJECT_KEY=?", String.class, pk)).isNull();
	}
}
