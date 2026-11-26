package tsh.d94.app.server.apService.lifeMetric;

import static tsh.d94.app.server.apService.lifeMetric.LifeMetricException.Code.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 지표 수식 평가기 (재귀 하강, 파싱하면서 바로 계산).
 * <pre>
 * expr    := term (('+'|'-') term)*
 * term    := unary (('*'|'/') unary)*
 * unary   := '-' unary | primary
 * primary := number | '(' expr ')' | '@' code | name '(' args ')' | name
 * </pre>
 * name = 기준값 키. 날짜 값은 epoch day(1970-01-01 부터 일수)로 다룬다.
 * 함수: now() 오늘(epoch day), daysBetween(a, b) = b - a, sum|avg|count(로그키[, 최근 N일]).
 * ponytail: 함수는 이 다섯 개뿐 — 필요해지면 {@link Parser#call} 에 case 하나.
 */
public final class LifeMetricFormulaEngine {

	/** 수식 밖 값을 꺼내는 곳. 날짜 기준값은 epoch day 로 돌려준다. */
	public interface Scope {
		double var(String key);

		double ref(String code);

		/** fn = sum|avg|count, days = null 이면 전체 기간 */
		double agg(String fn, String key, Integer days);

		long todayEpochDay();
	}

	private static final Pattern REF = Pattern.compile("@([A-Za-z_][A-Za-z0-9_]*)");

	private LifeMetricFormulaEngine() {
	}

	public static double evaluate(String formula, Scope scope) {
		if (formula == null || formula.isBlank()) {
			throw new LifeMetricException(INVALID_FORMULA, "수식이 비어 있습니다.");
		}
		double v = new Parser(formula, scope).parseAll();
		if (!Double.isFinite(v)) {
			throw new LifeMetricException(EVALUATION_FAILED, "결과가 유한한 수가 아닙니다.");
		}
		return v;
	}

	/** 문법만 검사: 값은 모두 1로 두고 계산 오류(0 나누기 등)는 무시한다 */
	public static void validate(String formula) {
		try {
			evaluate(formula, new Scope() {
				public double var(String key) { return 1; }
				public double ref(String code) { return 1; }
				public double agg(String fn, String key, Integer days) { return 1; }
				public long todayEpochDay() { return 1; }
			});
		} catch (LifeMetricException e) {
			if (e.getCode() != EVALUATION_FAILED) {
				throw e;
			}
		}
	}

	/** 수식이 참조하는 @code (등장 순서) */
	public static Set<String> refs(String formula) {
		Set<String> out = new LinkedHashSet<>();
		Matcher m = REF.matcher(formula == null ? "" : formula);
		while (m.find()) {
			out.add(m.group(1));
		}
		return out;
	}

	/**
	 * start 에서 @참조를 따라가 start 로 돌아오는 경로. 없으면 빈 목록.
	 * formulas = code → 수식 (없는 code 참조는 순환이 아니다).
	 */
	public static List<String> findCycle(String start, Map<String, String> formulas) {
		Deque<String> path = new ArrayDeque<>();
		path.add(start);
		return dfs(start, start, formulas, path, new LinkedHashSet<>()) ? new ArrayList<>(path) : List.of();
	}

	private static boolean dfs(String start, String cur, Map<String, String> formulas, Deque<String> path,
		Set<String> seen) {
		for (String next : refs(formulas.get(cur))) {
			path.addLast(next);
			if (next.equals(start)) {
				return true;
			}
			if (formulas.containsKey(next) && seen.add(next) && dfs(start, next, formulas, path, seen)) {
				return true;
			}
			path.removeLast();
		}
		return false;
	}

	private static final class Parser {
		private final String s;
		private final Scope scope;
		private int pos;

		Parser(String s, Scope scope) {
			this.s = s;
			this.scope = scope;
		}

		double parseAll() {
			double v = expr();
			skip();
			if (pos < s.length()) {
				throw syntax("예상하지 못한 문자 '" + s.charAt(pos) + "'");
			}
			return v;
		}

		private double expr() {
			double v = term();
			while (true) {
				if (eat('+')) {
					v += term();
				} else if (eat('-')) {
					v -= term();
				} else {
					return v;
				}
			}
		}

		private double term() {
			double v = unary();
			while (true) {
				if (eat('*')) {
					v *= unary();
				} else if (eat('/')) {
					double d = unary();
					if (d == 0) {
						throw new LifeMetricException(EVALUATION_FAILED, "0으로 나눌 수 없습니다.");
					}
					v /= d;
				} else {
					return v;
				}
			}
		}

		private double unary() {
			return eat('-') ? -unary() : primary();
		}

		private double primary() {
			skip();
			if (eat('(')) {
				double v = expr();
				expect(')');
				return v;
			}
			if (eat('@')) {
				return scope.ref(name("지표 코드"));
			}
			if (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
				return number();
			}
			String id = name("값");
			return peek('(') ? call(id) : scope.var(id);
		}

		private double call(String fn) {
			expect('(');
			double v = switch (fn) {
				case "now" -> scope.todayEpochDay();
				case "daysBetween" -> {
					double a = expr();
					expect(',');
					yield expr() - a;
				}
				case "sum", "avg", "count" -> {
					String key = name("로그 키");
					Integer days = null;
					if (eat(',')) {
						double d = expr();
						if (d < 0 || d != Math.rint(d)) {
							throw syntax(fn + " 의 기간(일)은 0 이상의 정수여야 합니다");
						}
						days = (int)d;
					}
					yield scope.agg(fn, key, days);
				}
				default -> throw syntax("모르는 함수 '" + fn + "'");
			};
			expect(')');
			return v;
		}

		private double number() {
			int start = pos;
			while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
				pos++;
			}
			try {
				return Double.parseDouble(s.substring(start, pos));
			} catch (NumberFormatException e) {
				throw syntax("숫자 형식 오류 '" + s.substring(start, pos) + "'");
			}
		}

		private String name(String what) {
			skip();
			int start = pos;
			while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) {
				pos++;
			}
			if (start == pos || Character.isDigit(s.charAt(start))) {
				throw syntax(what + " 이(가) 필요합니다");
			}
			return s.substring(start, pos);
		}

		private boolean peek(char c) {
			skip();
			return pos < s.length() && s.charAt(pos) == c;
		}

		private boolean eat(char c) {
			if (peek(c)) {
				pos++;
				return true;
			}
			return false;
		}

		private void expect(char c) {
			if (!eat(c)) {
				throw syntax("'" + c + "' 가 필요합니다");
			}
		}

		private void skip() {
			while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
				pos++;
			}
		}

		private LifeMetricException syntax(String msg) {
			return new LifeMetricException(INVALID_FORMULA, msg + " (위치 " + (pos + 1) + ")");
		}
	}
}
