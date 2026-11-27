package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 2단계 Feature 규칙 + Task 설명(메인 노트 본문) 교체 규칙 */
class FeatureRulesTest {

	@Test
	void scope_onlyFeatureHasScope_defaultUnit() {
		assertThat(FeatureRules.scope(false, null)).isNull();
		assertThat(FeatureRules.scope(true, null)).isEqualTo("UNIT");
		assertThat(FeatureRules.scope(true, "common")).isEqualTo("COMMON");
		assertThatThrownBy(() -> FeatureRules.scope(false, "COMMON")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> FeatureRules.scope(true, "EPIC")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void validateLink() {
		// Task → COMMON, Task → 같은 공간 UNIT
		assertThatCode(() -> FeatureRules.validateLink(null, "hw", "COMMON", null)).doesNotThrowAnyException();
		assertThatCode(() -> FeatureRules.validateLink(null, "hw", "UNIT", "hw")).doesNotThrowAnyException();
		// Task → 다른 공간 UNIT, Task → Feature 아닌 이슈
		assertThatThrownBy(() -> FeatureRules.validateLink(null, "hw", "UNIT", "sw")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> FeatureRules.validateLink(null, "hw", null, "hw")).isInstanceOf(IllegalArgumentException.class);
		// UNIT → COMMON 만, COMMON → 아무것도 안 됨
		assertThatCode(() -> FeatureRules.validateLink("UNIT", "hw", "COMMON", null)).doesNotThrowAnyException();
		assertThatThrownBy(() -> FeatureRules.validateLink("UNIT", "hw", "UNIT", "hw")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> FeatureRules.validateLink("COMMON", null, "COMMON", null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void commonFirst_evenIfCreatedLater() {
		record F(String scope, int created) {}
		List<F> list = new ArrayList<>(List.of(new F("UNIT", 1), new F("COMMON", 3), new F("UNIT", 2), new F("COMMON", 4)));
		list.sort(FeatureRules.commonFirst(F::scope, Comparator.comparingInt(F::created)));
		assertThat(list).extracting(F::created).containsExactly(3, 4, 1, 2);
	}

	@Test
	void withDescription_keepsFrontmatterAndProgress() {
		String note = "---\nkind: task\nissueKey: \"A-1\"\n---\n옛 설명\n\n## 진행 경과\n- 2026-11-26 10:00 | 생성 (by a)\n";
		String out = CiraDocsService.withDescription(note, "새 설명\n## 진행 경과\n- 가짜 기록");
		assertThat(out).startsWith("---\nkind: task\nissueKey: \"A-1\"\n---\n새 설명\n")
			.contains("## 진행 경과\n- 2026-11-26 10:00 | 생성 (by a)")
			.doesNotContain("옛 설명", "가짜 기록");
		assertThat(CiraDocsService.description(out)).isEqualTo("새 설명");
		// 진행 경과 섹션이 없는 노트
		assertThat(CiraDocsService.withDescription("본문만", "바뀐 본문")).isEqualTo("바뀐 본문\n");
	}
}
