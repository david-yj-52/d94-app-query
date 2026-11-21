package tsh.d94.app.server.apService.study;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.dto.StudyDtos.SyntaxResponse;
import tsh.d94.app.server.apService.study.dto.StudyDtos.SyntaxSpan;
import tsh.d94.app.server.apService.study.parser.PdfParserClient;
import tsh.d94.app.server.apService.study.parser.SyntaxRequest;
import tsh.d94.app.server.data.orm.study.readingAids.StudyReadingAidsAccess;

@Service
@RequiredArgsConstructor
public class StudySyntaxService {
	private final StudyPageService pages;
	private final PdfParserClient parser;
	private final StudyReadingAidsAccess data;
	private final ObjectMapper mapper;

	public SyntaxResponse getPageSyntax(String documentId, String pageSectionId) {
		// getPage가 소유권·READY 검사를 한다. ponytail: 용어/마킹 계산까지 같이 하지만 pageBlocks 추출보다 단순해서 재사용
		var page = pages.getPage(documentId, pageSectionId);
		// ponytail: 버전 무시 조회 — 분석기 업그레이드 시 테이블 비우기
		var cached = data.syntaxCache(documentId, pageSectionId);
		if (!cached.isEmpty()) {
			return new SyntaxResponse(cached.get(0)[0], read(cached.get(0)[1]));
		}
		List<SyntaxRequest.Block> blocks = new ArrayList<>();
		page.blocks().forEach(b -> {
			if ("TABLE".equals(b.type())) {
				if (b.cells() != null) {
					b.cells().forEach(c -> add(blocks, c.key(), c.text()));
				}
			} else if (!"FIGURE".equals(b.type())) {
				add(blocks, b.key(), b.text());
			}
		});
		var response = parser.syntax(blocks);
		List<SyntaxSpan> spans = response.spans().stream()
			.map(s -> new SyntaxSpan(s.blockKey(), s.start(), s.end(), s.role())).toList();
		try {
			// 같은 (문서, 섹션, 버전) 동시 저장은 SQL의 on conflict가 흡수한다
			data.saveSyntaxCache(documentId, pageSectionId, response.analyzerVersion(), mapper.writeValueAsString(spans));
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
		return new SyntaxResponse(response.analyzerVersion(), spans);
	}

	private static void add(List<SyntaxRequest.Block> blocks, String key, String text) {
		if (text != null && !text.isBlank()) {
			blocks.add(new SyntaxRequest.Block(key, text));
		}
	}

	private List<SyntaxSpan> read(String json) {
		try {
			return mapper.readValue(json, new TypeReference<List<SyntaxSpan>>() { });
		} catch (com.fasterxml.jackson.core.JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
	}
}
