package tsh.d94.app.server.apService.study;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;

import tsh.d94.app.server.apService.study.dto.StudyDtos.*;
import tsh.d94.app.server.apService.study.exception.StudyErrorCode;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.apService.study.parser.PdfParserClient;
import tsh.d94.app.server.apService.study.parser.SyntaxRequest;
import tsh.d94.app.server.apService.study.parser.SyntaxResponse;
import tsh.d94.app.server.data.orm.study.readingAids.StudyReadingAidsAccess;

@ExtendWith(MockitoExtension.class)
class StudySyntaxServiceTest {
	@Mock StudyPageService pages;
	@Mock PdfParserClient parser;
	@Mock StudyReadingAidsAccess data;
	StudySyntaxService service() { return new StudySyntaxService(pages, parser, data, new ObjectMapper()); }

	private PageResponse page() {
		var cell = new PageCell("b3", 0, 0, "cell text here");
		return new PageResponse(null, null, null, List.of(), List.of(
			new PageBlock("b1", "PARAGRAPH", null, "A paragraph of text.", 1, null),
			new PageBlock("b2", "TABLE", null, "", 1, List.of(cell)),
			new PageBlock("b4", "FIGURE", null, "figure", 1, null)), List.of(), java.util.Map.of(), List.of());
	}

	@Test void syntaxCacheHitSkipsParserEvenIfVersionDiffers() {
		when(pages.getPage("d", "p")).thenReturn(page());
		when(data.syntaxCache("d", "p")).thenReturn(List.<String[]>of(new String[] {"old-ver",
			"[{\"blockKey\":\"b1\",\"start\":0,\"end\":5,\"role\":\"SUBJECT\"}]"}));
		var r = service().getPageSyntax("d", "p");
		assertThat(r.analyzerVersion()).isEqualTo("old-ver");
		assertThat(r.spans()).hasSize(1);
		verifyNoInteractions(parser);
	}

	@Test @SuppressWarnings("unchecked") void syntaxCacheMissCallsParserAndSaves() {
		when(pages.getPage("d", "p")).thenReturn(page());
		when(data.syntaxCache("d", "p")).thenReturn(List.of());
		when(parser.syntax(any())).thenReturn(new SyntaxResponse("v1", List.of(new SyntaxResponse.Span("b1", 0, 5, "SUBJECT"))));
		var r = service().getPageSyntax("d", "p");
		assertThat(r.spans()).containsExactly(new SyntaxSpan("b1", 0, 5, "SUBJECT"));
		var cap = ArgumentCaptor.forClass(List.class);
		verify(parser).syntax(cap.capture());
		assertThat(((List<SyntaxRequest.Block>) cap.getValue()).stream().map(SyntaxRequest.Block::key)).containsExactly("b1", "b3");
		verify(data).saveSyntaxCache(eq("d"), eq("p"), eq("v1"), anyString());
	}

	@Test void syntaxParserFailureNotSaved() {
		when(pages.getPage("d", "p")).thenReturn(page());
		when(data.syntaxCache("d", "p")).thenReturn(List.of());
		when(parser.syntax(any())).thenThrow(new StudyException(StudyErrorCode.SYNTAX_UNAVAILABLE));
		assertThatThrownBy(() -> service().getPageSyntax("d", "p")).isInstanceOf(StudyException.class);
		verify(data, never()).saveSyntaxCache(any(), any(), any(), any());
	}

	@Test void syntaxRequiresOwnership() {
		when(pages.getPage("d", "p")).thenThrow(new StudyException(StudyErrorCode.NOT_FOUND));
		assertThatThrownBy(() -> service().getPageSyntax("d", "p")).isInstanceOf(StudyException.class);
		verifyNoInteractions(parser, data);
	}
}
