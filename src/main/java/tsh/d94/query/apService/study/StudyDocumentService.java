package tsh.d94.app.server.apService.study;

import static tsh.d94.app.server.apService.study.exception.StudyErrorCode.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.dto.StudyDtos.DocumentResponse;
import tsh.d94.app.server.apService.study.dto.StudyDtos.TocNode;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.data.orm.study.studyAnnotation.SnStudyAnnotationAccess;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockAccess;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentAccess;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentModel;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionAccess;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionModel;
import tsh.d94.app.server.data.orm.study.studyTerm.SnStudyTermAccess;
import tsh.d94.app.server.service.StorageService;

@Service
@RequiredArgsConstructor
public class StudyDocumentService {

	private final SnStudyDocumentAccess documentAccess;
	private final SnStudySectionAccess sectionAccess;
	private final SnStudyBlockAccess blockAccess;
	private final SnStudyTermAccess termAccess;
	private final SnStudyAnnotationAccess annotationAccess;
	private final StorageService storage;

	/** 없거나 남의 문서면 404 (존재 여부를 노출하지 않는다) */
	public SnStudyDocumentModel requireOwned(String id) {
		return documentAccess.findByObjIdAndOwnerId(id, Study.owner()).orElseThrow(() -> new StudyException(NOT_FOUND));
	}

	public SnStudyDocumentModel requireReady(String id) {
		SnStudyDocumentModel doc = requireOwned(id);
		if (!Study.READY.equals(doc.getStatus())) {
			throw new StudyException(NOT_READY);
		}
		return doc;
	}

	public Page<DocumentResponse> list(Pageable pageable) {
		return documentAccess.findByOwnerIdOrderByCreatedAtDesc(Study.owner(), pageable).map(DocumentResponse::of);
	}

	public DocumentResponse get(String id) {
		return DocumentResponse.of(requireOwned(id));
	}

	@Transactional
	public void delete(String id) {
		SnStudyDocumentModel doc = requireOwned(id);
		annotationAccess.deleteByDocumentId(id);
		termAccess.deleteByDocumentId(id);
		blockAccess.deleteByDocumentId(id);
		sectionAccess.deleteByDocumentId(id);
		documentAccess.remove(doc);
		storage.deletePrefix("study/docs/" + id + "/"); // 실패하면 DB 삭제도 롤백된다
	}

	public List<TocNode> toc(String id) {
		requireReady(id);
		List<SnStudySectionModel> sections = sectionAccess.findByDocumentIdOrderByOrd(id);
		Map<String, List<SnStudySectionModel>> children = new HashMap<>();
		List<SnStudySectionModel> roots = new ArrayList<>();
		for (SnStudySectionModel s : sections) {
			if (s.getParentId() == null) {
				roots.add(s);
			} else {
				children.computeIfAbsent(s.getParentId(), k -> new ArrayList<>()).add(s);
			}
		}
		return roots.stream().map(s -> node(s, children)).toList();
	}

	private TocNode node(SnStudySectionModel s, Map<String, List<SnStudySectionModel>> children) {
		return new TocNode(s.getObjId(), s.getTitle(), s.getLvl(), s.getPageSectionId(), s.getAnchorBlockKey(),
			children.getOrDefault(s.getObjId(), List.of()).stream().map(c -> node(c, children)).toList());
	}

	public InputStream figure(String id, String blockKey) {
		requireOwned(id);
		return blockAccess.findByDocumentIdAndBlockKey(id, blockKey)
			.filter(b -> "FIGURE".equals(b.getBlockTyp()) && b.getFigureKey() != null)
			.map(b -> storage.getObjectStream(b.getFigureKey()))
			.orElseThrow(() -> new StudyException(NOT_FOUND));
	}
}
