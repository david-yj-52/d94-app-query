package tsh.d94.app.server.interfaces.controller.study;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tsh.d94.app.server.apService.study.StudyAnnotationService;
import tsh.d94.app.server.apService.study.StudyDocumentService;
import tsh.d94.app.server.apService.study.StudyPageService;
import tsh.d94.app.server.apService.study.StudyTermService;
import tsh.d94.app.server.apService.study.StudyUploadService;
import tsh.d94.app.server.support.ControllerTestSupport;

@ExtendWith(MockitoExtension.class)
class StudyControllerTest {

	@Mock
	private StudyUploadService uploads;
	@Mock
	private StudyDocumentService documents;
	@Mock
	private StudyPageService pages;
	@Mock
	private StudyTermService terms;
	@Mock
	private StudyAnnotationService annotations;
	@InjectMocks
	private StudyController controller;

	@Test
	@DisplayName("GET /documents/{id}/figures/{key} → 비동기 없이 PNG 본문")
	void figure() throws Exception {
		given(documents.figure("d-1", "b000001")).willReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

		ControllerTestSupport.mvc(controller).perform(get("/api/v1/study/documents/d-1/figures/b000001"))
			// 비동기(StreamingResponseBody)면 ASYNC 재디스패치에서 보안 필터에 막혀 그림이 안 보인다
			.andExpect(request().asyncNotStarted())
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/png"))
			.andExpect(content().bytes(new byte[] {1, 2, 3}));
	}
}
