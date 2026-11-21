package tsh.d94.app.server.apService.docs;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tsh.d94.app.server.apService.docs.DocsDtos.FolderRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.FolderResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.LinkRef;
import tsh.d94.app.server.apService.docs.DocsDtos.MoveFolderRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.MoveNoteRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteSummary;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/** 노트 링크·백링크·이름 변경·버전, 폴더 규칙, 개인 vault 격리. */
@IntegrationTest
class DocsFlowIT {

	@Autowired DocsNoteService notes;
	@Autowired DocsFolderService folders;

	private String me;

	@BeforeEach
	void setUp() {
		me = CiraFixtures.uniqueEmail("docs");
		TestContexts.login(me);
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private static NoteRequest note(String title, String content) {
		return new NoteRequest(title, content, null, null);
	}

	private static DocsException.Code codeOf(Throwable t) {
		assertThat(t).isInstanceOf(DocsException.class);
		return ((DocsException)t).getCode();
	}

	@Test
	void links_backlinks_rename_versions() {
		NoteResponse a = notes.create(note("A", "---\nkind: doc\n---\nB 를 본다 [[B]] #설계\n- [ ] 할 일"));
		assertThat(a.kind()).isEqualTo("doc");
		assertThat(a.tags()).containsExactly("설계");
		assertThat(a.links()).containsExactly(new LinkRef("B", "link", false)); // 아직 없는 노트

		NoteResponse b = notes.create(note("B", "본문"));
		assertThat(notes.get(a.id()).links()).containsExactly(new LinkRef("B", "link", true));
		assertThat(notes.backlinks(b.id())).extracting(NoteSummary::title).containsExactly("A");

		// B 이름 변경 → A 본문의 [[B]] 가 [[B2]] 로, A 버전도 올라감
		NoteResponse b2 = notes.update(b.id(), new NoteRequest("B2", "본문 v2", null, b.version()));
		assertThat(b2.version()).isEqualTo(2);
		NoteResponse a2 = notes.get(a.id());
		assertThat(a2.content()).contains("[[B2]]").doesNotContain("[[B]]");
		assertThat(a2.version()).isEqualTo(2);
		assertThat(notes.backlinks(b.id())).extracting(NoteSummary::title).containsExactly("A");
		assertThat(notes.versions(b.id())).extracting(DocsDtos.VersionResponse::title).containsExactly("B2", "B");

		// 낙관적 잠금: 옛 버전으로 저장하면 409
		assertThat(codeOf(catchThrowable(() -> notes.update(b.id(), new NoteRequest("B2", "x", null, 1)))))
			.isEqualTo(DocsException.Code.VERSION_CONFLICT);
		// 제목 중복
		assertThat(codeOf(catchThrowable(() -> notes.create(note("A", ""))))).isEqualTo(DocsException.Code.DUPLICATE_TITLE);
		// 링크를 깨는 문자 금지
		assertThat(codeOf(catchThrowable(() -> notes.create(note("a|b", ""))))).isEqualTo(DocsException.Code.INVALID_REQUEST);

		// 목록 필터
		assertThat(notes.list(null, null, "doc", null, null, null)).extracting(NoteSummary::title).containsExactly("A");
		assertThat(notes.list(null, "v2", null, null, null, null)).extracting(NoteSummary::title).containsExactly("B2");
		assertThat(notes.list(null, null, null, null, "설계", null)).extracting(NoteSummary::title).containsExactly("A");

		// 삭제하면 A 의 링크는 다시 없는 노트 링크
		notes.delete(b.id());
		assertThat(notes.get(a.id()).links()).containsExactly(new LinkRef("B2", "link", false));
	}

	@Test
	void folders_rules() {
		FolderResponse dev = folders.create(new FolderRequest("개발", null));
		FolderResponse design = folders.create(new FolderRequest("설계", dev.id()));
		assertThat(codeOf(catchThrowable(() -> folders.create(new FolderRequest("개발", null)))))
			.isEqualTo(DocsException.Code.DUPLICATE_FOLDER);
		folders.create(new FolderRequest("설계", null)); // 다른 상위면 같은 이름 허용

		// 자기 하위로 이동 거부
		assertThat(codeOf(catchThrowable(() -> folders.move(dev.id(), new MoveFolderRequest(design.id(), null)))))
			.isEqualTo(DocsException.Code.INVALID_MOVE);

		NoteResponse n = notes.create(new NoteRequest("폴더 안 노트", "", design.id(), null));
		assertThat(notes.list(design.id(), null, null, null, null, null)).hasSize(1);
		assertThat(notes.list("", null, null, null, null, null)).isEmpty();

		// 노트가 있으면 삭제 거부 → 옮기고 나면 삭제
		assertThat(codeOf(catchThrowable(() -> folders.delete(design.id())))).isEqualTo(DocsException.Code.FOLDER_NOT_EMPTY);
		notes.move(n.id(), new MoveNoteRequest(null));
		folders.delete(design.id());
		assertThat(folders.list()).extracting(FolderResponse::name).containsExactlyInAnyOrder("개발", "설계");
	}

	@Test
	void personalVault_isolated() {
		NoteResponse mine = notes.create(note("같은 제목", "내 노트"));
		FolderResponse myFolder = folders.create(new FolderRequest("내 폴더", null));

		TestContexts.login(CiraFixtures.uniqueEmail("other"));
		assertThat(codeOf(catchThrowable(() -> notes.get(mine.id())))).isEqualTo(DocsException.Code.NOT_FOUND);
		assertThat(codeOf(catchThrowable(() -> folders.delete(myFolder.id())))).isEqualTo(DocsException.Code.NOT_FOUND);
		assertThat(notes.list(null, null, null, null, null, null)).isEmpty();
		NoteResponse theirs = notes.create(note("같은 제목", "다른 사람 노트")); // 제목은 vault 마다 유일
		assertThat(theirs.id()).isNotEqualTo(mine.id());
		assertThat(notes.titles("같")).isEqualTo(List.of("같은 제목"));
	}
}
