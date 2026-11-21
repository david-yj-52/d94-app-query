package tsh.d94.app.server.apService.study;

import java.util.ArrayList;
import java.util.Base64;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.parser.ParserResponse;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockAccess;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockModel;
import tsh.d94.app.server.data.orm.study.studyAnnotation.SnStudyAnnotationAccess;
import tsh.d94.app.server.data.orm.study.studyAnnotation.SnStudyAnnotationModel;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentAccess;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentModel;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionAccess;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionModel;
import tsh.d94.app.server.service.StorageService;
import tsh.d94.app.server.data.orm.study.readingAids.StudyReadingAidsAccess;

@Service
@RequiredArgsConstructor
public class StudyParseResultWriter {

	private static final String EVENT = "ParseStudyDocument";
	private static final int BATCH = 1000;

	private final SnStudyDocumentAccess documentAccess;
	private final SnStudySectionAccess sectionAccess;
	private final SnStudyBlockAccess blockAccess;
	private final SnStudyAnnotationAccess annotationAccess;
	private final StudyReadingAidsAccess readingAids;
	private final StorageService storage;
	private static final Pattern BLOCK_NUMBER = Pattern.compile("b(\\d+)");

	@Transactional
	public void save(SnStudyDocumentModel doc, ParserResponse r) {
		String docId = doc.getObjId();
		String tenant = doc.getTenant();
		List<SnStudyBlockModel> previousBlocks = blockAccess.findByDocumentIdOrderByOrd(docId);
		Map<String, String> previousToCurrent = new HashMap<>();
		r = preserveBlockKeys(r, previousBlocks, previousToCurrent);

		// 재파싱은 기존 구조를 교체한다. 원본 PDF, 단어장, 노트는 그대로 둔다.
		blockAccess.deleteByDocumentId(docId);
		sectionAccess.deleteByDocumentId(docId);
		readingAids.deleteSyntaxByDocumentId(docId);

		Map<String, SnStudySectionModel> sections = new LinkedHashMap<>();
		int ord = 0;
		for (ParserResponse.Section s : r.sections()) { // 부모가 먼저 온다
			SnStudySectionModel parent = s.parentKey() == null ? null : sections.get(s.parentKey());
			sections.put(s.key(), sectionAccess.save(Study.base(SnStudySectionModel.builder(), EVENT, tenant)
				.documentId(docId)
				.parentId(parent == null ? null : parent.getObjId())
				.sectionKey(s.key())
				.lvl(s.level())
				.title(Study.truncate(s.title(), 1000))
				.ord(ord++)
				.pageNo(s.page())
				.anchorBlockKey(s.anchorBlockKey())
				.build()));
		}
		Map<String, String> pageKeys = PageSectionResolver.resolve(r.sections());
		for (Map.Entry<String, SnStudySectionModel> e : sections.entrySet()) {
			e.getValue().setPageSectionId(sections.get(pageKeys.get(e.getKey())).getObjId());
		}
		sectionAccess.saveAll(new ArrayList<>(sections.values()));

		Map<String, String> figureKeys = new HashMap<>();
		for (ParserResponse.Figure f : r.figures()) {
			String key = "study/docs/" + docId + "/fig/" + f.blockKey() + ".png";
			storage.putBytes(key, Base64.getDecoder().decode(f.pngBase64()), "image/png");
			figureKeys.put(f.blockKey(), key);
		}

		List<SnStudyBlockModel> batch = new ArrayList<>(BATCH);
		int blockOrd = 0;
		for (ParserResponse.Block b : r.blocks()) {
			SnStudySectionModel section = sections.get(b.sectionKey());
			batch.add(Study.base(SnStudyBlockModel.builder(), EVENT, tenant)
				.documentId(docId)
				.sectionId(section.getObjId())
				.blockKey(b.key())
				.parentBlockKey(b.parentKey())
				.ord(blockOrd++)
				.blockTyp(b.type())
				.lvl(b.level())
				.text(b.text())
				.pageNo(b.page())
				.rowIdx(b.row())
				.colIdx(b.col())
				.figureKey(figureKeys.get(b.key()))
				.build());
			if (batch.size() == BATCH) {
				blockAccess.saveAll(batch);
				batch = new ArrayList<>(BATCH);
			}
		}
		blockAccess.saveAll(batch);
		remapAnnotations(doc, previousBlocks, r, previousToCurrent);

		doc.setStatus(Study.READY);
		doc.setFailReason(null);
		doc.setPageCount(r.pageCount());
		doc.setParserVersion(r.parserVersion());
		doc.setLastSectionId(null);
		doc.setLastBlockKey(null);
		if (r.title() != null && !r.title().isBlank()) {
			doc.setTitle(Study.truncate(r.title(), 500));
		}
		documentAccess.save(doc);
	}

	private ParserResponse preserveBlockKeys(ParserResponse response, List<SnStudyBlockModel> previous,
		Map<String, String> previousToCurrent) {
		Map<String, Deque<String>> oldKeysBySignature = new HashMap<>();
		int nextNumber = 1;
		for (SnStudyBlockModel block : previous) {
			oldKeysBySignature.computeIfAbsent(signature(block.getPageNo(), block.getBlockTyp(), block.getText()),
				ignored -> new ArrayDeque<>()).add(block.getBlockKey());
			Matcher matcher = BLOCK_NUMBER.matcher(block.getBlockKey());
			if (matcher.matches()) nextNumber = Math.max(nextNumber, Integer.parseInt(matcher.group(1)) + 1);
		}

		Map<String, String> currentKeys = new HashMap<>();
		Set<String> used = new HashSet<>();
		for (ParserResponse.Block block : response.blocks()) {
			Deque<String> matches = oldKeysBySignature.get(signature(block.page(), block.type(), block.text()));
			String key = matches == null || matches.isEmpty() ? null : matches.removeFirst();
			if (key == null || !used.add(key)) {
				key = String.format(Locale.ROOT, "b%06d", nextNumber++);
				used.add(key);
			} else {
				previousToCurrent.put(key, key);
			}
			currentKeys.put(block.key(), key);
		}

		List<ParserResponse.Block> blocks = response.blocks().stream().map(b -> new ParserResponse.Block(
			currentKeys.get(b.key()), b.sectionKey(), b.parentKey() == null ? null : currentKeys.get(b.parentKey()),
			b.type(), b.level(), b.text(), b.page(), b.row(), b.col())).toList();
		List<ParserResponse.Section> sections = response.sections().stream().map(s -> new ParserResponse.Section(
			s.key(), s.parentKey(), s.level(), s.title(), s.page(), currentKeys.getOrDefault(s.anchorBlockKey(), s.anchorBlockKey()))).toList();
		List<ParserResponse.Figure> figures = response.figures().stream().map(f ->
			new ParserResponse.Figure(currentKeys.getOrDefault(f.blockKey(), f.blockKey()), f.pngBase64())).toList();
		return new ParserResponse(response.parserVersion(), response.title(), response.pageCount(), sections, blocks, figures);
	}

	private String signature(Integer page, String type, String text) {
		String normalized = text == null ? "" : text.strip().replaceAll("\\s+", " ");
		return page + "|" + type + "|" + normalized;
	}

	private void remapAnnotations(SnStudyDocumentModel doc, List<SnStudyBlockModel> previous,
		ParserResponse response, Map<String, String> previousToCurrent) {
		Map<String, SnStudyBlockModel> oldByKey = new HashMap<>();
		previous.forEach(b -> oldByKey.put(b.getBlockKey(), b));
		Map<String, List<ParserResponse.Block>> newByPage = new HashMap<>();
		response.blocks().forEach(b -> newByPage.computeIfAbsent(String.valueOf(b.page()), ignored -> new ArrayList<>()).add(b));
		List<SnStudyAnnotationModel> annotations = annotationAccess
			.findByOwnerIdAndDocumentIdOrderByCreatedAtDesc(doc.getOwnerId(), doc.getObjId());
		for (SnStudyAnnotationModel annotation : annotations) {
			String mappedStart = previousToCurrent.get(annotation.getStartBlockKey());
			String mappedEnd = previousToCurrent.get(annotation.getEndBlockKey());
			if (mappedStart != null && mappedEnd != null) {
				annotation.setStartBlockKey(mappedStart);
				annotation.setEndBlockKey(mappedEnd);
				annotationAccess.save(annotation);
				continue;
			}
			SnStudyBlockModel oldStart = oldByKey.get(annotation.getStartBlockKey());
			if (oldStart == null) continue;
			for (ParserResponse.Block block : newByPage.getOrDefault(String.valueOf(oldStart.getPageNo()), List.of())) {
				String text = block.text() == null ? "" : block.text();
				int offset = text.indexOf(annotation.getQuote());
				if (offset >= 0) {
					annotation.setStartBlockKey(block.key());
					annotation.setStartOffset(offset);
					annotation.setEndBlockKey(block.key());
					annotation.setEndOffset(offset + annotation.getQuote().length());
					annotationAccess.save(annotation);
					break;
				}
			}
		}
	}
}
