package tsh.d94.app.server.apService.study;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 블록 텍스트에서 단어장 단어 위치를 찾는다. 정규식을 쓰지 않으므로 "C++", "(EDI)"도 글자 그대로 매칭된다.
 * 대소문자 무시, 왼쪽은 항상 단어 경계(앞 글자가 문자·숫자가 아님), 오른쪽은 영숫자로 끝나는 단어만 경계 검사
 * (한글은 "트랜잭션은"처럼 조사가 붙어도 매칭). 겹치면 시작이 빠른 것, 같으면 긴 것.
 * ponytail: 시작 글자별 목록 스캔 — 단어 수천 개까지 충분, 그 이상이면 Aho–Corasick.
 */
public final class TermMatcher {

	public record Entry(String termId, String normTerm) {
	}

	public record Hit(int start, int end, String termId) {
	}

	private final Map<Character, List<Entry>> byFirst = new HashMap<>();

	public TermMatcher(List<Entry> entries) {
		for (Entry e : entries) {
			if (!e.normTerm().isEmpty()) {
				byFirst.computeIfAbsent(e.normTerm().charAt(0), k -> new ArrayList<>()).add(e);
			}
		}
		byFirst.values().forEach(l -> l.sort(Comparator.comparingInt((Entry e) -> e.normTerm().length()).reversed()));
	}

	public static String normalize(String s) {
		return s == null ? "" : String.join(" ", s.toLowerCase(Locale.ROOT).trim().split("\\s+"));
	}

	public List<Hit> find(String text) {
		List<Hit> hits = new ArrayList<>();
		if (text == null || byFirst.isEmpty()) {
			return hits;
		}
		int len = text.length();
		int i = 0;
		outer:
		while (i < len) {
			if (i > 0 && Character.isLetterOrDigit(text.charAt(i - 1))) {
				i++;
				continue;
			}
			List<Entry> candidates = byFirst.get(Character.toLowerCase(text.charAt(i)));
			if (candidates != null) {
				for (Entry e : candidates) {
					String t = e.normTerm();
					int end = i + t.length();
					if (end <= len && text.regionMatches(true, i, t, 0, t.length())
						&& (!isAsciiAlnum(t.charAt(t.length() - 1)) || end == len || !isAsciiAlnum(text.charAt(end)))) {
						hits.add(new Hit(i, end, e.termId()));
						i = end;
						continue outer;
					}
				}
			}
			i++;
		}
		return hits;
	}

	private static boolean isAsciiAlnum(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
	}
}
