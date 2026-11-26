package tsh.d94.app.server.apService.lifeMetric;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tsh.d94.app.server.apService.apiToken.ApiTokenDtos.IssueRequest;
import tsh.d94.app.server.apService.apiToken.ApiTokenService;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.InputsRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.LogRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.MetricRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.MetricResponse;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.PreviewRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.TemplateResponse;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/** 템플릿 → 지표 → 기준값 → 로그 → 평가(@code 포함), 순환 거절, 소유자 격리, API 토큰 범위. */
@IntegrationTest
@AutoConfigureMockMvc
class LifeMetricFlowIT {

	@Autowired LifeMetricService service;
	@Autowired ApiTokenService tokens;
	@Autowired CiraFixtures fixtures;
	@Autowired JdbcTemplate jdbc;
	@Autowired MockMvc mvc;

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private static MetricRequest custom(String code, String formula) {
		return new MetricRequest(null, code, code, null, formula, null);
	}

	private static LifeMetricException.Code codeOf(Throwable t) {
		assertThat(t).isInstanceOf(LifeMetricException.class);
		return ((LifeMetricException)t).getCode();
	}

	private String login(String prefix) {
		String email = CiraFixtures.uniqueEmail(prefix);
		TestContexts.login(email);
		fixtures.user(email);
		return email;
	}

	@Test
	void templateMetricInputsLogsEvaluate() {
		login("lm");
		List<TemplateResponse> ts = service.templates();
		assertThat(ts).extracting(TemplateResponse::code)
			.contains("remaining_life", "hourly_wage", "car_depreciation");
		String wageTpl = ts.stream().filter(t -> t.code().equals("hourly_wage")).findFirst().orElseThrow().id();
		String carTpl = ts.stream().filter(t -> t.code().equals("car_depreciation")).findFirst().orElseThrow().id();

		// 템플릿으로 생성 → 스키마·수식·단위 복사, 기준값 없으니 error
		MetricResponse wage = service.create(new MetricRequest(wageTpl, "hourly_wage", null, null, null, null));
		assertThat(wage.formula()).isEqualTo("annual_salary / (weekly_hours * 52)");
		assertThat(wage.outputUnit()).isEqualTo("원/시간");
		assertThat(wage.inputSchema().isArray()).isTrue();
		assertThat(wage.value()).isNull();
		assertThat(wage.error()).contains("annual_salary");

		wage = service.putInputs(wage.id(), new InputsRequest(Map.of("annual_salary", 52_000_000, "weekly_hours", 40)));
		assertThat(wage.value()).isEqualTo(25_000.0);

		// 커스텀 지표가 @hourly_wage 참조: 커피 4,500원 = 노동 10.8분
		MetricResponse coffee = service.create(custom("coffee_minutes", "sum(coffee) / @hourly_wage * 60"));
		assertThat(service.evaluate(coffee.id()).value()).isEqualTo(0.0);
		service.addLog(coffee.id(), new LogRequest("coffee", 4500.0, "아메리카노", null));
		assertThat(service.evaluate(coffee.id()).value()).isCloseTo(10.8, within(1e-9));
		// 최근 N일 집계: 40일 전 기록은 sum(coffee, 30) 에서 빠진다
		service.addLog(coffee.id(), new LogRequest("coffee", 9000.0, null, LocalDateTime.now().minusDays(40)));
		service.update(coffee.id(), new MetricRequest(null, null, null, null, "sum(coffee, 30) / @hourly_wage * 60", null));
		assertThat(service.evaluate(coffee.id()).value()).isCloseTo(10.8, within(1e-9));
		assertThat(service.logs(coffee.id(), "coffee", null)).hasSize(2);

		// 차량: 로그 없음 → 0 나누기 error, 기록 후 계산
		MetricResponse car = service.create(new MetricRequest(carTpl, "car", "내 차", null, null, null));
		service.putInputs(car.id(), new InputsRequest(Map.of("purchase_price", 30_000_000, "residual_value", 10_000_000)));
		assertThat(service.evaluate(car.id()).error()).contains("0으로");
		service.addLog(car.id(), new LogRequest("distance", 10_000.0, null, null));
		var maint = service.addLog(car.id(), new LogRequest("maintenance", 1_000_000.0, null, null));
		assertThat(service.evaluate(car.id()).value()).isEqualTo(2_100.0);
		service.deleteLog(car.id(), maint.id());
		assertThat(service.evaluate(car.id()).value()).isEqualTo(2_000.0);
		assertThat(codeOf(catchThrowable(() -> service.deleteLog(car.id(), maint.id()))))
			.isEqualTo(LifeMetricException.Code.NOT_FOUND);

		// 남은 수명: 날짜 기준값(텍스트) → epoch day
		MetricResponse life = service.create(custom("life", "life_expectancy * 365 - daysBetween(birth_date, now())"));
		Map<String, Object> in = new HashMap<>();
		in.put("birth_date", LocalDate.now().minusDays(100).toString());
		in.put("life_expectancy", 1);
		assertThat(service.putInputs(life.id(), new InputsRequest(in)).value()).isEqualTo(265.0);
		in.put("life_expectancy", null); // null = 삭제
		assertThat(service.putInputs(life.id(), new InputsRequest(in)).inputs()).containsOnlyKeys("birth_date");

		// 미리보기: 저장 없이 계산
		assertThat(service.preview(new PreviewRequest("x * 2 + @hourly_wage", Map.of("x", 5))).value()).isEqualTo(25_010.0);
		assertThat(service.preview(new PreviewRequest("x +", Map.of())).error()).contains("위치");

		assertThat(service.list()).extracting(MetricResponse::code)
			.containsExactlyInAnyOrder("hourly_wage", "coffee_minutes", "car", "life");

		// 삭제 → 기준값·로그 CASCADE
		service.delete(car.id());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SN_LIFE_METRIC_LOG WHERE METRIC_ID=?", Integer.class, car.id()))
			.isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SN_LIFE_METRIC_INPUT WHERE METRIC_ID=?", Integer.class, car.id()))
			.isZero();
	}

	@Test
	void validationCyclesAndDuplicates() {
		login("lmv");
		MetricResponse a = service.create(custom("a", "@b + 1"));
		service.create(custom("b", "2"));
		assertThat(service.evaluate(a.id()).value()).isEqualTo(3.0);

		// b 가 a 를 참조하게 바꾸면 a → b → a
		String bId = service.list().stream().filter(m -> m.code().equals("b")).findFirst().orElseThrow().id();
		Throwable t = catchThrowable(() -> service.update(bId, custom(null, "@a * 2")));
		assertThat(codeOf(t)).isEqualTo(LifeMetricException.Code.CIRCULAR_REFERENCE);
		assertThat(t).hasMessageContaining("@b → @a → @b");
		assertThat(codeOf(catchThrowable(() -> service.create(custom("self", "@self")))))
			.isEqualTo(LifeMetricException.Code.CIRCULAR_REFERENCE);

		assertThat(codeOf(catchThrowable(() -> service.create(custom("a", "1")))))
			.isEqualTo(LifeMetricException.Code.DUPLICATE_CODE);
		assertThat(codeOf(catchThrowable(() -> service.create(custom("Bad-Code", "1")))))
			.isEqualTo(LifeMetricException.Code.INVALID_REQUEST);
		assertThat(codeOf(catchThrowable(() -> service.create(custom("c", "1 +")))))
			.isEqualTo(LifeMetricException.Code.INVALID_FORMULA);
		assertThat(codeOf(catchThrowable(() -> service.addLog(a.id(), new LogRequest("1bad", 1.0, null, null)))))
			.isEqualTo(LifeMetricException.Code.INVALID_REQUEST);
	}

	@Test
	void ownerIsolation() {
		login("lmo");
		MetricResponse mine = service.create(custom("secret", "1"));

		login("lmx");
		for (Runnable r : List.<Runnable>of(
			() -> service.get(mine.id()),
			() -> service.evaluate(mine.id()),
			() -> service.putInputs(mine.id(), new InputsRequest(Map.of("x", 1))),
			() -> service.addLog(mine.id(), new LogRequest("x", 1.0, null, null)),
			() -> service.logs(mine.id(), null, null),
			() -> service.delete(mine.id()))) {
			assertThat(codeOf(catchThrowable(r::run))).isEqualTo(LifeMetricException.Code.NOT_FOUND);
		}
		// 같은 code 를 다른 사람은 쓸 수 있고, @secret 은 내 지표만 본다
		MetricResponse other = service.create(custom("other", "@secret"));
		assertThat(other.error()).contains("@secret");
	}

	@Test
	void apiTokenScope() throws Exception {
		String email = login("lmt");
		String id = service.create(custom("one", "1")).id();
		String lm = tokens.issue(TestContexts.TENANT, email, new IssueRequest("life-metric", "it", "WRITE", null)).token();
		String all = tokens.issue(TestContexts.TENANT, email, new IssueRequest("all", "it", "READ", null)).token();
		String docs = tokens.issue(TestContexts.TENANT, email, new IssueRequest("docs", "it", "WRITE", null)).token();
		assertThat(lm).startsWith("d94_life-metric_");

		call(get("/api/v1/life-metric/metrics/" + id + "/evaluate"), lm).andExpect(status().isOk())
			.andExpect(jsonPath("$.data.value").value(1.0));
		call(post("/api/v1/life-metric/metrics").contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"two\",\"name\":\"둘\",\"formula\":\"@one + 1\"}"), lm)
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.value").value(2.0));
		call(get("/api/v1/life-metric/templates"), all).andExpect(status().isOk());
		call(get("/api/v1/life-metric/metrics"), docs).andExpect(status().isForbidden());
		call(get("/api/v1/docs/notes"), lm).andExpect(status().isForbidden());

		// 다른 사람 토큰으로는 404
		String other = login("lmt2");
		String otherToken = tokens.issue(TestContexts.TENANT, other, new IssueRequest("life-metric", "it", "READ", null)).token();
		call(get("/api/v1/life-metric/metrics/" + id), otherToken).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("LIFE_METRIC_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.ResultActions call(MockHttpServletRequestBuilder b, String token)
		throws Exception {
		TestContexts.clear(); // 요청은 필터가 만든 컨텍스트로만 동작해야 한다
		return mvc.perform(b.header("Authorization", "Bearer " + token));
	}
}
