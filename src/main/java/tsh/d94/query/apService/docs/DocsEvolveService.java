package tsh.d94.app.server.apService.docs;

import static tsh.d94.app.server.apService.docs.DocsException.Code.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.docs.DocsDtos.DeriveRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.LineageNode;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.TaskResponse;
import tsh.d94.app.server.data.orm.docs.docsIndex.DocsIndexAccess;
import tsh.d94.app.server.data.orm.docs.docsNote.SnDocsNoteModel;

/**
 * 문서의 진화: 승격(kind 변경)·파생(브로드캐스트·결과 문서)·Task(체크박스 줄) 상태 변경·노트 승격·계보.
 * 모두 Markdown 본문을 고쳐 저장하는 단축 동작이다 — 원천은 언제나 본문.
 */
@Service
@RequiredArgsConstructor
public class DocsEvolveService {

	private static final Set<String> TASK_STATUSES = Set.of("todo", "doing", "done");

	private final DocsNoteService notes;
	private final DocsIndexAccess index;

	/** 원본에서 갈라진 새 노트: frontmatter kind·stream·origin, 본문 첫 줄에 원본 인용. 원본과 같은 폴더 */
	@Transactional
	public NoteResponse derive(String id, DeriveRequest req) {
		SnDocsNoteModel origin = notes.requireOwned(id);
		String kind = kind(req.kind() == null ? "doc" : req.kind());
		String stream = req.stream() == null || req.stream().isBlank() ? null
			: Docs.name(req.stream(), 50, ":\"'[]|#", "트랙");
		String fm = "kind: " + kind + "\n" + (stream == null ? "" : "stream: " + stream + "\n")
			+ "origin: \"[[" + origin.getTitle() + "]]\"";
		String content = "---\n" + fm + "\n---\n> 원본: [[" + origin.getTitle() + "]]\n\n";
		return notes.create(new NoteRequest(req.title(), content, origin.getFolderId(), null));
	}

	/** 승격: frontmatter kind 만 바꿔 저장 (버전 1건) */
	@Transactional
	public NoteResponse changeKind(String id, String kind) {
		SnDocsNoteModel n = notes.requireOwned(id);
		notes.write(n, n.getTitle(), DocsMarkdownIndexer.setFrontmatter(n.getContent(), "kind", kind(kind)));
		return notes.toResponse(n);
	}

	public List<TaskResponse> tasks(String status, String tag, String stream, LocalDate dueBefore) {
		return index.tasks(Docs.owner(), status, tag, stream, dueBefore).stream()
			.map(t -> new TaskResponse(t.noteId(), t.noteTitle(), t.lineNo(), t.text(), t.status(), t.dueDt()))
			.toList();
	}

	/** 체크박스 줄의 상태 문자만 바꾼다. 그 줄이 더 이상 체크박스가 아니면(다른 곳에서 수정됨) 409 */
	@Transactional
	public NoteResponse setTaskStatus(String noteId, int lineNo, String status) {
		if (status == null || !TASK_STATUSES.contains(status)) {
			throw new DocsException(INVALID_REQUEST, "status 는 todo·doing·done 중 하나입니다.");
		}
		SnDocsNoteModel n = notes.requireOwned(noteId);
		String[] lines = lines(n);
		Matcher m = taskLine(lines, lineNo);
		lines[lineNo] = m.group(1) + DocsMarkdownIndexer.mark(status) + m.group(3) + m.group(4);
		notes.write(n, n.getTitle(), String.join("\n", lines));
		return notes.toResponse(n);
	}

	/**
	 * 체크박스 줄을 노트로 승격: 줄 내용(마감 표시·링크 금지 문자 제외)을 제목으로 kind: task 노트를 만들고,
	 * 그 줄을 `- [ ] [[제목]]` 으로 바꾼다. 새 노트를 돌려준다.
	 */
	@Transactional
	public NoteResponse promoteTask(String noteId, int lineNo) {
		SnDocsNoteModel n = notes.requireOwned(noteId);
		String[] lines = lines(n);
		Matcher m = taskLine(lines, lineNo);
		String title = m.group(4).replaceAll("📅\\s*\\d{4}-\\d{2}-\\d{2}", "").replaceAll("[\\[\\]|#]", "").trim();
		NoteResponse created = notes.create(new NoteRequest(title,
			"---\nkind: task\norigin: \"[[" + n.getTitle() + "]]\"\n---\n", n.getFolderId(), null));
		lines[lineNo] = m.group(1) + m.group(2) + m.group(3) + "[[" + created.title() + "]]";
		notes.write(n, n.getTitle(), String.join("\n", lines));
		return created;
	}

	public List<LineageNode> lineage(String id) {
		notes.requireOwned(id);
		return index.lineage(Docs.owner(), id).stream()
			.map(r -> new LineageNode(r.id(), r.title(), r.kind(), r.stream(), r.originTitle())).toList();
	}

	private static String kind(String raw) {
		String k = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
		if (!DocsMarkdownIndexer.KINDS.contains(k)) {
			throw new DocsException(INVALID_REQUEST, "kind 는 idea·doc·task·knowledge 중 하나입니다.");
		}
		return k;
	}

	/** 줄 번호를 색인과 같게 맞추려고 \r\n 도 줄 끝으로 본다(\r 은 줄 안에 남기지 않음) */
	private static String[] lines(SnDocsNoteModel n) {
		return (n.getContent() == null ? "" : n.getContent()).split("\\r?\\n", -1);
	}

	private static Matcher taskLine(String[] lines, int lineNo) {
		Matcher m = lineNo >= 0 && lineNo < lines.length ? DocsMarkdownIndexer.TASK.matcher(lines[lineNo]) : null;
		if (m == null || !m.matches()) {
			throw new DocsException(VERSION_CONFLICT, "그 줄은 더 이상 체크박스가 아닙니다. 새로고침 후 다시 시도하세요.");
		}
		return m;
	}
}
