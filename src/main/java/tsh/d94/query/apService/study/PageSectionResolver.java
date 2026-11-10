package tsh.d94.app.server.apService.study;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tsh.d94.app.server.apService.study.parser.ParserResponse.Section;

/**
 * 섹션 → 화면 "페이지" 섹션. 깊이 2와 자식 없는 깊이 1은 자기 자신, 자식 있는 깊이 1은 첫 자식의 페이지,
 * 깊이 3 이상은 가장 가까운 깊이 2 조상(깊이 2 조상이 없으면 자기 자신).
 */
final class PageSectionResolver {

	private PageSectionResolver() {
	}

	static Map<String, String> resolve(List<Section> sections) {
		Map<String, Section> byKey = new HashMap<>();
		Map<String, String> firstChild = new HashMap<>();
		for (Section s : sections) {
			byKey.put(s.key(), s);
			if (s.parentKey() != null) {
				firstChild.putIfAbsent(s.parentKey(), s.key());
			}
		}
		Map<String, String> result = new HashMap<>();
		for (Section s : sections) {
			result.put(s.key(), page(s, byKey, firstChild));
		}
		return result;
	}

	private static String page(Section s, Map<String, Section> byKey, Map<String, String> firstChild) {
		if (s.level() == 2) {
			return s.key();
		}
		if (s.level() < 2) {
			String child = firstChild.get(s.key());
			return child == null ? s.key() : page(byKey.get(child), byKey, firstChild);
		}
		Section parent = s.parentKey() == null ? null : byKey.get(s.parentKey());
		return parent == null || parent.level() < 2 ? s.key() : page(parent, byKey, firstChild);
	}
}
