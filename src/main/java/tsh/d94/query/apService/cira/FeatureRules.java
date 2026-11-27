package tsh.d94.app.server.apService.cira;

import java.util.Comparator;
import java.util.List;

/**
 * 2단계 Feature 규칙 (V650, 기존 Epic 대체). 순수 함수만 — FeatureRulesTest.
 * COMMON = 프로젝트 공통 레이어(SPACE_ID null), UNIT = 공간 Feature. 정렬·노출은 항상 COMMON 이 먼저.
 */
public final class FeatureRules {

	public static final String FEATURE_TYPE = "Feature";
	public static final String COMMON = "COMMON";
	public static final String UNIT = "UNIT";

	private FeatureRules() {
	}

	/** Feature 가 아니면 null. Feature 면 요청값(COMMON|UNIT, 기본 UNIT) */
	static String scope(boolean isFeature, String requested) {
		if (!isFeature) {
			if (requested != null && !requested.isBlank()) {
				throw new IllegalArgumentException("Feature 유형만 featureScope 를 가질 수 있습니다");
			}
			return null;
		}
		if (requested == null || requested.isBlank()) {
			return UNIT;
		}
		String s = requested.strip().toUpperCase();
		if (!COMMON.equals(s) && !UNIT.equals(s)) {
			throw new IllegalArgumentException("featureScope 는 COMMON 또는 UNIT 입니다: " + requested);
		}
		return s;
	}

	/**
	 * scope/spaceId = 연결하는 쪽, targetScope/targetSpaceId = featureId 가 가리키는 이슈.
	 * - COMMON Feature 는 최상위라 다른 Feature 에 연결하지 않는다.
	 * - UNIT Feature 는 COMMON Feature 아래에만.
	 * - Task 는 COMMON Feature 또는 같은 공간의 UNIT Feature 에.
	 */
	static void validateLink(String scope, String spaceId, String targetScope, String targetSpaceId) {
		if (targetScope == null) {
			throw new IllegalArgumentException("Feature 에만 연결할 수 있습니다");
		}
		if (COMMON.equals(scope)) {
			throw new IllegalArgumentException("공통 Feature 는 최상위라 다른 Feature 에 연결할 수 없습니다");
		}
		if (UNIT.equals(scope) && !COMMON.equals(targetScope)) {
			throw new IllegalArgumentException("단위 Feature 는 공통 Feature 아래에만 연결할 수 있습니다");
		}
		if (UNIT.equals(targetScope) && !java.util.Objects.equals(spaceId, targetSpaceId)) {
			throw new IllegalArgumentException("다른 작업 공간의 단위 Feature 에는 연결할 수 없습니다");
		}
	}

	/** COMMON 먼저, 그다음 정렬 키(보통 생성 순) */
	static <T> Comparator<T> commonFirst(java.util.function.Function<T, String> scopeOf, Comparator<T> then) {
		return Comparator.<T, Integer>comparing(t -> COMMON.equals(scopeOf.apply(t)) ? 0 : 1).thenComparing(then);
	}

}
