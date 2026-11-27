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
import tsh.d94.app.server.apService.docs.DocsDtos.GraphLink;
import tsh.d94.app.server.apService.docs.DocsDtos.GraphNode;
import tsh.d94.app.server.apService.docs.DocsDtos.GraphResponse;
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
 * 제목을 바꾸면 그 제목을 링크한 같은 vault 노트들의 [[옛 제목]] 을 같은 트랜잭션에서 고친다.
 * 제목·링크·계보는 vault 안에서만 유일·해석된다.
 */
@Service
@RequiredArgsConstructor
public class DocsNoteService {

	/** [[ ]] 링크를 깨뜨리는 문자 */
	static final String FORBIDDEN = "[]|#";

	private final SnDocsNoteAccess noteAccess;
	private final DocsIndexAccess index;
	private final DocsFolderService folders;
	private final DocsVaultService vaults;

	/** 없거나 접근 못 하는 vault 의 노트면 404 (존재 여부를 숨긴다) */
	public SnDocsNoteModel requireOwned(String id) {
		return noteAccess.findByIdOptional(id).filter(n -> vaults.canAccess(n.getVaultId()))
			.orElseThrow(() -> new DocsException(NOT_FOUND));
	}

	public List<NoteSummary> list(String vaultId, String folderId, String q, String kind, String stream, String tag,
		String origin) {
		return index.listNotes(vaults.scope(vaultId), folderId, q, kind, stream, tag, origin).stream()
			.map(NoteSummary::of).toList();
	}

	public List<String> titles(String vaultId, String prefix) {
		return index.titles(vaults.scope(vaultId), prefix);
	}

	public NoteResponse get(String id) {
		return toResponse(requireOwned(id));
	}

	/** 범위(통합이면 Private 먼저) 안에서 제목이 같은 첫 노트 */
	public NoteResponse getByTitle(String title, String vaultId) {
		return vaults.scope(vaultId).stream().map(v -> noteAccess.findByTitle(v, title)).flatMap(java.util.Optional::stream)
			.findFirst().map(this::toResponse).orElseThrow(() -> new DocsException(NOT_FOUND));
	}

	@Transactional
	public NoteResponse create(NoteRequest req) {
		String folderId = DocsFolderService.blankToNull(req.folderId());
		String vaultId = folderId != null ? folders.requireOwned(folderId).getVaultId() : vaults.target(req.vaultId());
		return toResponse(createIn(vaultId, folderId, req.title(), req.content()));
	}

	/** vault·폴더 권한 검사 없이 만든다 — 호출 쪽(create, CIRA 연동)이 검사한다 */
	@Transactional
	public SnDocsNoteModel createIn(String vaultId, String folderId, String rawTitle, String rawContent) {
		String title = Docs.name(rawTitle, 500, FORBIDDEN, "제목");
		requireFreeTitle(vaultId, title, null);
		String content = rawContent == null ? "" : rawContent;
		Index ix = DocsMarkdownIndexer.index(content);
		SnDocsNoteModel n = Docs.base(SnDocsNoteModel.builder(), "CreateDocsNote")
			.ownerId(Docs.owner())
			.vaultId(vaultId)
			.folderId(folderId)
			.title(title)
			.content(content)
			.kind(ix.kind())
			.stream(ix.stream())
			.version(1)
			.build();
		n = save(n);
		afterSave(n, ix);
		return n;
	}

	@Transactional
	public NoteResponse update(String id, NoteRequest req) {
		SnDocsNoteModel n = requireOwned(id);
		if (req.version() == null || !req.version().equals(n.getVersion())) {
			throw new DocsException(VERSION_CONFLICT);
		}
		String title = req.title() == null ? n.getTitle() : Docs.name(req.title(), 500, FORBIDDEN, "제목");
		String content = req.content() == null ? n.getContent() : req.content();
		writeRenamed(n, title, content);
		return toResponse(n);
	}

	/**
	 * 그래프: noteId 가 없으면 vault(생략·ALL = 접근 가능한 전체)의 모든 노트·링크,
	 * 있으면 그 노트의 vault 에서 depth(1~5) 단계 안에 이어진 노트만 (로컬 그래프, 링크 방향 무시).
	 */
	public GraphResponse graph(String vaultId, String noteId, int depth) {
		List<String> scope = noteId == null ? vaults.scope(vaultId) : List.of(requireOwned(noteId).getVaultId());
		List<GraphLink> links = index.graphEdges(scope).stream().map(e -> new GraphLink(e.fromId(), e.toId(), e.rel())).toList();
		Set<String> keep = null;
		if (noteId != null) {
			keep = new HashSet<>(Set.of(noteId));
			Set<String> frontier = Set.of(noteId);
			for (int d = 0; d < Math.max(1, Math.min(depth, 5)) && !frontier.isEmpty(); d++) {
				Set<String> next = new HashSet<>();
				for (GraphLink l : links) {
					if (frontier.contains(l.source())) next.add(l.target());
					if (frontier.contains(l.target())) next.add(l.source());
				}
				next.removeAll(keep);
				keep.addAll(next);
				frontier = next;
			}
		}
		Set<String> k = keep;
		List<GraphNode> nodes = index.graphNotes(scope).stream().filter(r -> k == null || k.contains(r.id()))
			.map(r -> new GraphNode(r.id(), r.vaultId(), r.title(), r.kind(), r.stream())).toList();
		Set<String> ids = new HashSet<>(nodes.stream().map(GraphNode::id).toList());
		return new GraphResponse(nodes, links.stream().filter(l -> ids.contains(l.source()) && ids.contains(l.target())).toList());
	}

	/** 권한·version 검사 없이 제목만 바꾼다 (CIRA 이슈 제목 동기화). 링크 치환은 update 와 같다 */
	@Transactional
	public void renameInternal(SnDocsNoteModel n, String rawTitle) {
		writeRenamed(n, Docs.name(rawTitle, 500, FORBIDDEN, "제목"), n.getContent());
	}

	/** 제목이 바뀌면 같은 vault 의 [[옛 제목]] 을 새 제목으로 고친 뒤 저장 */
	private void writeRenamed(SnDocsNoteModel n, String title, String content) {
		String id = n.getObjId();
		String oldTitle = n.getTitle();
		if (!title.equals(oldTitle)) {
			requireFreeTitle(n.getVaultId(), title, id);
			for (var ref : index.backlinks(n.getVaultId(), oldTitle)) {
				if (!ref.id().equals(id)) {
					SnDocsNoteModel other = noteAccess.findById(ref.id()); // 같은 vault
					write(other, other.getTitle(), DocsMarkdownIndexer.renameLinks(other.getContent(), oldTitle, title));
				}
			}
			content = DocsMarkdownIndexer.renameLinks(content, oldTitle, title); // 자기 자신 링크
		}
		write(n, title, content);
	}

	@Transactional
	public NoteResponse move(String id, MoveNoteRequest req) {
		SnDocsNoteModel n = requireOwned(id);
		String folderId = DocsFolderService.blankToNull(req.folderId());
		if (folderId != null && !folders.requireOwned(folderId).getVaultId().equals(n.getVaultId())) {
			// ponytail: vault 간 이동은 막는다(링크·계보가 vault 단위). 필요하면 복사 후 삭제
			throw new DocsException(INVALID_REQUEST, "다른 vault 의 폴더로는 옮길 수 없습니다.");
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
		return index.backlinks(n.getVaultId(), n.getTitle()).stream().filter(r -> !r.id().equals(id))
			.map(NoteSummary::of).toList();
	}

	/** 권한·version 검사 없이 최신 행에 저장 (CIRA 진행 경과 등 서버 내부 기록). 호출 쪽이 최신 행을 읽어 고친다 */
	@Transactional
	public void writeInternal(SnDocsNoteModel n, String title, String content) {
		write(n, title, content);
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

	public boolean titleTaken(String vaultId, String title) {
		return noteAccess.findByTitle(vaultId, title).isPresent();
	}

	private void requireFreeTitle(String vaultId, String title, String selfId) {
		noteAccess.findByTitle(vaultId, title)
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
		Set<String> existing = new HashSet<>(index.existingTitles(n.getVaultId(), titles));
		List<LinkRef> links = ix.links().stream()
			.map(l -> new LinkRef(l.toTitle(), l.rel(), existing.contains(l.toTitle()))).toList();
		return new NoteResponse(n.getObjId(), n.getVaultId(), n.getTitle(), n.getContent(), n.getFolderId(), n.getKind(),
			n.getStream(), ix.origin(), Objects.requireNonNullElse(n.getVersion(), 1), index.tags(n.getObjId()), links,
			n.getCreatedAt(), n.getModifiedAt());
	}
}
