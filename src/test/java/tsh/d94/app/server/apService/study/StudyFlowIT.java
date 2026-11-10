package tsh.d94.app.server.apService.study;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.apService.study.dto.StudyDtos.*;
import tsh.d94.app.server.apService.study.parser.ParserResponse;
import tsh.d94.app.server.apService.study.parser.ParserResponse.Block;
import tsh.d94.app.server.apService.study.parser.ParserResponse.Figure;
import tsh.d94.app.server.apService.study.parser.ParserResponse.Section;
import tsh.d94.app.server.apService.study.parser.PdfParserClient;
import tsh.d94.app.server.service.StorageService;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/** 업로드 → (가짜) 파서 → 저장 → 목차·페이지 → 단어·마킹 → 삭제. MinIO 는 메모리 맵. */
@IntegrationTest
class StudyFlowIT {

	@Autowired StudyUploadService uploads;
	@Autowired StudyDocumentService documents;
	@Autowired StudyPageService pages;
	@Autowired StudyTermService terms;
	@Autowired StudyAnnotationService annotations;
	@Autowired JdbcTemplate jdbc;
	@MockBean PdfParserClient parser;
	@MockBean StorageService storage;

	private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

	@BeforeEach
	void setUp() throws IOException {
		TestContexts.login(CiraFixtures.uniqueEmail("study"));
		doAnswer(i -> objects.put(i.getArgument(0), i.getArgument(1))).when(storage).putBytes(anyString(), any(), any());
		doAnswer(i -> objects.put(i.getArgument(0), ((InputStream)i.getArgument(1)).readAllBytes()))
			.when(storage).putStream(anyString(), any(), anyLong(), any());
		when(storage.objectSize(anyString())).thenAnswer(i -> {
			byte[] b = objects.get((String)i.getArgument(0));
			return b == null ? -1L : (long)b.length;
		});
		when(storage.getObjectStream(anyString())).thenAnswer(i -> new ByteArrayInputStream(objects.get((String)i.getArgument(0))));
		doAnswer(i -> objects.keySet().removeIf(k -> k.startsWith(i.getArgument(0)))).when(storage).deletePrefix(anyString());
		when(parser.parse(any(), any())).thenReturn(new ParserResponse("1.0.0", "Guide Title", 3,
			List.of(new Section("s0001", null, 1, "Chapter 1", 1, "b000001"),
				new Section("s0002", "s0001", 2, "Audit Planning", 1, "b000002"),
				new Section("s0003", "s0001", 2, "Controls", 3, "b000007")),
			List.of(new Block("b000001", "s0001", null, "HEADING", 1, "Chapter 1", 1, null, null),
				new Block("b000002", "s0002", null, "HEADING", 2, "Audit Planning", 1, null, null),
				new Block("b000003", "s0002", null, "PARAGRAPH", null, "An audit plan defines the audit scope.", 1, null, null),
				new Block("b000004", "s0002", null, "TABLE", null, "Table 1.1: Key aspects", 2, null, null),
				new Block("b000005", "s0002", "b000004", "TABLE_CELL", null, "What is an audit?", 2, 0, 0),
				new Block("b000006", "s0002", "b000004", "FIGURE", null, "Figure 1.1: Flow", 2, null, null),
				new Block("b000007", "s0003", null, "HEADING", 2, "Controls", 3, null, null)),
			List.of(new Figure("b000006", Base64.getEncoder().encodeToString(new byte[] {(byte)0x89, 'P', 'N', 'G'})))));
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	@Test
	void uploadParseReadAnnotateDelete() throws Exception {
		byte[] pdf = new byte[786_432 * 2 + 100];
		System.arraycopy("%PDF-".getBytes(), 0, pdf, 0, 5);
		StartUploadResponse up = uploads.start(new StartUploadRequest("guide.pdf", pdf.length));
		assertThat(up.partCount()).isEqualTo(3);
		for (int n = 1; n <= 3; n++) {
			int from = (n - 1) * up.partSize();
			uploads.putPart(up.uploadId(), n, java.util.Arrays.copyOfRange(pdf, from, Math.min(pdf.length, from + up.partSize())));
		}
		String docId = uploads.complete(up.uploadId()).id();

		long until = System.currentTimeMillis() + 10_000;
		while (!"READY".equals(documents.get(docId).status()) && System.currentTimeMillis() < until) {
			Thread.sleep(200);
		}
		DocumentResponse doc = documents.get(docId);
		assertThat(doc.status()).isEqualTo("READY");
		assertThat(doc.title()).isEqualTo("Guide Title");
		assertThat(objects).containsKey("study/docs/" + docId + "/source.pdf");

		List<TocNode> toc = documents.toc(docId);
		assertThat(toc).hasSize(1);
		assertThat(toc.get(0).children()).hasSize(2);
		String page1 = toc.get(0).pageSectionId();
		assertThat(page1).isEqualTo(toc.get(0).children().get(0).id());

		terms.create(new TermRequest("audit", "감사", null));
		PageResponse page = pages.getPage(docId, page1);
		assertThat(page.blocks()).extracting(PageBlock::key).containsExactly("b000001", "b000002", "b000003", "b000004", "b000006");
		assertThat(page.blocks().get(3).cells()).extracting(PageCell::text).containsExactly("What is an audit?");
		assertThat(page.termHits()).extracting(TermHitDto::blockKey).contains("b000003", "b000005");
		assertThat(page.prev()).isNull();
		assertThat(page.next().title()).isEqualTo("Controls");
		assertThat(documents.figure(docId, "b000006").readAllBytes()).hasSize(4);

		AnnotationListItem created = annotations.create(new CreateAnnotationRequest(docId, "b000005", 0, "b000005", 4, "yellow", "중요"));
		assertThat(created.quote()).isEqualTo("What");
		List<AnnotationListItem> notes = annotations.list(docId);
		assertThat(notes).hasSize(1);
		assertThat(notes.get(0).location()).isEqualTo(new NoteLocation(docId, page1, "b000005"));
		assertThat(pages.getPage(docId, page1).annotations()).singleElement().matches(PageAnnotation::hasNote);

		documents.delete(docId);
		for (String table : List.of("sn_study_block", "sn_study_section", "sn_study_annotation", "sn_study_term")) {
			assertThat(jdbc.queryForObject("select count(*) from " + table + " where document_id = ?", Integer.class, docId))
				.as(table).isZero();
		}
		assertThat(jdbc.queryForObject("select count(*) from sn_study_document where obj_id = ?", Integer.class, docId)).isZero();
		assertThat(objects.keySet()).noneMatch(k -> k.contains(docId));
	}
}
