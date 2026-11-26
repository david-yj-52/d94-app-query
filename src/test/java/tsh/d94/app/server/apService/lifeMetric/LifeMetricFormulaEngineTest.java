package tsh.d94.app.server.apService.lifeMetric;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tsh.d94.app.server.apService.lifeMetric.LifeMetricException.Code;

class LifeMetricFormulaEngineTest {

	static final long TODAY = LocalDate.of(2026, 11, 25).toEpochDay();

	/** 기준값 a=10, b=4, birth=2000-01-01, @wage=20000, sum(x)=30·최근 7일 7, avg(x)=5, count(x)=6 */
	static final LifeMetricFormulaEngine.Scope SCOPE = new LifeMetricFormulaEngine.Scope() {
		public double var(String key) {
			return switch (key) {
				case "a" -> 10;
				case "b" -> 4;
				case "birth" -> LocalDate.of(2000, 1, 1).toEpochDay();
				default -> throw new LifeMetricException(Code.EVALUATION_FAILED, "기준값이 없습니다: " + key);
			};
		}

		public double ref(String code) {
			if (code.equals("wage")) {
				return 20000;
			}
			throw new LifeMetricException(Code.EVALUATION_FAILED, "지표가 없습니다: @" + code);
		}

		public double agg(String fn, String key, Integer days) {
			return switch (fn) {
				case "sum" -> days == null ? 30 : days;
				case "avg" -> 5;
				default -> 6;
			};
		}

		public long todayEpochDay() {
			return TODAY;
		}
	};

	static double eval(String f) {
		return LifeMetricFormulaEngine.evaluate(f, SCOPE);
	}

	static Code codeOf(String f) {
		Throwable t = catchThrowable(() -> eval(f));
		assertThat(t).isInstanceOf(LifeMetricException.class);
		return ((LifeMetricException)t).getCode();
	}

	@Test
	void arithmeticPrecedenceParensUnary() {
		assertThat(eval("1 + 2 * 3")).isEqualTo(7);
		assertThat(eval("(1 + 2) * 3")).isEqualTo(9);
		assertThat(eval("a - b - 1")).isEqualTo(5); // 왼쪽 결합
		assertThat(eval("a / b / 2")).isEqualTo(1.25);
		assertThat(eval("-a + --b")).isEqualTo(-6);
		assertThat(eval(" 2.5*a ")).isEqualTo(25);
	}

	@Test
	void dateFunctions() {
		assertThat(eval("now()")).isEqualTo(TODAY);
		assertThat(eval("daysBetween(birth, now())")).isEqualTo(TODAY - LocalDate.of(2000, 1, 1).toEpochDay());
		// 템플릿 remaining_life 모양
		assertThat(eval("100 * 365.2425 - daysBetween(birth, now())")).isCloseTo(36524.25 - 9825, within(1e-9));
	}

	@Test
	void logAggregatesAndRefs() {
		assertThat(eval("sum(x)")).isEqualTo(30);
		assertThat(eval("sum(x, 7)")).isEqualTo(7);
		assertThat(eval("avg(x) + count(x)")).isEqualTo(11);
		assertThat(eval("4500 / @wage * 60")).isEqualTo(13.5); // 커피 한 잔 = 노동 13.5분
		assertThat(codeOf("sum(x, 1.5)")).isEqualTo(Code.INVALID_FORMULA);
		assertThat(codeOf("sum(1)")).isEqualTo(Code.INVALID_FORMULA);
	}

	@Test
	void errors() {
		assertThat(codeOf("a / (b - 4)")).isEqualTo(Code.EVALUATION_FAILED);
		assertThat(codeOf("missing + 1")).isEqualTo(Code.EVALUATION_FAILED);
		assertThat(codeOf("@nope")).isEqualTo(Code.EVALUATION_FAILED);
		assertThat(codeOf("1 +")).isEqualTo(Code.INVALID_FORMULA);
		assertThat(codeOf("(1 + 2")).isEqualTo(Code.INVALID_FORMULA);
		assertThat(codeOf("1 2")).isEqualTo(Code.INVALID_FORMULA);
		assertThat(codeOf("pow(2, 3)")).isEqualTo(Code.INVALID_FORMULA);
		assertThat(codeOf("1.2.3")).isEqualTo(Code.INVALID_FORMULA);
		assertThat(codeOf("")).isEqualTo(Code.INVALID_FORMULA);
		assertThat(catchThrowable(() -> eval("1 $ 2"))).hasMessageContaining("위치 3");
	}

	@Test
	void validateIgnoresValueErrorsButNotSyntax() {
		LifeMetricFormulaEngine.validate("a / (b - b)"); // 값은 모두 1 → 0 나누기지만 문법은 맞다
		LifeMetricFormulaEngine.validate("unknown_key * @other + sum(x, 30)");
		assertThatThrownBy(() -> LifeMetricFormulaEngine.validate("a +* b")).isInstanceOf(LifeMetricException.class);
	}

	@Test
	void refsAndCycles() {
		assertThat(LifeMetricFormulaEngine.refs("@a + @b * @a")).containsExactly("a", "b");
		Map<String, String> f = Map.of("a", "@b + 1", "b", "@c * 2", "c", "10", "d", "@d");
		assertThat(LifeMetricFormulaEngine.findCycle("a", f)).isEmpty();
		assertThat(LifeMetricFormulaEngine.findCycle("d", f)).containsExactly("d", "d");
		Map<String, String> cyc = Map.of("a", "@b", "b", "@c + @x", "c", "@a");
		assertThat(LifeMetricFormulaEngine.findCycle("a", cyc)).containsExactly("a", "b", "c", "a");
		// 시작점이 아닌 곳의 순환(b↔c)은 a 의 순환이 아니다 — 그 지표를 저장할 때 잡힌다
		assertThat(LifeMetricFormulaEngine.findCycle("a", Map.of("a", "@b", "b", "@c", "c", "@b"))).isEmpty();
	}
}
