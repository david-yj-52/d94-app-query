package tsh.d94.app.server.apService.study;

import static tsh.d94.app.server.apService.study.exception.StudyErrorCode.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.dto.StudyDtos.OutlineItem;
import tsh.d94.app.server.apService.study.dto.StudyDtos.PageAnnotation;
import tsh.d94.app.server.apService.study.dto.StudyDtos.PageBlock;
import tsh.d94.app.server.apService.study.dto.StudyDtos.PageCell;
import tsh.d94.app.server.apService.study.dto.StudyDtos.PageResponse;
import tsh.d94.app.server.apService.study.dto.StudyDtos.SectionRef;
import tsh.d94.app.server.apService.study.dto.StudyDtos.TermDefDto;
import tsh.d94.app.server.apService.study.dto.StudyDtos.TermHitDto;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.data.orm.study.studyAnnotation.SnStudyAnnotationAccess;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockAccess;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockModel;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionAccess;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionModel;
import tsh.d94.app.server.data.orm.study.studyTerm.SnStudyTermModel;

/** 화면 한 페이지(페이지 섹션 + 소속 섹션들)의 블록·단어 위치·마킹을 한 번에 만든다. 웹은 그대로 그린다. */
@Service
@RequiredArgsConstructor
public class StudyPageService {

	private final StudyDocumentService documents;
	private final StudyTermService terms;
	private final SnStudySectionAccess sectionAccess;
	private final SnStudyBlockAccess blockAccess;
	private final SnStudyAnnotationAccess annotationAccess;

	public PageResponse getPage(String documentId, String pageSectionId) {
		documents.requireReady(documentId);
		List<SnStudySectionModel> sections = sectionAccess.findByDocumentIdOrderByOrd(documentId);
		Map<String, SnStudySectionModel> byId = new HashMap<>();
		LinkedHashSet<String> pageOrder = new LinkedHashSet<>();
		List<String> sectionIds = new ArrayList<>();
		for (SnStudySectionModel s : sections) {
			byId.put(s.getObjId(), s);
			pageOrder.add(s.getPageSectionId());
			if (pageSectionId.equals(s.getPageSectionId())) {
				sectionIds.add(s.getObjId());
			}
		}
		SnStudySectionModel page = byId.get(pageSectionId);
		if (page == null || sectionIds.isEmpty()) {
			throw new StudyException(NOT_FOUND);
		}
		List<String> pages = new ArrayList<>(pageOrder);
		int idx = pages.indexOf(pageSectionId);

		List<SnStudyBlockModel> blocks = blockAccess.findByDocumentIdAndSectionIdInOrderByOrd(documentId, sectionIds);
		Map<String, List<PageCell>> cells = new HashMap<>();
		for (SnStudyBlockModel b : blocks) {
			if ("TABLE_CELL".equals(b.getBlockTyp())) {
				cells.computeIfAbsent(b.getParentBlockKey(), k -> new ArrayList<>())
					.add(new PageCell(b.getBlockKey(), b.getRowIdx(), b.getColIdx(), text(b)));
			}
		}
		List<PageBlock> pageBlocks = new ArrayList<>();
		List<OutlineItem> outline = new ArrayList<>();
		for (SnStudyBlockModel b : blocks) {
			if ("TABLE_CELL".equals(b.getBlockTyp())) {
				continue;
			}
			pageBlocks.add(new PageBlock(b.getBlockKey(), b.getBlockTyp(), b.getLvl(), text(b), b.getPageNo(),
				"TABLE".equals(b.getBlockTyp()) ? cells.getOrDefault(b.getBlockKey(), List.of()) : null));
			if ("HEADING".equals(b.getBlockTyp()) && b.getLvl() != null && b.getLvl() > page.getLvl()) {
				outline.add(new OutlineItem(b.getBlockKey(), text(b), b.getLvl()));
			}
		}

		List<SnStudyTermModel> effective = terms.effectiveTerms(documentId);
		TermMatcher matcher = new TermMatcher(effective.stream()
			.map(t -> new TermMatcher.Entry(t.getObjId(), t.getNormTerm())).toList());
		Map<String, SnStudyTermModel> termById = new HashMap<>();
		effective.forEach(t -> termById.put(t.getObjId(), t));
		List<TermHitDto> hits = new ArrayList<>();
		Map<String, TermDefDto> defs = new LinkedHashMap<>();
		for (SnStudyBlockModel b : blocks) {
			if ("FIGURE".equals(b.getBlockTyp())) {
				continue;
			}
			for (TermMatcher.Hit h : matcher.find(text(b))) {
				hits.add(new TermHitDto(b.getBlockKey(), h.start(), h.end(), h.termId()));
				SnStudyTermModel t = termById.get(h.termId());
				defs.putIfAbsent(h.termId(), new TermDefDto(t.getTerm(), t.getDefinition(),
					t.getDocumentId() == null ? "GLOBAL" : "DOCUMENT"));
			}
		}

		List<String> keys = blocks.stream().map(SnStudyBlockModel::getBlockKey).toList();
		List<PageAnnotation> annotations = annotationAccess
			.findByOwnerIdAndDocumentIdAndStartBlockKeyIn(Study.owner(), documentId, keys).stream()
			.map(a -> new PageAnnotation(a.getObjId(), a.getStartBlockKey(), a.getStartOffset(), a.getEndBlockKey(),
				a.getEndOffset(), a.getColor(), a.getNote() != null && !a.getNote().isBlank()))
			.toList();

		return new PageResponse(ref(page), idx > 0 ? ref(byId.get(pages.get(idx - 1))) : null,
			idx < pages.size() - 1 ? ref(byId.get(pages.get(idx + 1))) : null,
			outline, pageBlocks, hits, defs, annotations);
	}

	private static SectionRef ref(SnStudySectionModel s) {
		return new SectionRef(s.getObjId(), s.getTitle());
	}

	static String text(SnStudyBlockModel b) {
		return b.getText() == null ? "" : b.getText();
	}
}
