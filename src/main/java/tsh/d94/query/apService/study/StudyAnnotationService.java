package tsh.d94.app.server.apService.study;

import static tsh.d94.app.server.apService.study.exception.StudyErrorCode.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.dto.StudyDtos.AnnotationListItem;
import tsh.d94.app.server.apService.study.dto.StudyDtos.CreateAnnotationRequest;
import tsh.d94.app.server.apService.study.dto.StudyDtos.NoteLocation;
import tsh.d94.app.server.apService.study.dto.StudyDtos.UpdateAnnotationRequest;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.data.orm.study.studyAnnotation.SnStudyAnnotationAccess;
import tsh.d94.app.server.data.orm.study.studyAnnotation.SnStudyAnnotationModel;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockAccess;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockModel;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentModel;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionAccess;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionModel;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

/** ponytail: 노트 목록은 검색·페이지네이션 없이 전체 반환(혼자 쓰는 앱). 느려지면 Pageable 추가. */
@Service
@RequiredArgsConstructor
public class StudyAnnotationService {

	static final Set<String> COLORS = Set.of("yellow", "green", "blue", "pink");

	private final StudyDocumentService documents;
	private final SnStudyAnnotationAccess annotationAccess;
	private final SnStudyBlockAccess blockAccess;
	private final SnStudySectionAccess sectionAccess;

	public AnnotationListItem create(CreateAnnotationRequest req) {
		if (req.color() == null || !COLORS.contains(req.color())) {
			throw new StudyException(INVALID_REQUEST, "색은 yellow, green, blue, pink 중 하나입니다.");
		}
		SnStudyDocumentModel doc = documents.requireReady(req.documentId());
		SnStudyBlockModel start = block(doc.getObjId(), req.startBlockKey());
		SnStudyBlockModel end = block(doc.getObjId(), req.endBlockKey());
		List<SnStudyBlockModel> between = end.getOrd() - start.getOrd() < 2 ? List.of()
			: blockAccess.findByDocumentIdAndOrdBetweenOrderByOrd(doc.getObjId(), start.getOrd() + 1, end.getOrd() - 1)
				.stream().filter(b -> !"TABLE".equals(b.getBlockTyp()) && !"FIGURE".equals(b.getBlockTyp())).toList();
		String quote = AnnotationRange.validateAndQuote(start, req.startOffset(), end, req.endOffset(), between,
			section(start).getPageSectionId(), section(end).getPageSectionId());
		SnStudyAnnotationModel saved = annotationAccess.save(Study.base(SnStudyAnnotationModel.builder(),
				"CreateStudyAnnotation", TenantContext.get())
			.ownerId(Study.owner())
			.documentId(doc.getObjId())
			.startBlockKey(start.getBlockKey())
			.startOffset(req.startOffset())
			.endBlockKey(end.getBlockKey())
			.endOffset(req.endOffset())
			.quote(quote)
			.color(req.color())
			.note(blankToNull(req.note()))
			.build());
		return toItem(saved, doc.getTitle(), new HashMap<>());
	}

	public AnnotationListItem update(String id, UpdateAnnotationRequest req) {
		SnStudyAnnotationModel a = owned(id);
		if (req.color() != null) {
			if (!COLORS.contains(req.color())) {
				throw new StudyException(INVALID_REQUEST, "색은 yellow, green, blue, pink 중 하나입니다.");
			}
			a.setColor(req.color());
		}
		if (req.note() != null) {
			a.setNote(blankToNull(req.note()));
		}
		annotationAccess.save(a);
		return toItem(a, documents.requireOwned(a.getDocumentId()).getTitle(), new HashMap<>());
	}

	public void delete(String id) {
		annotationAccess.remove(owned(id));
	}

	public List<AnnotationListItem> list(String documentId) {
		String owner = Study.owner();
		List<SnStudyAnnotationModel> items;
		if (documentId == null || documentId.isBlank()) {
			items = annotationAccess.findByOwnerIdOrderByCreatedAtDesc(owner);
		} else {
			documents.requireOwned(documentId);
			items = annotationAccess.findByOwnerIdAndDocumentIdOrderByCreatedAtDesc(owner, documentId);
		}
		Map<String, String> titles = new HashMap<>();
		Map<String, SnStudySectionModel> sectionCache = new HashMap<>();
		return items.stream()
			.map(a -> toItem(a, titles.computeIfAbsent(a.getDocumentId(), d -> documents.requireOwned(d).getTitle()),
				sectionCache))
			.toList();
	}

	private AnnotationListItem toItem(SnStudyAnnotationModel a, String documentTitle,
		Map<String, SnStudySectionModel> sectionCache) {
		SnStudyBlockModel start = blockAccess.findByDocumentIdAndBlockKey(a.getDocumentId(), a.getStartBlockKey())
			.orElse(null);
		SnStudySectionModel section = start == null ? null
			: sectionCache.computeIfAbsent(start.getSectionId(), sectionAccess::findById);
		return new AnnotationListItem(a.getObjId(), a.getDocumentId(), documentTitle,
			section == null ? "" : section.getTitle(), a.getQuote(), a.getNote(), a.getColor(), a.getCreatedAt(),
			new NoteLocation(a.getDocumentId(), section == null ? null : section.getPageSectionId(),
				a.getStartBlockKey()));
	}

	private SnStudyBlockModel block(String documentId, String blockKey) {
		return blockAccess.findByDocumentIdAndBlockKey(documentId, blockKey)
			.orElseThrow(() -> new StudyException(INVALID_RANGE));
	}

	private SnStudySectionModel section(SnStudyBlockModel b) {
		return sectionAccess.findById(b.getSectionId());
	}

	private SnStudyAnnotationModel owned(String id) {
		return annotationAccess.findByObjIdAndOwnerId(id, Study.owner())
			.orElseThrow(() -> new StudyException(NOT_FOUND));
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}
}
