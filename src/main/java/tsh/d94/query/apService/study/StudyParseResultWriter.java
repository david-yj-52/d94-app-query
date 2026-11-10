package tsh.d94.app.server.apService.study;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.parser.ParserResponse;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockAccess;
import tsh.d94.app.server.data.orm.study.studyBlock.SnStudyBlockModel;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentAccess;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentModel;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionAccess;
import tsh.d94.app.server.data.orm.study.studySection.SnStudySectionModel;
import tsh.d94.app.server.service.StorageService;

@Service
@RequiredArgsConstructor
public class StudyParseResultWriter {

	private static final String EVENT = "ParseStudyDocument";
	private static final int BATCH = 1000;

	private final SnStudyDocumentAccess documentAccess;
	private final SnStudySectionAccess sectionAccess;
	private final SnStudyBlockAccess blockAccess;
	private final StorageService storage;

	@Transactional
	public void save(SnStudyDocumentModel doc, ParserResponse r) {
		String docId = doc.getObjId();
		String tenant = doc.getTenant();

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

		doc.setStatus(Study.READY);
		doc.setFailReason(null);
		doc.setPageCount(r.pageCount());
		doc.setParserVersion(r.parserVersion());
		if (r.title() != null && !r.title().isBlank()) {
			doc.setTitle(Study.truncate(r.title(), 500));
		}
		documentAccess.save(doc);
	}
}
