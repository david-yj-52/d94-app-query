package tsh.d94.app.server.apService.study;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tsh.d94.app.server.apService.study.TermMatcher.Entry;
import tsh.d94.app.server.apService.study.TermMatcher.Hit;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.apService.study.parser.ParserResponse.Section;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockModel;

/** study 순수 로직: 단어 매칭, 페이지 섹션 계산, 마킹 범위·quote */
class StudyLogicTest {

	private static List<int[]> spans(List<String> terms, String text) {
		List<Entry> entries = terms.stream().map(t -> new Entry(t, TermMatcher.normalize(t))).toList();
		return new TermMatcher(entries).find(text).stream().map(h -> new int[] {h.start(), h.end()}).toList();
	}

	@Test
	void caseInsensitiveWithEnglishWordBoundaries() {
		assertThat(spans(List.of("audit"), "Audit planning; the auditor audits.")).containsExactly(new int[] {0, 5});
	}

	@Test
	void koreanMatchesWithParticlesButNeedsLeftBoundary() {
		assertThat(spans(List.of("트랜잭션"), "트랜잭션은 커밋된 트랜잭션을")).containsExactly(new int[] {0, 4}, new int[] {10, 14});
		assertThat(spans(List.of("잭션"), "트랜잭션")).isEmpty();
	}

	@Test
	void longestWinsAtSameStartAndEarlierStartWinsOnOverlap() {
		List<Hit> hits = new TermMatcher(List.of(new Entry("short", "electronic"),
			new Entry("long", "electronic data interchange"))).find("Electronic Data Interchange (EDI)");
		assertThat(hits).containsExactly(new Hit(0, 27, "long"));
		assertThat(spans(List.of("data interchange", "interchange format"), "data interchange format"))
			.containsExactly(new int[] {0, 16});
	}

	@Test
	void matchesTermsWithSymbolsLiterally() {
		assertThat(spans(List.of("C++", "(EDI)"), "C++ and (EDI) and c+++"))
			.containsExactly(new int[] {0, 3}, new int[] {8, 13}, new int[] {18, 21});
	}

	@Test
	void normalizeCollapsesSpaces() {
		assertThat(TermMatcher.normalize("  Audit   Universe ")).isEqualTo("audit universe");
	}

	@Test
	void pageSectionRules() {
		Map<String, String> pages = PageSectionResolver.resolve(List.of(
			new Section("s1", null, 1, "Ch", 1, "b1"),
			new Section("s2", "s1", 2, "A", 1, "b2"),
			new Section("s3", "s2", 3, "A.1", 2, "b3"),
			new Section("s4", "s1", 2, "B", 3, "b4"),
			new Section("s5", null, 1, "Index", 4, "b5")));
		assertThat(pages).isEqualTo(Map.of("s1", "s2", "s2", "s2", "s3", "s2", "s4", "s4", "s5", "s5"));
		assertThat(PageSectionResolver.resolve(List.of(new Section("s0001", null, 1, "본문", 1, "b000001"))))
			.isEqualTo(Map.of("s0001", "s0001"));
	}

	private static SnStudyBlockModel block(int ord, String text) {
		return SnStudyBlockModel.builder().ord(ord).text(text).blockTyp("PARAGRAPH").build();
	}

	@Test
	void quoteSingleBlockUsesUtf16Offsets() {
		assertThat(AnnotationRange.validateAndQuote(block(1, "Audit planning is the initial stage"), 0,
			block(1, "Audit planning is the initial stage"), 14, List.of(), "p", "p")).isEqualTo("Audit planning");
		SnStudyBlockModel ko = block(1, "트랜잭션은 commit 이후");
		assertThat(AnnotationRange.validateAndQuote(ko, 0, ko, 5, List.of(), "p", "p")).isEqualTo("트랜잭션은");
	}

	@Test
	void multiBlockQuoteJoinsWithNewlineAndTruncates() {
		assertThat(AnnotationRange.validateAndQuote(block(1, "AB"), 1, block(3, "EF"), 1, List.of(block(2, "CD")),
			"p", "p")).isEqualTo("B\nCD\nE");
		SnStudyBlockModel big = block(1, "x".repeat(1500));
		String q = AnnotationRange.validateAndQuote(big, 0, big, 1500, List.of(), "p", "p");
		assertThat(q).hasSize(1000).endsWith("…");
	}

	@Test
	void rejectsInvalidRanges() {
		SnStudyBlockModel a = block(1, "abc");
		SnStudyBlockModel b = block(2, "def");
		assertThatThrownBy(() -> AnnotationRange.validateAndQuote(b, 0, a, 1, List.of(), "p", "p"))
			.isInstanceOf(StudyException.class);
		assertThatThrownBy(() -> AnnotationRange.validateAndQuote(a, 2, a, 2, List.of(), "p", "p"))
			.isInstanceOf(StudyException.class);
		assertThatThrownBy(() -> AnnotationRange.validateAndQuote(a, 0, a, 4, List.of(), "p", "p"))
			.isInstanceOf(StudyException.class);
		assertThatThrownBy(() -> AnnotationRange.validateAndQuote(a, 0, b, 1, List.of(), "p1", "p2"))
			.isInstanceOf(StudyException.class);
	}
}
