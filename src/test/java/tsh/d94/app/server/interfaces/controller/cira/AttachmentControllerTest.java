package tsh.d94.app.server.interfaces.controller.cira;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import tsh.d94.app.server.apService.cira.AttachmentService;
import tsh.d94.app.server.data.orm.cira.ciraAttachment.SnCiraAttachmentModel;
import tsh.d94.app.server.support.ControllerTestSupport;

@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

	@Mock
	private AttachmentService attachmentService;
	@InjectMocks
	private AttachmentController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = ControllerTestSupport.mvc(controller);
	}

	@Test
	@DisplayName("GET /attachments/{id}/download → 비동기 없이 본문·inline·UTF-8 파일명")
	void download() throws Exception {
		SnCiraAttachmentModel att = mock(SnCiraAttachmentModel.class);
		given(att.getFileNm()).willReturn("스크린샷.png");
		given(att.getMimeType()).willReturn("image/png");
		given(att.getS3Key()).willReturn("k");
		given(att.getFileSize()).willReturn(3L);
		given(attachmentService.getAttachmentForDownload("a-1")).willReturn(att);
		given(attachmentService.getObjectStream("k")).willReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

		mvc.perform(get("/api/v1/attachments/a-1/download"))
			// 비동기(StreamingResponseBody)면 ASYNC 재디스패치에서 보안 필터에 막힌다
			.andExpect(request().asyncNotStarted())
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/png"))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("inline"),
				containsString("filename*=UTF-8''" + java.net.URLEncoder.encode("스크린샷", StandardCharsets.UTF_8)))))
			.andExpect(content().bytes(new byte[] {1, 2, 3}));
	}

}
