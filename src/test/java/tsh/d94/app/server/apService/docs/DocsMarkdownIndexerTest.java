package tsh.d94.app.server.apService.docs;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import tsh.d94.app.server.apService.docs.DocsMarkdownIndexer.Index;
import tsh.d94.app.server.data.orm.docs.docsIndex.DocsIndexAccess.Link;
import tsh.d94.app.server.data.orm.docs.docsIndex.DocsIndexAccess.Task;

class DocsMarkdownIndexerTest {

	private static final String NOTE = """
		---
		kind: Doc
		stream: dev
		origin: [[아이디어 A]]
		tags: [plan, "#web"]
		---
		# 제목은 태그가 아님
		본문 [[노트 B]] 와 [[노트 C|별칭]], [[노트 D#헤딩]], 다시 [[노트 B]].
		#설계/백엔드 그리고 #123 숫자만은 태그 아님, url http://x.com/a#frag 도 아님
		- [ ] 할 일 1 📅 2026-12-01
		- [/] 진행 중
		* [x] 끝난 일
		- [] 체크박스 아님
		""";

	@Test
	void index_extractsFrontmatterLinksTagsTasks() {
		Index ix = DocsMarkdownIndexer.index(NOTE);

		assertThat(ix.kind()).isEqualTo("doc");
		assertThat(ix.stream()).isEqualTo("dev");
		assertThat(ix.origin()).isEqualTo("아이디어 A");
		assertThat(ix.links()).containsExactly(new Link("아이디어 A", "origin"), new Link("노트 B", "link"),
			new Link("노트 C", "link"), new Link("노트 D", "link"));
		assertThat(ix.tags()).containsExactly("plan", "web", "설계/백엔드");
		assertThat(ix.tasks()).containsExactly(
			new Task(9, "할 일 1 📅 2026-12-01", "todo", LocalDate.of(2026, 12, 1)),
			new Task(10, "진행 중", "doing", null),
			new Task(11, "끝난 일", "done", null));
	}

	@Test
	void index_defaultsAndBrokenYaml() {
		assertThat(DocsMarkdownIndexer.index(null).kind()).isEqualTo("idea");
		Index ix = DocsMarkdownIndexer.index("---\nkind: [unclosed\n---\n[[A]]");
		assertThat(ix.kind()).isEqualTo("idea");
		assertThat(ix.links()).containsExactly(new Link("A", "link"));
		assertThat(DocsMarkdownIndexer.index("---\nkind: weird\norigin: \"[[X]]\"\n---\n").kind()).isEqualTo("idea");
		assertThat(DocsMarkdownIndexer.index("---\norigin: \"[[X]]\"\n---\n").origin()).isEqualTo("X");
	}

	@Test
	void renameLinks_keepsAliasAndHeading() {
		String out = DocsMarkdownIndexer.renameLinks("origin: \"[[A]]\"\n[[A]] [[A|별칭]] [[A#h]] [[AB]] [[ A ]]", "A",
			"새 A");
		assertThat(out).isEqualTo("origin: \"[[새 A]]\"\n[[새 A]] [[새 A|별칭]] [[새 A#h]] [[AB]] [[새 A]]");
	}

	@Test
	void setFrontmatter_replacesOrAdds() {
		assertThat(DocsMarkdownIndexer.setFrontmatter("본문", "kind", "doc")).isEqualTo("---\nkind: doc\n---\n본문");
		assertThat(DocsMarkdownIndexer.setFrontmatter("---\nkind: idea\nstream: dev\n---\n본문", "kind", "knowledge"))
			.isEqualTo("---\nkind: knowledge\nstream: dev\n---\n본문");
		assertThat(DocsMarkdownIndexer.setFrontmatter("---\nstream: dev\n---\n본문", "kind", "doc"))
			.isEqualTo("---\nstream: dev\nkind: doc\n---\n본문");
	}
}
