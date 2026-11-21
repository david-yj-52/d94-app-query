package tsh.d94.app.server.apService.docs;

import static tsh.d94.app.server.apService.docs.DocsException.Code.*;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.docs.DocsDtos.LinkRef;
import tsh.d94.app.server.apService.docs.DocsDtos.MoveNoteRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteSummary;
import tsh.d94.app.server.apService.docs.DocsDtos.VersionResponse;
import tsh.d94.app.server.apService.docs.DocsMarkdownIndexer.Index;
import tsh.d94.app.server.data.orm.docs.docsIndex.DocsIndexAccess;
import tsh.d94.app.server.data.orm.docs.docsNote.SnDocsNoteAccess;
import tsh.d94.app.server.data.orm.docs.docsNote.SnDocsNoteModel;

/**
 * 노트 CRUD. 본문(Markdown)이 원천이고, 저장할 때마다 버전 1건을 남기고 색인을 다시 만든다.
 * 제목을 바꾸면 그 제목을 링크한 내 노트들의 [[옛 제목]] 을 같은 트랜잭션에서 고친다.
 */
@Service
@RequiredArgsConstructor
public class DocsNoteService {

	/** [[ ]] 링크를 깨뜨리는 문자 */
	static final String FORBIDDEN = "[]|#";

	private final SnDocsNoteAccess noteAccess;
	private final DocsIndexAccess index;
	private final DocsFolderService folders;

	/** 없거나 남의 노트면 404 (존재 여부를 숨긴다) */
	public SnDocsNoteModel requireOwned(String id) {
		return noteAccess.findOwned(id, Docs.owner()).orElseThrow(() -> new DocsException(NOT_FOUND));
	}

	public List<NoteSummary> list(String folderId, String q, String kind, String stream, String tag, String origin) {
		return index.listNotes(Docs.owner(), folderId, q, kind, stream, tag, origin).stream().map(NoteSummary::of)
			.toList();
	}

	public List<String> titles(String prefix) {
		return index.titles(Docs.owner(), prefix);
	}

	public NoteResponse get(String id) {
		return toResponse(requireOwned(id));
	}

	public NoteResponse getByTitle(String title) {
		return toResponse(noteAccess.findByTitle(Docs.owner(), title).orElseThrow(() -> new DocsException(NOT_FOUND)));
	}

	@Transactional
	public NoteResponse create(NoteRequest req) {
		String folderId = DocsFolderService.blankToNull(req.folderId());
		if (folderId != null) {
			folders.requireOwned(folderId);
		}
		String title = Docs.name(req.title(), 500, FORBIDDEN, "제목");
		requireFreeTitle(title, null);
		String content = req.content() == null ? "" : req.content();
		Index ix = DocsMarkdownIndexer.index(content);
		SnDocsNoteModel n = Docs.base(SnDocsNoteModel.builder(), "CreateDocsNote")
			.ownerId(Docs.owner())
			.folderId(folderId)
			.title(title)
			.content(content)
			.kind(ix.kind())
			.stream(ix.stream())
			.version(1)
			.build();
		n = save(n);
		afterSave(n, ix);
		return toResponse(n);
	}

	@Transactional
	public NoteResponse update(String id, NoteRequest req) {
		SnDocsNoteModel n = requireOwned(id);
		if (req.version() == null || !req.version().equals(n.getVersion())) {
			throw new DocsException(VERSION_CONFLICT);
		}
		String title = req.title() == null ? n.getTitle() : Docs.name(req.title(), 500, FORBIDDEN, "제목");
		String content = req.content() == null ? n.getContent() : req.content();
		String oldTitle = n.getTitle();
		if (!title.equals(oldTitle)) {
			requireFreeTitle(title, id);
			for (var ref : index.backlinks(Docs.owner(), oldTitle)) {
				if (!ref.id().equals(id)) {
					SnDocsNoteModel other = requireOwned(ref.id());
					write(other, other.getTitle(), DocsMarkdownIndexer.renameLinks(other.getContent(), oldTitle, title));
				}
			}
			content = DocsMarkdownIndexer.renameLinks(content, oldTitle, title); // 자기 자신 링크
		}
		write(n, title, content);
		return toResponse(n);
	}

	@Transactional
	public NoteResponse move(String id, MoveNoteRequest req) {
		SnDocsNoteModel n = requireOwned(id);
		String folderId = DocsFolderService.blankToNull(req.folderId());
		if (folderId != null) {
			folders.requireOwned(folderId);
		}
		n.setFolderId(folderId);
		return toResponse(save(n));
	}

	/** 물리 삭제. 버전·색인은 CASCADE. 이 노트를 가리키던 링크는 "없는 노트 링크"가 된다. */
	@Transactional
	public void delete(String id) {
		noteAccess.remove(requireOwned(id));
	}

	public List<VersionResponse> versions(String id) {
		requireOwned(id);
		return index.versions(id).stream()
			.map(v -> new VersionResponse(v.version(), v.title(), v.content(), v.editedBy(), v.editedAt())).toList();
	}

	public List<NoteSummary> backlinks(String id) {
		SnDocsNoteModel n = requireOwned(id);
		return index.backlinks(Docs.owner(), n.getTitle()).stream().filter(r -> !r.id().equals(id))
			.map(NoteSummary::of).toList();
	}

	/** 제목·본문을 바꿔 저장: 버전 +1, 버전 이력 1건, 색인 교체 */
	void write(SnDocsNoteModel n, String title, String content) {
		Index ix = DocsMarkdownIndexer.index(content);
		n.setTitle(title);
		n.setContent(content);
		n.setKind(ix.kind());
		n.setStream(ix.stream());
		n.setVersion(n.getVersion() + 1);
		save(n);
		afterSave(n, ix);
	}

	private void afterSave(SnDocsNoteModel n, Index ix) {
		index.addVersion(n.getObjId(), n.getVersion(), n.getTitle(), n.getContent(), Docs.owner());
		index.replaceIndex(n.getObjId(), ix.links(), ix.tags(), ix.tasks());
	}

	private void requireFreeTitle(String title, String selfId) {
		noteAccess.findByTitle(Docs.owner(), title)
			.filter(o -> !o.getObjId().equals(selfId))
			.ifPresent(o -> {
				throw new DocsException(DUPLICATE_TITLE);
			});
	}

	private SnDocsNoteModel save(SnDocsNoteModel n) {
		try {
			return noteAccess.saveAndFlush(n);
		} catch (DataIntegrityViolationException e) { // 동시에 같은 제목을 만든 경우
			throw new DocsException(DUPLICATE_TITLE);
		}
	}

	NoteResponse toResponse(SnDocsNoteModel n) {
		Index ix = DocsMarkdownIndexer.index(n.getContent());
		Set<String> titles = new HashSet<>();
		ix.links().forEach(l -> titles.add(l.toTitle()));
		Set<String> existing = new HashSet<>(index.existingTitles(Docs.owner(), titles));
		List<LinkRef> links = ix.links().stream()
			.map(l -> new LinkRef(l.toTitle(), l.rel(), existing.contains(l.toTitle()))).toList();
		return new NoteResponse(n.getObjId(), n.getTitle(), n.getContent(), n.getFolderId(), n.getKind(),
			n.getStream(), ix.origin(), Objects.requireNonNullElse(n.getVersion(), 1), index.tags(n.getObjId()), links,
			n.getCreatedAt(), n.getModifiedAt());
	}
}
